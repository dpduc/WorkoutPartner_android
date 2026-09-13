package com.workoutpartner.core.posetracking

/**
 * The subset of MediaPipe Pose Landmarker's 33 landmark indices this module
 * reads — the left/right pairs behind core-rep-counting's generic,
 * side-agnostic [com.workoutpartner.core.repcounting.Landmark]. Index values
 * are per MediaPipe's documented Pose landmark topology
 * (https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker).
 */
enum class MediaPipePoseLandmark(val index: Int) {
    LEFT_SHOULDER(11),
    RIGHT_SHOULDER(12),
    LEFT_ELBOW(13),
    RIGHT_ELBOW(14),
    LEFT_WRIST(15),
    RIGHT_WRIST(16),
    LEFT_HIP(23),
    RIGHT_HIP(24),
    LEFT_KNEE(25),
    RIGHT_KNEE(26),
    LEFT_ANKLE(27),
    RIGHT_ANKLE(28),
}
