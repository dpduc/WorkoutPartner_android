package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.RoutineStepEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineDurationEstimatorTest {

    private fun step(targetReps: Int, restIntervalSeconds: Int) = RoutineStepEntity(
        routineId = "r1",
        orderIndex = 0,
        exercise = Exercise.SQUAT,
        targetReps = targetReps,
        restIntervalSeconds = restIntervalSeconds,
    )

    @Test
    fun `sums seconds-per-rep plus rest across all steps at Standard difficulty`() {
        val steps = listOf(step(targetReps = 10, restIntervalSeconds = 30), step(targetReps = 5, restIntervalSeconds = 20))

        val duration = RoutineDurationEstimator.estimatedDurationSeconds(steps, DifficultyTier.STANDARD)

        // (10 reps * 3s) + 30s rest + (5 reps * 3s) + 20s rest
        assertEquals(30 + 30 + 15 + 20, duration)
    }

    @Test
    fun `a Challenging Routine's higher rep target takes longer than Easy's, rest held equal`() {
        // Zero rest isolates the rep-target effect: EASY's shorter rest can
        // otherwise outweigh CHALLENGING's higher rep count for small
        // per-rep-second assumptions, so asserting the two tiers' total
        // ordering with nonzero rest wouldn't be a reliable invariant.
        val steps = listOf(step(targetReps = 10, restIntervalSeconds = 0))

        val easy = RoutineDurationEstimator.estimatedDurationSeconds(steps, DifficultyTier.EASY)
        val challenging = RoutineDurationEstimator.estimatedDurationSeconds(steps, DifficultyTier.CHALLENGING)

        assertTrue("Challenging's higher rep target should take longer than Easy's lower one", challenging > easy)
    }

    @Test
    fun `an empty Routine takes zero seconds`() {
        assertEquals(0, RoutineDurationEstimator.estimatedDurationSeconds(emptyList(), DifficultyTier.STANDARD))
    }
}
