package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.PoseLandmarkFrame
import com.workoutpartner.core.repcounting.Point3D

/**
 * Maps one MediaPipe detection onto core-rep-counting's side-agnostic
 * [PoseLandmarkFrame] — this module's half of the contract described on
 * [Landmark]'s doc comment: picking a side per joint pair so the rep
 * counting engine never has to know left from right.
 *
 * A joint below [visibilityThreshold] on both sides (or missing from [raw]
 * entirely, e.g. MediaPipe detected no pose this frame at all) is treated
 * as untracked and omitted from the resulting frame — the same contract an
 * occluded joint gets, which is what lets
 * [com.workoutpartner.core.repcounting.RepCounter] skip the frame instead
 * of misreading a phase change.
 */
object PoseFrameMapper {
    const val DEFAULT_VISIBILITY_THRESHOLD = 0.5f

    private val jointPairs: Map<Landmark, Pair<MediaPipePoseLandmark, MediaPipePoseLandmark>> = mapOf(
        Landmark.SHOULDER to (MediaPipePoseLandmark.LEFT_SHOULDER to MediaPipePoseLandmark.RIGHT_SHOULDER),
        Landmark.ELBOW to (MediaPipePoseLandmark.LEFT_ELBOW to MediaPipePoseLandmark.RIGHT_ELBOW),
        Landmark.WRIST to (MediaPipePoseLandmark.LEFT_WRIST to MediaPipePoseLandmark.RIGHT_WRIST),
        Landmark.HIP to (MediaPipePoseLandmark.LEFT_HIP to MediaPipePoseLandmark.RIGHT_HIP),
        Landmark.KNEE to (MediaPipePoseLandmark.LEFT_KNEE to MediaPipePoseLandmark.RIGHT_KNEE),
        Landmark.ANKLE to (MediaPipePoseLandmark.LEFT_ANKLE to MediaPipePoseLandmark.RIGHT_ANKLE),
    )

    fun toPoseLandmarkFrame(
        raw: Map<MediaPipePoseLandmark, RawPoseLandmark>,
        visibilityThreshold: Float = DEFAULT_VISIBILITY_THRESHOLD,
    ): PoseLandmarkFrame {
        val landmarks = jointPairs.mapNotNull { (landmark, sides) ->
            pickSide(raw, sides.first, sides.second, visibilityThreshold)?.let { landmark to it }
        }.toMap()
        return PoseLandmarkFrame(landmarks)
    }

    /**
     * Prefers whichever side clears [visibilityThreshold] with the higher
     * confidence; falls back to the other side alone if only one clears it.
     * Neither side clearing it (occluded, or an exercise's off-camera side —
     * e.g. a Lunge's back leg naturally reads lower confidence than the
     * front leg) leaves this joint untracked for the frame.
     */
    private fun pickSide(
        raw: Map<MediaPipePoseLandmark, RawPoseLandmark>,
        left: MediaPipePoseLandmark,
        right: MediaPipePoseLandmark,
        visibilityThreshold: Float,
    ): Point3D? {
        val leftSample = raw[left]?.takeIf { it.confidence >= visibilityThreshold }
        val rightSample = raw[right]?.takeIf { it.confidence >= visibilityThreshold }

        val chosen = when {
            leftSample != null && rightSample != null ->
                if (leftSample.confidence >= rightSample.confidence) leftSample else rightSample
            leftSample != null -> leftSample
            rightSample != null -> rightSample
            else -> null
        }
        return chosen?.let { Point3D(it.x, it.y, it.z) }
    }
}
