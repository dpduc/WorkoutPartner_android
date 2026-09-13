package com.workoutpartner.core.posetracking

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow

/**
 * The pose-tracking engine's public seam: front-camera capture in, a stream
 * of [PoseTrackingSignal] out. [CameraPoseTracker] is the real MediaPipe/
 * CameraX-backed implementation; consumers (ticket 09's session flow,
 * ticket 11's Quick Count) should depend on this interface so they can be
 * driven by a fake in tests instead of a real camera.
 *
 * Start collecting [signals] before or at the same time as calling [start] —
 * signals only flow to collectors registered by the time a frame is
 * analyzed.
 */
interface PoseTracker {
    /** Emits one [PoseTrackingSignal] per analyzed camera frame once [start] has bound a camera. */
    val signals: Flow<PoseTrackingSignal>

    /** Binds the front camera to [lifecycleOwner] and starts analysis; frames go to [signals]. */
    fun start(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider)

    /** Unbinds the camera and releases the MediaPipe Pose Landmarker. Safe to call multiple times, including without a prior [start]. */
    fun stop()
}
