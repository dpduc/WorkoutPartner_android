package com.workoutpartner.core.posetracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.workoutpartner.core.repcounting.ExerciseProfile
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
 *
 * [onCameraSessionStarted]/[onCameraSessionStopped] (camera-session-robustness
 * ticket 02) fire once the camera has actually bound and once [stop] tears
 * that down, letting a caller run a Foreground Service for as long as this
 * class genuinely has the camera open — without `core-pose-tracking`
 * depending on that app-module concern itself. Default to no-ops so nothing
 * outside `app`'s composition root has to know they exist.
 *
 * Camera-session-robustness ticket 03: once bound, also observes the bound
 * `Camera`'s `CameraInfo.cameraState` and reacts to the camera being lost
 * mid-session — `analyzeFrame` otherwise just silently stops being called,
 * with nothing telling the rest of the app anything is wrong. A recoverable
 * loss ([CameraState.ErrorType.RECOVERABLE] — another app briefly grabbed
 * the camera, e.g.) gets a bounded, backed-off retry via [retryPolicy]; an
 * unrecoverable one, or a recoverable one that's retried too many times
 * already, is reported through [errors] like any other failure. This never
 * touches [trackingStateMachine] — a person stepping out of frame is a
 * `CameraState.Type.OPEN` camera producing empty-ish frames, structurally
 * indistinguishable at this layer from any other frame content, so it can
 * never be mistaken for a camera loss here.
 */
