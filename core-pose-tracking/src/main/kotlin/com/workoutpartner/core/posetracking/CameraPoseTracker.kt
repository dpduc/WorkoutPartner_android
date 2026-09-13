package com.workoutpartner.core.posetracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The real pose-tracking engine (ticket 03): CameraX front camera feeding
 * frames to MediaPipe's Pose Landmarker in `LIVE_STREAM` mode, mapped
 * through [PoseFrameMapper] and debounced through [TrackingStateMachine]
 * into the [PoseTrackingSignal] stream [PoseTracker] promises.
 *
 * Not unit-tested — it's a thin wire-up of CameraX and the MediaPipe Tasks
 * Vision runtime, both of which need a real camera/device to exercise (same
 * caveat ticket 01 raised about its instrumented test: no emulator/device
 * was available in this session either). The logic worth testing —
 * [PoseFrameMapper]'s side-picking and [TrackingStateMachine]'s lost/resume
 * debounce — is pulled out into those plain-Kotlin classes and covered by
 * JUnit fixtures instead. This class, and its dependence on [MODEL_ASSET_PATH]
 * actually being bundled, is unverified against a real build in this session
 * (no JDK/Android SDK toolchain was available) — see this ticket's Comments.
 */
class CameraPoseTracker(private val context: Context) : PoseTracker {

    private var cameraProvider: ProcessCameraProvider? = null
    private var poseLandmarker: PoseLandmarker? = null
    private val trackingStateMachine = TrackingStateMachine()
    private var emitSignal: ((PoseTrackingSignal) -> Unit)? = null

    override val signals: Flow<PoseTrackingSignal> = callbackFlow {
        emitSignal = { trySend(it) }
        awaitClose { emitSignal = null }
    }

    override fun start(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        if (poseLandmarker == null) {
            poseLandmarker = createPoseLandmarker()
        }

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewSurfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), ::analyzeFrame) }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun stop() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        poseLandmarker?.close()
        poseLandmarker = null
    }

    private fun analyzeFrame(imageProxy: ImageProxy) {
        val landmarker = poseLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        val bitmapBuffer = Bitmap.createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }

        // Front camera output is mirrored; un-mirror so MediaPipe sees the
        // same orientation a rear-camera user would present.
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true,
        )

        landmarker.detectAsync(BitmapImageBuilder(rotatedBitmap).build(), SystemClock.uptimeMillis())
    }

    private fun createPoseLandmarker(): PoseLandmarker {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET_PATH).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                val frame = PoseFrameMapper.toPoseLandmarkFrame(result.toRawLandmarks())
                emitSignal?.invoke(trackingStateMachine.accept(frame))
            }
            .setErrorListener {
                // A dropped detection reads the same as an empty frame to
                // TrackingStateMachine on the next successful callback, which
                // is what decides Lost vs. a momentary blip — no separate
                // error signal needed here.
            }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    companion object {
        /**
         * Must exist under `src/main/assets/` — the actual
         * `pose_landmarker_lite.task` model file from MediaPipe's model zoo
         * (https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker)
         * is NOT bundled by this change. It's a binary asset to provision,
         * not code to write — tracked as an outstanding step in this
         * ticket's Comments, the same way ticket 01 flagged the missing
         * `google-services.json` rather than fake one.
         */
        const val MODEL_ASSET_PATH = "pose_landmarker_lite.task"
    }
}
