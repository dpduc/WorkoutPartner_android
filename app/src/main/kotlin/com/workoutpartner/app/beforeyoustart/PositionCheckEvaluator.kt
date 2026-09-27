package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.core.posetracking.PoseFrameMapper
import com.workoutpartner.core.posetracking.RawPoseFrame
import com.workoutpartner.core.posetracking.RawPoseLandmark
import kotlin.math.abs

/**
 * The Position Check's stillness detection (`workout-partner-v3` ticket 12):
 * whether the Athlete stood still between two [RawPoseFrame]s, driving
 * [BeforeYouStartEngine]'s stability window. This object's original
 * distance-scoring half (skeleton-height fraction, `DistanceStatus`) moved to
 * [com.workoutpartner.app.framing.FramingScorer] (camera-framing-indicator
 * ticket 02), since Session and Quick Count now depend on it too, not just
 * Position Check; its old whole-body `bodyInFrame`/`REQUIRED_CONFIDENT_LANDMARKS`
 * check was deleted outright, not moved — Position Check's readiness now
 * comes from collecting `poseTracker.signals` instead (see ADR-0011).
 */
object PositionCheckEvaluator {
    /** Mean per-landmark movement, in normalized image units, at or above which the Athlete counts as not standing still. */
    const val STILLNESS_DISPLACEMENT_THRESHOLD = 0.02f

    /** Whether [current] is close enough to [previous] to count as standing still; frames with no landmark confident in both can't be compared and count as movement. */
    fun isStill(previous: RawPoseFrame, current: RawPoseFrame): Boolean {
        val displacements = previous.landmarks.zip(current.landmarks)
            .filter { (before, after) -> before.isConfident() && after.isConfident() }
            .map { (before, after) -> abs(after.x - before.x) + abs(after.y - before.y) }
        if (displacements.isEmpty()) return false
        return displacements.average() < STILLNESS_DISPLACEMENT_THRESHOLD
    }

    /** The same confidence rule and threshold [PoseFrameMapper] uses to decide a joint is tracked at all. */
    private fun RawPoseLandmark.isConfident() = confidence >= PoseFrameMapper.DEFAULT_VISIBILITY_THRESHOLD
}
