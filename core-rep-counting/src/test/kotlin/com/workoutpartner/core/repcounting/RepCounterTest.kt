package com.workoutpartner.core.repcounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The three fixture scenarios from ticket 02's Testing section, run against
 * every Exercise's profile so each of the five state machines is covered by
 * the same behavior contract.
 */
@RunWith(Parameterized::class)
class RepCounterTest(private val profile: ExerciseProfile) {

    @Test
    fun `full valid rep cycle counts a Rep that passes the form threshold`() {
        val events = feed(profile.restingAngle(), profile.beyondFormThreshold(), profile.restingAngle())

        assertEquals(1, events.size)
        assertTrue(events.single().passedFormThreshold)
        assertEquals(100, FormScore.compute(events))
    }

    @Test
    fun `partial cycle that never reaches the rep threshold counts no Rep`() {
        val events = feed(profile.restingAngle(), profile.shortOfRepThreshold(), profile.restingAngle())

        assertTrue(events.isEmpty())
        assertEquals(0, FormScore.compute(events))
    }

    @Test
    fun `cycle reaching the rep threshold but not the form threshold counts a Rep that fails form`() {
        val events = feed(profile.restingAngle(), profile.betweenRepAndFormThreshold(), profile.restingAngle())

        assertEquals(1, events.size)
        assertFalse(events.single().passedFormThreshold)
        assertEquals(0, FormScore.compute(events))
    }

    /** Each angle is held for a full smoothing window: a phase change now has to be sustained, not a one-frame blip. */
    private fun feed(vararg angles: Float): List<RepEvent> {
        val counter = RepCounter.forExercise(profile.exercise)
        return angles.toList().flatMap { angle -> List(RepCounter.SMOOTHING_WINDOW) { angle } }
            .mapNotNull { counter.process(frameAtAngle(profile, it)) }
    }

    @Test
    fun `a one-frame spike into the engaged range does not count a Rep`() {
        val counter = RepCounter.forExercise(profile.exercise)
        val angles = List(6) { profile.restingAngle() } + profile.beyondFormThreshold() + List(6) { profile.restingAngle() }

        assertTrue(angles.mapNotNull { counter.process(frameAtAngle(profile, it)) }.isEmpty())
    }

    @Test
    fun `wobbling around the rep threshold while engaged counts one Rep, not several`() {
        val counter = RepCounter.forExercise(profile.exercise)
        val wobble = listOf(profile.repThresholdDegrees, profile.direction.towardResting(profile.repThresholdDegrees, 8f))
        val angles = List(8) { profile.restingAngle() } +
            List(8) { profile.beyondFormThreshold() } +
            List(12) { wobble[it % 2] } + // hovers within the release margin: still one movement
            List(8) { profile.restingAngle() }

        assertEquals(1, angles.mapNotNull { counter.process(frameAtAngle(profile, it)) }.size)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun profiles(): Collection<Array<Any>> =
            Exercise.entries.map { arrayOf<Any>(ExerciseProfiles.forExercise(it)) }
    }
}
