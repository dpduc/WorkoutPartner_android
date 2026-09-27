package com.workoutpartner.app.framing

import com.workoutpartner.core.posetracking.RawPoseFrame
import com.workoutpartner.core.posetracking.RawPoseLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dedicated coverage for the shared distance-scoring pipeline built for
 * camera-framing-indicator ticket 02 (spec.md's Testing Decisions): the
 * continuous score's monotonicity/boundaries against the existing
 * skeleton-height-fraction constants ([FramingScorer]), the smoothing
 * function's behavior across a sequence of noisy inputs
 * ([FramingScoreSmoother]), and the score-to-color mapping's endpoints and
 * midpoint ([FramingColor]). Previously this logic had no dedicated test
 * file, only indirect coverage via `BeforeYouStartEngineTest`.
 */
class FramingPipelineTest {

    // --- FramingScorer ---

    @Test
    fun `closeness is 1_0 exactly at the ideal skeleton height, the midpoint of the min-max range`() {
        val idealHeight = (FramingScorer.MIN_SKELETON_HEIGHT_FRACTION + FramingScorer.MAX_SKELETON_HEIGHT_FRACTION) / 2f

        val result = FramingScorer.evaluate(frame(topY = 0f, bottomY = idealHeight))

        assertEquals(1f, result.closeness, 0.001f)
        assertEquals(DistanceStatus.OK, result.status)
    }

    @Test
    fun `closeness falls off symmetrically moving away from the ideal height in either direction`() {
        val idealHeight = (FramingScorer.MIN_SKELETON_HEIGHT_FRACTION + FramingScorer.MAX_SKELETON_HEIGHT_FRACTION) / 2f
        val halfRange = (FramingScorer.MAX_SKELETON_HEIGHT_FRACTION - FramingScorer.MIN_SKELETON_HEIGHT_FRACTION) / 2f
        val offset = halfRange / 2f

        val below = FramingScorer.evaluate(frame(topY = 0f, bottomY = idealHeight - offset)).closeness
        val above = FramingScorer.evaluate(frame(topY = 0f, bottomY = idealHeight + offset)).closeness

        assertEquals(below, above, 0.001f)
        assertTrue("closer to the edge should score lower than the ideal midpoint", below < 1f)
    }

    @Test
    fun `closeness is monotonically decreasing as the skeleton height moves further from ideal`() {
        val idealHeight = (FramingScorer.MIN_SKELETON_HEIGHT_FRACTION + FramingScorer.MAX_SKELETON_HEIGHT_FRACTION) / 2f
        val halfRange = (FramingScorer.MAX_SKELETON_HEIGHT_FRACTION - FramingScorer.MIN_SKELETON_HEIGHT_FRACTION) / 2f

        val scores = listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { fraction ->
            FramingScorer.evaluate(frame(topY = 0f, bottomY = idealHeight + halfRange * fraction)).closeness
        }

        for (i in 0 until scores.size - 1) {
            assertTrue("score should strictly decrease moving toward the edge: $scores", scores[i] > scores[i + 1])
        }
    }

    @Test
    fun `closeness is clamped to 0_0 at and beyond the min-max boundaries`() {
        val atMin = FramingScorer.evaluate(frame(topY = 0f, bottomY = FramingScorer.MIN_SKELETON_HEIGHT_FRACTION)).closeness
        val wellBeyondMin = FramingScorer.evaluate(frame(topY = 0f, bottomY = FramingScorer.MIN_SKELETON_HEIGHT_FRACTION / 4f)).closeness
        val wellBeyondMax = FramingScorer.evaluate(frame(topY = 0f, bottomY = FramingScorer.MAX_SKELETON_HEIGHT_FRACTION * 2f)).closeness

        assertEquals(0f, atMin, 0.001f)
        assertEquals(0f, wellBeyondMin, 0.001f)
        assertEquals(0f, wellBeyondMax, 0.001f)
    }

    @Test
    fun `a skeleton under the minimum height fraction reads too far`() {
        val result = FramingScorer.evaluate(frame(topY = 0.3f, bottomY = 0.69f))

        assertEquals(DistanceStatus.TOO_FAR, result.status)
    }

    @Test
    fun `a skeleton over the maximum height fraction reads too close`() {
        val result = FramingScorer.evaluate(frame(topY = 0.05f, bottomY = 0.9f))

        assertEquals(DistanceStatus.TOO_CLOSE, result.status)
    }

    @Test
    fun `an empty frame with no confident landmarks reads unknown distance and zero closeness`() {
        val result = FramingScorer.evaluate(RawPoseFrame(emptyList()))

        assertEquals(DistanceStatus.UNKNOWN, result.status)
        assertEquals(0f, result.closeness, 0.001f)
    }

    @Test
    fun `low confidence landmarks do not stretch the measured skeleton height`() {
        // 30 confident landmarks span 0.2..0.8 (60%); the 3 unconfident ones sit far outside that.
        val result = FramingScorer.evaluate(frame(topY = 0.2f, bottomY = 0.8f, confidentCount = 30, strayY = 0f))

        assertEquals(DistanceStatus.OK, result.status)
    }

    // --- FramingScoreSmoother ---

    @Test
    fun `the first sample passes through unsmoothed`() {
        val smoother = FramingScoreSmoother()

        assertEquals(0.7f, smoother.next(0.7f), 0.001f)
    }

    @Test
    fun `smoothing pulls a noisy sample only partway toward it, not all the way`() {
        val smoother = FramingScoreSmoother(smoothingFactor = 0.2f)
        smoother.next(0f)

        val result = smoother.next(1f)

        assertTrue("should move toward the new sample...", result > 0f)
        assertTrue("...but not jump all the way to it", result < 1f)
        assertEquals(0.2f, result, 0.001f)
    }

    @Test
    fun `smoothed value converges toward a sustained input across repeated samples`() {
        val smoother = FramingScoreSmoother(smoothingFactor = 0.3f)
        smoother.next(0f)

        var last = 0f
        repeat(50) { last = smoother.next(1f) }

        assertEquals(1f, last, 0.01f)
    }

    @Test
    fun `a single noisy outlier moves the smoothed value only a little`() {
        val smoother = FramingScoreSmoother(smoothingFactor = 0.2f)
        repeat(10) { smoother.next(0.8f) } // settle near a steady 0.8 reading

        val afterOutlier = smoother.next(0f) // one noisy, momentarily bad frame

        assertTrue("one bad frame shouldn't crash the score to 0", afterOutlier > 0.5f)
    }

    // --- FramingColor ---

    @Test
    fun `closeness 1_0 maps to the ideal color`() {
        val result = FramingColor.forCloseness(1f)

        assertEquals(FramingColor.IDEAL.red, result.red, 0.001f)
        assertEquals(FramingColor.IDEAL.green, result.green, 0.001f)
        assertEquals(FramingColor.IDEAL.blue, result.blue, 0.001f)
    }

    @Test
    fun `closeness 0_0 maps to the far-off color`() {
        val result = FramingColor.forCloseness(0f)

        assertEquals(FramingColor.FAR_OFF.red, result.red, 0.001f)
        assertEquals(FramingColor.FAR_OFF.green, result.green, 0.001f)
        assertEquals(FramingColor.FAR_OFF.blue, result.blue, 0.001f)
    }

    @Test
    fun `closeness 0_5 maps to a color strictly between ideal and far-off on every channel`() {
        val midpoint = FramingColor.forCloseness(0.5f)

        listOf(
            Triple(midpoint.red, FramingColor.IDEAL.red, FramingColor.FAR_OFF.red),
            Triple(midpoint.green, FramingColor.IDEAL.green, FramingColor.FAR_OFF.green),
            Triple(midpoint.blue, FramingColor.IDEAL.blue, FramingColor.FAR_OFF.blue),
        ).forEach { (mid, ideal, farOff) ->
            assertTrue(mid in minOf(ideal, farOff)..maxOf(ideal, farOff))
        }
        assertTrue(midpoint != FramingColor.IDEAL)
        assertTrue(midpoint != FramingColor.FAR_OFF)
    }

    @Test
    fun `untrackable is a fixed color distinct from both ends of the distance gradient`() {
        assertTrue(FramingColor.UNTRACKABLE != FramingColor.IDEAL)
        assertTrue(FramingColor.UNTRACKABLE != FramingColor.FAR_OFF)
    }

    /**
     * [RawPoseFrame.LANDMARK_COUNT] landmarks spread evenly from [topY] to
     * [bottomY]; only the first [confidentCount] are confident — the rest are
     * low-confidence strays at [strayY] (default: [topY]), which the height
     * measurement must ignore. Same shape as `BeforeYouStartEngineTest`'s own
     * frame builder.
     */
    private fun frame(
        topY: Float,
        bottomY: Float,
        confidentCount: Int = RawPoseFrame.LANDMARK_COUNT,
        strayY: Float = topY,
    ): RawPoseFrame {
        val landmarks = (0 until RawPoseFrame.LANDMARK_COUNT).map { i ->
            if (i < confidentCount) {
                val y = topY + (bottomY - topY) * i / (confidentCount - 1).coerceAtLeast(1)
                RawPoseLandmark(x = 0.5f, y = y, z = 0f, visibility = 0.9f, presence = 0.9f)
            } else {
                RawPoseLandmark(x = 0.5f, y = strayY, z = 0f, visibility = 0.1f, presence = 0.1f)
            }
        }
        return RawPoseFrame(landmarks)
    }
}
