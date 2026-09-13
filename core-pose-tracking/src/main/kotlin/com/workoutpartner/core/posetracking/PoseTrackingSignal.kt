package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.PoseLandmarkFrame

/**
 * What the pose-tracking engine hands downstream once per analyzed camera
 * frame — per ticket 03's "trackable state": [Trackable] carries the frame
 * for the Rep Counting Engine (ticket 02) to consume; [Lost] is the signal
 * ticket 09's UI turns into the warning banner. No Rep is ever counted for
 * the gap between a [Lost] and the [Trackable] that resumes after it,
 * because no frame is delivered to the Rep Counting Engine while [Lost] —
 * see [TrackingStateMachine].
 */
sealed interface PoseTrackingSignal {
    data class Trackable(val frame: PoseLandmarkFrame) : PoseTrackingSignal
    data object Lost : PoseTrackingSignal
}
