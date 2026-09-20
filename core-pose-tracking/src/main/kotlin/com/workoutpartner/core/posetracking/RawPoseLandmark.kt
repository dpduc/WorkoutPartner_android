package com.workoutpartner.core.posetracking

/**
 * One landmark sample as MediaPipe's Pose Landmarker reports it: a
 * normalized image-space point plus its confidence scores. This module's
 * own type — deliberately not `com.google.mediapipe`'s `NormalizedLandmark`
 * — so [PoseFrameMapper]'s side-picking logic stays plain Kotlin and
 * unit-testable without the MediaPipe runtime. [PoseLandmarkerResultMapping]
 * is the (untestable, MediaPipe-dependent) adapter between the two.
 */
data class RawPoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    /** 0f..1f confidence the landmark isn't occluded; null if MediaPipe didn't report one. */
    val visibility: Float?,
    /** 0f..1f confidence the landmark is present in frame at all; null if MediaPipe didn't report one. */
    val presence: Float?,
) {
    /** Visibility and presence both gate how much MediaPipe trusts a landmark; the lower of the two wins (an unreported score counts as fully confident). */
    val confidence: Float get() = minOf(visibility ?: 1f, presence ?: 1f)
}
