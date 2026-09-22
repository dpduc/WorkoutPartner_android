package com.workoutpartner.core.posetracking

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import android.util.Size
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
 * A debug-only [PoseTracker] that plays a video file through the same MediaPipe Pose Landmarker,
 * [PoseFrameMapper] and [TrackingStateMachine] as [CameraPoseTracker] — so the whole Position
 * Check / Session / Quick Count pipeline can be exercised (and its rep counts and Form Scores
 * inspected) without a person standing in front of the camera.
 *
 * Decodes the clip **once, sequentially, start to finish** — [MediaExtractor] demuxes, [MediaCodec]
 * decodes, and only the output frame closest to each [FRAME_INTERVAL_MS] boundary is read back to a
 * bitmap; every other frame is still decoded (unavoidable — H.264/HEVC frames depend on the ones
 * before them) but dropped unread. An earlier version of this class called
 * `MediaMetadataRetriever.getFrameAtTime` once per sampled timestamp instead: frame-accurate seeking
 * forces the decoder back to the last keyframe and forward again on *every single call*, so a clip
 * with a long keyframe interval could take many times its own length to analyze (measured: an 8x
 * device-real-time clip took over 10 minutes), and, empirically on-device, a meaningful fraction of
 * those seeks simply returned no frame at all, leaving gaps of several hundred milliseconds in the
 * sampled timeline. This sequential version decodes the whole clip in one linear pass, so nothing is
 * ever re-decoded and nothing leaves seek-induced gaps in the output.
 *
 * Frame readback is via a **hardware decoder rendering into a [SurfaceTexture], sampled back to
 * pixels with OpenGL** ([GlFrameReader]) — not [android.media.ImageReader], and not a software
 * decoder, both found the hard way, in that order:
 * - A hardware decoder (Qualcomm's `c2.qti.hevc.decoder` on the device this was built against)
 *   writing into an `ImageReader`'s `Surface` crashed the process outright reading
 *   [android.media.Image.getPlanes] (`JNI DETECTED ERROR ... non-zero capacity for nullptr
 *   pointer`) — hardware decoders are free to write compressed/tiled buffer layouts (e.g.
 *   Qualcomm's UBWC) into that surface that aren't safely readable as plain YUV planes on the CPU
 *   side.
 * - Forcing a software decoder to sidestep that hit two more failures in a row: over the same
 *   `Surface`/`ImageReader`, `Codec2Client: setOutputSurface -- failed to set consumer usage
 *   (6/BAD_INDEX)`; and, with the `Surface` dropped entirely in favor of [MediaCodec.getOutputImage],
 *   `C2SoftHevcDec: Fatal error in decoder` within ~1ms of starting, on the very first real input
 *   sample. AOSP's software HEVC decoder is simply not reliably capable of decoding this device's own
 *   real camera-recorded HEVC content, regardless of output mode.
 *
 * The hardware decoder, notably, never failed to *decode* — only `ImageReader`'s CPU plane-read
 * choked on its output layout. A GPU texture sampler handles that layout correctly (it's exactly
 * what a `TextureView`/`SurfaceView` video preview uses under the hood system-wide), so rendering to
 * a [SurfaceTexture] and reading pixels back with `glReadPixels` gets a correctly-decoded frame
 * without ever asking the CPU to interpret the raw buffer itself.
 *
 * One side effect of decoding to a `Surface` at all (found the hard way, by dumping a decoded frame
 * to a PNG and looking at it — it came out sideways): unlike the earlier ByteBuffer-mode attempts,
 * the container's rotation hint (`MediaFormat.KEY_ROTATION`) does *not* need to be applied by hand
 * here. [SurfaceTexture.getTransformMatrix] already bakes it in for `Surface`-targeted decode, and
 * [GlFrameReader]'s shader samples through that matrix — applying the rotation a second time, as an
 * earlier version of this class did, rotated every frame an extra 90° past correct.
 *
 * Frames are analyzed every [FRAME_INTERVAL_MS] of *video* time and released at that same wall-clock
 * pace, so the app's own 1-second timers (countdowns, rests) behave as they would live; the video
 * plays once from its start each time [start] is called (restarting any playback in progress), and
 * nothing is emitted after it ends. There is no camera preview — [start]'s surface provider is
 * ignored (this class's own [Surface] is an internal decode target, never shown to the user).
 *
 * Not unit-tested, for the same reason as [CameraPoseTracker]: it needs a real device runtime for
 * MediaPipe, the media decoder, and OpenGL.
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
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var glReader: GlFrameReader? = null
        try {
            extractor.setDataSource(videoFile.absolutePath)
            val trackIndex = selectVideoTrack(extractor)
            if (trackIndex == null) {
                emitError?.invoke("No video track in the debug video.")
                return
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
                ?: run { emitError?.invoke("Debug video's track has no MIME type."); return }
            if (!format.containsKey(MediaFormat.KEY_WIDTH) || !format.containsKey(MediaFormat.KEY_HEIGHT)) {
                emitError?.invoke("Debug video's track has no width/height.")
                return
            }
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0L
            Log.i(TAG, "playing ${videoFile.name}: ${durationMs}ms, sampling every ${FRAME_INTERVAL_MS}ms, size=${width}x$height")

            val reader = GlFrameReader(Size(width, height)).also { glReader = it }
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, reader.outputSurface, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var nextSampleUs = 0L
            val startedAt = System.currentTimeMillis()
            var frameCount = 0
            var detectedCount = 0
            val dump = StringBuilder()

            while (!outputDone) {
                // Checked every iteration (not just when a frame is analyzed): decode alone can now
                // outrun real time by a lot, so this is what makes stop() take effect promptly instead
                // of the whole clip being decoded to completion first.
                currentCoroutineContext().ensureActive()

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)
                        val sampleSize = inBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                if (outIndex >= 0) {
                    val presentationUs = bufferInfo.presentationTimeUs
                    // The first output frame at or after each FRAME_INTERVAL_MS boundary is the one
                    // analyzed; every other frame is still decoded (unavoidable — later frames depend
                    // on it) but never rendered to the Surface or read back. render=true here is what
                    // actually pushes this frame's pixels through to the SurfaceTexture GlFrameReader
                    // reads from.
                    val shouldAnalyze = presentationUs >= nextSampleUs
                    decoder.releaseOutputBuffer(outIndex, shouldAnalyze)
                    if (shouldAnalyze) {
                        nextSampleUs += FRAME_INTERVAL_MS * 1000L
                        val bitmap = reader.readFrame()
                        val videoTimeMs = presentationUs / 1000
                        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), videoTimeMs)
                        val rawFrame = result.toRawPoseFrame()
                        dump.append(csvLine(videoTimeMs, rawFrame)).append('\n')
                        frameCount++
                        if (rawFrame.landmarks.isNotEmpty()) detectedCount++
                        emitRawFrame?.invoke(rawFrame)
                        emitSignal?.invoke(trackingStateMachine.accept(PoseFrameMapper.toPoseLandmarkFrame(result.toRawLandmarks())))

                        // Release the next frame at its video timestamp so the app's own timers line up with the clip.
                        val waitMs = startedAt + videoTimeMs - System.currentTimeMillis()
                        if (waitMs > 0) delay(waitMs)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            val dumpFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.landmarks.csv")
            dumpFile.writeText(dump.toString())
            Log.i(TAG, "finished: $frameCount frames analyzed, pose detected in $detectedCount; landmarks saved to ${dumpFile.name}")
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            glReader?.release()
            extractor.release()
            landmarker.close()
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
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

        /** How long [MediaCodec.dequeueInputBuffer]/[MediaCodec.dequeueOutputBuffer] block before returning "try again." */
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}