class CameraPoseTracker(
    private val context: Context,
    /** The Exercise (or Variant) already known at construction — a Session's first Set, Quick Count's own parameter, or Position Check's first Set (camera-framing-indicator ticket 01). Update it later via [updateExerciseProfile]. */
    initialProfile: ExerciseProfile,
    private val onCameraSessionStarted: (context: Context, onStartFailure: (String) -> Unit) -> Unit = { _, _ -> },
    private val onCameraSessionStopped: (context: Context) -> Unit = {},
) : PoseTracker {

    private var cameraProvider: ProcessCameraProvider? = null
    private var poseLandmarker: PoseLandmarker? = null
    private val trackingStateMachine = TrackingStateMachine(initialProfile)
    private var emitSignal: ((PoseTrackingSignal) -> Unit)? = null
    private var emitError: ((String) -> Unit)? = null
    /** Whether [onCameraSessionStarted] actually fired — so [stop] only calls [onCameraSessionStopped] for a session that really started (e.g. never called if the camera never bound). */
    private var cameraSessionActive = false

    // Ticket 03's reconnect state. [boundLifecycleOwner]/[boundSurfaceProvider] are
    // start()'s own params, kept around so a retry can re-run the same bind path
    // without the caller having to call start() again itself.
    private val retryPolicy = CameraRetryPolicy()
    private val retryHandler = Handler(Looper.getMainLooper())
    private var pendingRetry: Runnable? = null
    private var boundLifecycleOwner: LifecycleOwner? = null
    private var boundSurfaceProvider: Preview.SurfaceProvider? = null

    // A single, stable Observer instance — reused across every bind (including
    // every retry) so [observedCameraState] can actually remove it again before
    // the next bind. A method reference like `::handleCameraState` passed
    // straight to `LiveData.observe` gets SAM-converted to a *new* adapter object
    // every call, so `removeObserver` with a fresh reference would never match
    // the one already registered — this field exists specifically to avoid that.
    private val cameraStateObserver = Observer<CameraState> { state -> handleCameraState(state) }
    /** The `CameraInfo.cameraState` LiveData [cameraStateObserver] is currently registered on, so [bindCamera]/[stop] can detach it from the *previous* bind before attaching to a new one. */
    private var observedCameraState: LiveData<CameraState>? = null

    override val signals: Flow<PoseTrackingSignal> = callbackFlow {
        emitSignal = { trySend(it) }
        awaitClose { emitSignal = null }
    }

    override val mirrorsPreview = true

    private var emitRawFrame: ((RawPoseFrame) -> Unit)? = null

    override val rawFrames: Flow<RawPoseFrame> = callbackFlow {
        emitRawFrame = { trySend(it) }
        awaitClose { emitRawFrame = null }
    }

    override val errors: Flow<String> = callbackFlow {
        emitError = { trySend(it) }
        awaitClose { emitError = null }
    }

    /**
     * Neither the Pose Landmarker's model load nor CameraX's provider/bind
     * calls are things this class can guarantee will succeed — the model
     * asset may not be bundled (see [MODEL_ASSET_PATH]'s doc comment) and a
     * device may have no usable front camera. Both used to throw straight
     * out of here uncaught, which crashed the app the moment a Session or
     * Quick Count screen tried to open the camera; now caught and surfaced
     * through [errors] instead, so the UI can show a message rather than die.
     */
    override fun start(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        if (poseLandmarker == null) {
            poseLandmarker = try {
                createPoseLandmarker()
            } catch (e: Exception) {
                emitError?.invoke(e.message ?: "Couldn't load the pose tracking model.")
                return
            }
        }

        // A fresh start() means a fresh retry budget — a loss from a previous bind
        // (or a previous, now-superseded Session/Quick Count run reusing this same
        // instance) shouldn't count against this one.
        retryPolicy.reset()
        boundLifecycleOwner = lifecycleOwner
        boundSurfaceProvider = previewSurfaceProvider

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                try {
                    cameraProvider = providerFuture.get()
                    bindCamera(lifecycleOwner, previewSurfaceProvider)
                } catch (e: Exception) {
                    emitError?.invoke(e.message ?: "Couldn't start the camera.")
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * The actual bind call, shared by [start] and by [handleCameraState]'s retry
     * path — a reconnect is "do the same bind again," not a separate code path.
     * Must run on the main thread: `LiveData.observe` requires it, and this is
     * always called either from `start()`'s [ProcessCameraProvider] listener
     * (already main-thread, via `ContextCompat.getMainExecutor`) or from
     * [retryHandler]'s own delayed callback (main-thread `Looper` by construction).
     */
    private fun bindCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        val provider = cameraProvider ?: return
        try {
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewSurfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(context), ::analyzeFrame) }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                preview,
                analysis,
            )
            // A fresh Camera per bind means a fresh CameraState LiveData — but the
            // *previous* bind's LiveData doesn't just go away on its own: it can still
            // fire its own CLOSING/CLOSED transition after unbindAll(), so the old
            // observer has to be removed explicitly, not just left to be superseded.
            observedCameraState?.removeObserver(cameraStateObserver)
            val cameraState = camera.cameraInfo.cameraState
            observedCameraState = cameraState
            cameraState.observe(lifecycleOwner, cameraStateObserver)

            if (!cameraSessionActive) {
                onCameraSessionStarted(context) { message -> emitError?.invoke(message) }
                cameraSessionActive = true
            }
        } catch (e: Exception) {
            emitError?.invoke(e.message ?: "Couldn't start the camera.")
        }
    }

    /**
     * Ticket 03: reacts to the bound camera's own state, never to frame content
     * (that's [trackingStateMachine]'s job, fed from [analyzeFrame] instead).
     */
    private fun handleCameraState(state: CameraState) {
        val error = state.error
        if (error == null) {
            // Type.OPEN with no error is a healthy camera — including "healthy again
            // after a retry succeeded," which is exactly when the budget should reset.
            if (state.type == CameraState.Type.OPEN) retryPolicy.reset()
            return
        }

        if (error.type != CameraState.ErrorType.RECOVERABLE) {
            reportPermanentCameraLoss(error)
            return
        }

        val backoffMs = retryPolicy.onRecoverableError()
        if (backoffMs == null) {
            reportPermanentCameraLoss(error)
            return
        }

        val owner = boundLifecycleOwner
        val surfaceProvider = boundSurfaceProvider
        if (owner == null || surfaceProvider == null) return // stop() already ran; nothing to reconnect

        cameraProvider?.unbindAll()
        val retry = Runnable { bindCamera(owner, surfaceProvider) }
        pendingRetry = retry
        retryHandler.postDelayed(retry, backoffMs)
    }

    /**
     * Retries exhausted, or the error was never recoverable to begin with — report
     * it the same way a startup bind failure already is (`errors`, terminal per
     * [PoseTracker.errors]'s own doc comment), then tear this instance down for
     * real so a dead-camera session doesn't keep [onCameraSessionStopped] from
     * ever firing (e.g. leaving the Foreground Service notification up with no
     * camera actually behind it). [stop] is safe to call again later from the
     * caller's own cleanup (`PoseTracker.stop`'s doc comment already promises this).
     */
    private fun reportPermanentCameraLoss(error: CameraState.StateError) {
        emitError?.invoke("Lost connection to the camera (error ${error.code}).")
        stop()
    }

    override fun stop() {
        pendingRetry?.let(retryHandler::removeCallbacks)
        pendingRetry = null
        boundLifecycleOwner = null
        boundSurfaceProvider = null
        observedCameraState?.removeObserver(cameraStateObserver)
        observedCameraState = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        poseLandmarker?.close()
        poseLandmarker = null
        if (cameraSessionActive) {
            onCameraSessionStopped(context)
            cameraSessionActive = false
        }
    }

    override fun updateExerciseProfile(profile: ExerciseProfile) {
        trackingStateMachine.updateProfile(profile)
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
            // ADR-0003: single-person tracking is a deliberate choice, not
            // an accident of MediaPipe's default — pin it explicitly rather
            // than leaning on whatever the library's own default happens to
            // be (ticket 11's review caught this module previously relying
            // on an unstated default here, with only PoseLandmarkerResultMapping's
            // `firstOrNull()` as a soft, after-the-fact guard).
            .setNumPoses(1)
            .setResultListener { result, _ ->
                emitRawFrame?.invoke(result.toRawPoseFrame())
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
