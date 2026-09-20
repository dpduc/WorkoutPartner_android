package com.workoutpartner.core.posetracking

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * Converts one MediaPipe [PoseLandmarkerResult] into this module's own
 * [RawPoseLandmark] vocabulary, keyed by the joints [PoseFrameMapper] reads.
 * The only file in this module that touches MediaPipe's actual result
 * types — everything downstream of this adapter (mapping, debouncing) is
 * plain Kotlin and unit-tested without them.
 *
 * Per ADR-0003, only the first detected pose is used — this pipeline is
 * single-person by design, not a "pick the biggest person in frame" policy.
 * No pose detected at all this frame (subject stepped out, occluded) yields
 * an empty map, which [PoseFrameMapper] turns into an empty frame and
 * [TrackingStateMachine] eventually turns into [PoseTrackingSignal.Lost].
 */
fun PoseLandmarkerResult.toRawLandmarks(): Map<MediaPipePoseLandmark, RawPoseLandmark> {
    val detected = landmarks().firstOrNull() ?: return emptyMap()
    return MediaPipePoseLandmark.entries.mapNotNull { landmark ->
        detected.getOrNull(landmark.index)?.let { landmark to it.toRawPoseLandmark() }
    }.toMap()
}

/** Every landmark of the first detected pose, in MediaPipe index order; empty if none was detected. Same single-person policy as [toRawLandmarks]. */
fun PoseLandmarkerResult.toRawPoseFrame(): RawPoseFrame =
    RawPoseFrame(landmarks().firstOrNull()?.map { it.toRawPoseLandmark() } ?: emptyList())

private fun NormalizedLandmark.toRawPoseLandmark() = RawPoseLandmark(
    x = x(),
    y = y(),
    z = z(),
    visibility = if (visibility().isPresent) visibility().get() else null,
    presence = if (presence().isPresent) presence().get() else null,
)
