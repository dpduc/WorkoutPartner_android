package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseFrameMapperTest {

    @Test
    fun `picks the higher-confidence side when both clear the threshold`() {
        val raw = mapOf(
            MediaPipePoseLandmark.LEFT_KNEE to landmark(x = 1f, confidence = 0.7f),
            MediaPipePoseLandmark.RIGHT_KNEE to landmark(x = 2f, confidence = 0.95f),
        )

        val frame = PoseFrameMapper.toPoseLandmarkFrame(raw)

        assertEquals(Point3D(2f, 0f, 0f), frame.landmarks[Landmark.KNEE])
    }

    @Test
    fun `falls back to the only side that clears the threshold`() {
        val raw = mapOf(
            MediaPipePoseLandmark.LEFT_HIP to landmark(x = 1f, confidence = 0.1f),
            MediaPipePoseLandmark.RIGHT_HIP to landmark(x = 3f, confidence = 0.9f),
        )

        val frame = PoseFrameMapper.toPoseLandmarkFrame(raw)

        assertEquals(Point3D(3f, 0f, 0f), frame.landmarks[Landmark.HIP])
    }

    @Test
    fun `omits a joint neither side clears the threshold for`() {
        val raw = mapOf(
            MediaPipePoseLandmark.LEFT_ANKLE to landmark(x = 1f, confidence = 0.2f),
            MediaPipePoseLandmark.RIGHT_ANKLE to landmark(x = 2f, confidence = 0.3f),
        )

        val frame = PoseFrameMapper.toPoseLandmarkFrame(raw)

        assertNull(frame.landmarks[Landmark.ANKLE])
    }

    @Test
    fun `an empty detection produces an empty frame`() {
        val frame = PoseFrameMapper.toPoseLandmarkFrame(emptyMap())

        assertTrue(frame.landmarks.isEmpty())
    }

    @Test
    fun `presence gates confidence the same as visibility`() {
        val raw = mapOf(
            MediaPipePoseLandmark.LEFT_SHOULDER to
                RawPoseLandmark(x = 1f, y = 0f, z = 0f, visibility = 0.9f, presence = 0.1f),
        )

        val frame = PoseFrameMapper.toPoseLandmarkFrame(raw)

        assertNull(frame.landmarks[Landmark.SHOULDER])
    }

    private fun landmark(x: Float, confidence: Float) =
        RawPoseLandmark(x = x, y = 0f, z = 0f, visibility = confidence, presence = confidence)
}
