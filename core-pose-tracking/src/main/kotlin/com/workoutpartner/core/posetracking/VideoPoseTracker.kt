package com.workoutpartner.core.posetracking

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * A debug-only [PoseTracker] that plays a video file through the same
 * MediaPipe Pose Landmarker, [PoseFrameMapper] and [TrackingStateMachine] as
 * [CameraPoseTracker] — so the whole Position Check / Session / Quick Count
 * pipeline can be exercised (and its rep counts and Form Scores inspected)
 * without a person standing in front of the camera.
 *
 * Frames are sampled every [FRAME_INTERVAL_MS] of *video* time and released at
 * that same wall-clock pace, so the app's own 1-second timers (countdowns,
 * rests) behave as they would live; the video plays once from its start each
 * time [start] is called (restarting any playback in progress), and nothing is
 * emitted after it ends. There is no
 * camera preview — [start]'s surface provider is ignored.
 *
 * Not unit-tested, for the same reason as [CameraPoseTracker]: it needs a real
 * device runtime for MediaPipe and the media decoder.
 */
class VideoPoseTracker(private val context: Context, private val videoFile: File) : PoseTracker {

    override val mirrorsPreview = false

    private var scope: CoroutineScope? = null
    private var playback: Job? = null
    private val trackingStateMachine = TrackingStateMachine()
    private var emitSignal: ((PoseTrackingSignal) -> Unit)? = null
    private var emitRawFrame: ((RawPoseFrame) -> Unit)? = null
    private var emitError: ((String) -> Unit)? = null

    override val signals: Flow<PoseTrackingSignal> = callbackFlow {
        emitSignal = { trySend(it) }
        awaitClose { emitSignal = null }
    }

    override val rawFrames: Flow<RawPoseFrame> = callbackFlow {
        emitRawFrame = { trySend(it) }
        awaitClose { emitRawFrame = null }
    }

    override val errors: Flow<String> = callbackFlow {
        emitError = { trySend(it) }
        awaitClose { emitError = null }
    }

    override fun start(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        // Every start (each Set's tracking screen) plays the clip again from the top.
        scope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        playback = newScope.launch {
            try {
                play()
            } catch (e: Exception) {
                if (isActive) emitError?.invoke(e.message ?: "Couldn't play the debug video.")
            }
        }
    }

    override fun stop() {
        scope?.cancel()
        scope = null
        playback = null
    }

    private suspend fun play() {
        if (!videoFile.exists()) {
            emitError?.invoke("Debug video not found: ${videoFile.absolutePath}")
            return
        }
        val landmarker = createPoseLandmarker()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Log.i(TAG, "playing ${videoFile.name}: ${durationMs}ms, sampling every ${FRAME_INTERVAL_MS}ms")

            val startedAt = System.currentTimeMillis()
            var videoTimeMs = 0L
            var frameCount = 0
            var detectedCount = 0
            val dump = StringBuilder()
            while (videoTimeMs <= durationMs) {
                // Decoding is usually slower than real time, so the delay() below is often skipped —
                // without this, stop() would never take effect until the whole clip had been analyzed.
                currentCoroutineContext().ensureActive()
                val bitmap = retriever.getScaledFrameAtTime(
                    videoTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST, MAX_FRAME_WIDTH, MAX_FRAME_WIDTH,
                ) ?: retriever.getFrameAtTime(videoTimeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
                if (bitmap != null) {
                    val argb = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    val result = landmarker.detectForVideo(BitmapImageBuilder(argb).build(), videoTimeMs)
                    val rawFrame = result.toRawPoseFrame()
                    dump.append(csvLine(videoTimeMs, rawFrame)).append('\n')
                    frameCount++
                    if (rawFrame.landmarks.isNotEmpty()) detectedCount++
                    emitRawFrame?.invoke(rawFrame)
                    emitSignal?.invoke(trackingStateMachine.accept(PoseFrameMapper.toPoseLandmarkFrame(result.toRawLandmarks())))
                }
                videoTimeMs += FRAME_INTERVAL_MS
                // Release the next frame at its video timestamp so the app's own timers line up with the clip.
                val waitMs = startedAt + videoTimeMs - System.currentTimeMillis()
                if (waitMs > 0) delay(waitMs)
            }
            val dumpFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.landmarks.csv")
            dumpFile.writeText(dump.toString())
            Log.i(TAG, "finished: $frameCount frames analyzed, pose detected in $detectedCount; landmarks saved to ${dumpFile.name}")
        } finally {
            retriever.release()
            landmarker.close()
        }
    }

    /**
     * One analyzed frame as `videoMs,x,y,z,visibility,presence,x,y,...` (33 landmarks; an unreported score is
     * written as -1) — a plain-text dump a JVM test can replay through [PoseFrameMapper] and the rep counter
     * without a device, to debug counting offline.
     */
    private fun csvLine(videoTimeMs: Long, frame: RawPoseFrame): String =
        buildString {
            append(videoTimeMs)
            frame.landmarks.forEach { append(',').append(it.x).append(',').append(it.y).append(',').append(it.z).append(',').append(it.visibility ?: -1f).append(',').append(it.presence ?: -1f) }
        }

    private fun createPoseLandmarker(): PoseLandmarker {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(CameraPoseTracker.MODEL_ASSET_PATH).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    companion object {
        private const val TAG = "VideoPoseTracker"

        /** ~15 analyzed frames per second of video: plenty for rep counting, and cheap enough to keep up in real time. */
        const val FRAME_INTERVAL_MS = 66L

        /** Frames are decoded at no more than this width — MediaPipe resizes internally, so full resolution only costs time. */
        private const val MAX_FRAME_WIDTH = 640
    }
}
