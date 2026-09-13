package com.workoutpartner.core.repcounting

import org.junit.Assert.assertEquals
import org.junit.Test

class FormScoreTest {
    @Test
    fun `no reps scores zero`() {
        assertEquals(0, FormScore.compute(emptyList()))
    }

    @Test
    fun `all reps passing scores 100`() {
        val reps = List(3) { RepEvent(Exercise.SQUAT, passedFormThreshold = true) }

        assertEquals(100, FormScore.compute(reps))
    }

    @Test
    fun `some reps passing rounds to the nearest percent`() {
        val reps = listOf(
            RepEvent(Exercise.SQUAT, passedFormThreshold = true),
            RepEvent(Exercise.SQUAT, passedFormThreshold = true),
            RepEvent(Exercise.SQUAT, passedFormThreshold = false),
        )

        assertEquals(67, FormScore.compute(reps))
    }
}
