package com.workoutpartner.core.repcounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step Jack (ticket 08's only [ExerciseVariant]) against its parent Exercise,
 * Jumping Jack — not part of [RepCounterTest]'s parameterization, since
 * [ExerciseProfiles.forExercise] resolves it by an explicit [ExerciseVariant]
 * argument, not by iterating [Exercise.entries].
 */
class RepCounterVariantTest {

    private val jumpingJackProfile = ExerciseProfiles.forExercise(Exercise.JUMPING_JACK)
    private val stepJackProfile = ExerciseProfiles.forExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK)

    @Test
    fun `Step Jack's own 75-degree rep threshold is shallower than Jumping Jack's 90-degree one`() {
        assertEquals(75f, stepJackProfile.repThresholdDegrees)
        assertEquals(90f, jumpingJackProfile.repThresholdDegrees)
    }

    @Test
    fun `a sweep to 80 degrees counts a Step Jack Rep that Jumping Jack would not at the same angle`() {
        val stepJackEvents = feed(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK, stepJackProfile.restingAngle(), 80f, stepJackProfile.restingAngle())
        assertEquals(1, stepJackEvents.size)

        val jumpingJackEvents = feed(Exercise.JUMPING_JACK, variant = null, jumpingJackProfile.restingAngle(), 80f, jumpingJackProfile.restingAngle())
        assertTrue(jumpingJackEvents.isEmpty())
    }

    @Test
    fun `Form Score uses Step Jack's own 135-degree threshold, not Jumping Jack's 150-degree one`() {
        // 140 degrees clears both Exercises' rep thresholds (75/90) — a Rep
        // counts either way — but only clears Step Jack's 135-degree form
        // threshold, not Jumping Jack's 150-degree one.
        val sweepAngle = 140f

        val stepJackEvents = feed(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK, stepJackProfile.restingAngle(), sweepAngle, stepJackProfile.restingAngle())
        assertEquals(1, stepJackEvents.size)
        assertTrue(stepJackEvents.single().passedFormThreshold)
        assertEquals(100, FormScore.compute(stepJackEvents))

        val jumpingJackEvents = feed(Exercise.JUMPING_JACK, variant = null, jumpingJackProfile.restingAngle(), sweepAngle, jumpingJackProfile.restingAngle())
        assertEquals(1, jumpingJackEvents.size)
        assertFalse(jumpingJackEvents.single().passedFormThreshold)
        assertEquals(0, FormScore.compute(jumpingJackEvents))
    }

    @Test
    fun `resolving a variant against the wrong parent Exercise is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { ExerciseProfiles.forExercise(Exercise.SQUAT, ExerciseVariant.STEP_JACK) }
    }

    private fun feed(exercise: Exercise, variant: ExerciseVariant?, vararg angles: Float): List<RepEvent> {
        val profile = ExerciseProfiles.forExercise(exercise, variant)
        val counter = RepCounter.forExercise(exercise, variant)
        return angles.toList().mapNotNull { counter.process(frameAtAngle(profile, it)) }
    }
}
