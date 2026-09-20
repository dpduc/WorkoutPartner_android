package com.workoutpartner.core.posetracking

/**
 * All of one analyzed camera frame's MediaPipe landmarks (up to
 * [LANDMARK_COUNT], indexed per MediaPipe's Pose topology; empty when no
 * pose was detected) — unlike [PoseFrameMapper]'s six-joint, side-agnostic
 * frame, this keeps every landmark, since the Position Check
 * (`workout-partner-v3` ticket 12) needs the whole body's coverage and
 * height, not just the joints a rep counter reads.
 */
data class RawPoseFrame(val landmarks: List<RawPoseLandmark>) {
    companion object {
        const val LANDMARK_COUNT = 33
    }
}
