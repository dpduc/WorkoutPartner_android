package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.core.posetracking.PoseFrameMapper
import com.workoutpartner.core.posetracking.RawPoseFrame
import com.workoutpartner.core.posetracking.RawPoseLandmark
import kotlin.math.abs

/** Whether the Athlete's skeleton fills a workable share of the camera frame — see [PositionCheckEvaluator.MIN_SKELETON_HEIGHT_FRACTION]. */
enum class DistanceStatus { UNKNOWN, TOO_FAR, OK, TOO_CLOSE }

/** One frame's Position Check result: is the whole body visible, and how far away is it. */
data class FrameEvaluation(val bodyInFrame: Boolean, val distance: DistanceStatus) {
    val allChecksPass: Boolean get() = bodyInFrame && distance == DistanceStatus.OK
}

/**
 * The Position Check's pure per-frame evaluation (`workout-partner-v3`
 * ticket 12): plain functions of a [RawPoseFrame], driven by
 * [BeforeYouStartEngine]. Lighting is deliberately not checked in this
 * version (spec.md story 66). Every threshold is a placeholder to be tuned
 * against real footage, like this app's angle thresholds.
 */
object PositionCheckEvaluator {
    /** At least this many of [RawPoseFrame.LANDMARK_COUNT] landmarks must be confident for the whole body to count as in frame. */
    const val REQUIRED_CONFIDENT_LANDMARKS = 28

    /** Skeleton height as a fraction of frame height: under the minimum is too far, over the maximum too close. */
    const val MIN_SKELETON_HEIGHT_FRACTION = 0.4f
    const val MAX_SKELETON_HEIGHT_FRACTION = 0.8f

    /** Mean per-landmark movement, in normalized image units, at or above which the Athlete counts as not standing still. */
    const val STILLNESS_DISPLACEMENT_THRESHOLD = 0.02f

    fun evaluate(frame: RawPoseFrame): FrameEvaluation {
        val confident = frame.landmarks.filter { it.isConfident() }
        val distance = if (confident.isEmpty()) {
            DistanceStatus.UNKNOWN
        } else {
            val skeletonHeight = confident.maxOf { it.y } - confident.minOf { it.y }
            when {
                skeletonHeight < MIN_SKELETON_HEIGHT_FRACTION -> DistanceStatus.TOO_FAR
                skeletonHeight > MAX_SKELETON_HEIGHT_FRACTION -> DistanceStatus.TOO_CLOSE
                else -> DistanceStatus.OK
            }
        }
        return FrameEvaluation(bodyInFrame = confident.size >= REQUIRED_CONFIDENT_LANDMARKS, distance = distance)
    }

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
