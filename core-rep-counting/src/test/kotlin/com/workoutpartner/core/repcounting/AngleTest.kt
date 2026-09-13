package com.workoutpartner.core.repcounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AngleTest {
    @Test
    fun `right angle between perpendicular rays`() {
        val frame = PoseLandmarkFrame(
            mapOf(
                Landmark.HIP to Point3D(1f, 0f, 0f),
                Landmark.KNEE to Point3D(0f, 0f, 0f),
                Landmark.ANKLE to Point3D(0f, 1f, 0f),
            ),
        )

        val angle = Angle.between(frame, Landmark.HIP, Landmark.KNEE, Landmark.ANKLE)

        assertEquals(90f, angle!!, 0.01f)
    }

    @Test
    fun `straight line reads 180 degrees`() {
        val frame = PoseLandmarkFrame(
            mapOf(
                Landmark.HIP to Point3D(-1f, 0f, 0f),
                Landmark.KNEE to Point3D(0f, 0f, 0f),
                Landmark.ANKLE to Point3D(1f, 0f, 0f),
            ),
        )

        val angle = Angle.between(frame, Landmark.HIP, Landmark.KNEE, Landmark.ANKLE)

        assertEquals(180f, angle!!, 0.01f)
    }

    @Test
    fun `missing landmark yields no angle`() {
        val frame = PoseLandmarkFrame(
            mapOf(
                Landmark.KNEE to Point3D(0f, 0f, 0f),
                Landmark.ANKLE to Point3D(0f, 1f, 0f),
            ),
        )

        assertNull(Angle.between(frame, Landmark.HIP, Landmark.KNEE, Landmark.ANKLE))
    }
}
