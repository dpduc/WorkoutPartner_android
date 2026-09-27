package com.workoutpartner.app.framing

import com.workoutpartner.core.posetracking.PoseFrameMapper
import com.workoutpartner.core.posetracking.RawPoseFrame
import com.workoutpartner.core.posetracking.RawPoseLandmark
import kotlin.math.abs

/** Whether the Athlete's skeleton fills a workable share of the camera frame — the 3-bin label [FramingScorer.evaluate]'s continuous [FramingDistance.closeness] collapses to for the on-screen text (`workout-partner-v3` ticket 12; camera-framing-indicator ticket 02). */
enum class DistanceStatus { UNKNOWN, TOO_FAR, OK, TOO_CLOSE }

/**
 * One frame's camera-distance reading: [closeness] is a continuous 0.0
 * (as far off the ideal distance as the score registers) to 1.0 (exactly
 * ideal) score, symmetric regardless of direction; [status] is the same
 * three-bin classification the text label has always shown, kept alongside
 * it since [closeness] alone can't say *which way* to move.
 */
data class FramingDistance(val closeness: Float, val status: DistanceStatus)

/**
 * Turns a [RawPoseFrame] into the continuous camera-distance signal shared by
 * Position Check, Session Tracking and Quick Count Run (camera-framing-
 * indicator ticket 02) — the pure half of what used to be Position Check's
 * own, narrower `PositionCheckEvaluator.evaluate`, generalized from a 3-bin
 * result to a continuous score the border's color can animate along, moved
 * out of the `beforeyoustart` package since Session and Quick Count now
 * depend on it too. Distance is judged from the *whole* frame (every
 * confident raw MediaPipe landmark), deliberately not scoped to the current
 * Exercise's joints: how far the Athlete stands from the phone is a property
 * of the physical setup, not of which Exercise is running — unlike
 * [com.workoutpartner.core.posetracking.TrackingStateMachine], which *is*
 * exercise-scoped by design (see ADR-0011). Every threshold here is a
 * placeholder to be tuned against real footage, like this app's angle
 * thresholds.
 */
object FramingScorer {
    /** Skeleton height as a fraction of frame height: under the minimum is too far, over the maximum too close — also [FramingDistance.closeness]'s 0.0..1.0 range, centered on their midpoint (1.0), zero at either edge. */
    const val MIN_SKELETON_HEIGHT_FRACTION = 0.4f
    const val MAX_SKELETON_HEIGHT_FRACTION = 0.8f

    private const val IDEAL_SKELETON_HEIGHT_FRACTION = (MIN_SKELETON_HEIGHT_FRACTION + MAX_SKELETON_HEIGHT_FRACTION) / 2f
    private const val HALF_RANGE = (MAX_SKELETON_HEIGHT_FRACTION - MIN_SKELETON_HEIGHT_FRACTION) / 2f

    fun evaluate(frame: RawPoseFrame): FramingDistance {
        val confident = frame.landmarks.filter { it.isConfident() }
        if (confident.isEmpty()) return FramingDistance(closeness = 0f, status = DistanceStatus.UNKNOWN)

        val skeletonHeight = confident.maxOf { it.y } - confident.minOf { it.y }
        val closeness = (1f - abs(skeletonHeight - IDEAL_SKELETON_HEIGHT_FRACTION) / HALF_RANGE).coerceIn(0f, 1f)
        val status = when {
            skeletonHeight < MIN_SKELETON_HEIGHT_FRACTION -> DistanceStatus.TOO_FAR
            skeletonHeight > MAX_SKELETON_HEIGHT_FRACTION -> DistanceStatus.TOO_CLOSE
            else -> DistanceStatus.OK
        }
        return FramingDistance(closeness, status)
    }

    /** The same confidence rule and threshold [PoseFrameMapper] uses to decide a joint is tracked at all. */
    private fun RawPoseLandmark.isConfident() = confidence >= PoseFrameMapper.DEFAULT_VISIBILITY_THRESHOLD
}
