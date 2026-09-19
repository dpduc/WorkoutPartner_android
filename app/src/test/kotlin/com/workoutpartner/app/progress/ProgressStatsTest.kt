package com.workoutpartner.app.progress

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ProgressStatsTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `activeDays collapses multiple Sets on the same day into one entry`() {
        val sets = listOf(
            setOn("2024-01-01T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 90),
            setOn("2024-01-01T09:30:00Z", Exercise.PUSH_UP, actualReps = 10, formScore = 80),
            setOn("2024-01-02T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 90),
        )

        val activeDays = ProgressStats.activeDays(sets, zone)

        assertEquals(setOf(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)), activeDays)
    }

    @Test
    fun `personalBests tracks the best reps and best form score independently per Exercise`() {
        val sets = listOf(
            setOn("2024-01-01T09:00:00Z", Exercise.SQUAT, actualReps = 20, formScore = 60), // most reps, mediocre form
            setOn("2024-01-02T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 95), // fewer reps, best form
        )

        val bests = ProgressStats.personalBests(sets)

        assertEquals(PersonalBest(bestReps = 20, bestFormScore = 95), bests.getValue(TrackedExercise(Exercise.SQUAT)))
    }

    @Test
    fun `personalBests has no entry for an Exercise never attempted`() {
        val sets = listOf(setOn("2024-01-01T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 90))

        val bests = ProgressStats.personalBests(sets)

        assertNull(bests[TrackedExercise(Exercise.PUSH_UP)])
    }

    @Test
    fun `personalBests keeps a Step Jack Set separate from a Jumping Jack Set of the same parent Exercise`() {
        val sets = listOf(
            setOn("2024-01-01T09:00:00Z", Exercise.JUMPING_JACK, actualReps = 20, formScore = 90),
            setOn("2024-01-02T09:00:00Z", Exercise.JUMPING_JACK, actualReps = 30, formScore = 60, variant = ExerciseVariant.STEP_JACK),
        )

        val bests = ProgressStats.personalBests(sets)

        assertEquals(PersonalBest(bestReps = 20, bestFormScore = 90), bests.getValue(TrackedExercise(Exercise.JUMPING_JACK)))
        assertEquals(PersonalBest(bestReps = 30, bestFormScore = 60), bests.getValue(TrackedExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK)))
    }

    @Test
    fun `formScoreTrend returns only the given Exercise's Sets, oldest first`() {
        val sets = listOf(
            setOn("2024-01-03T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 70),
            setOn("2024-01-01T09:00:00Z", Exercise.PUSH_UP, actualReps = 10, formScore = 50),
            setOn("2024-01-02T09:00:00Z", Exercise.SQUAT, actualReps = 10, formScore = 85),
        )

        val trend = ProgressStats.formScoreTrend(sets, Exercise.SQUAT, zone)

        assertEquals(
            listOf(
                FormScorePoint(LocalDate.of(2024, 1, 2), 85),
                FormScorePoint(LocalDate.of(2024, 1, 3), 70),
            ),
            trend,
        )
    }

    private fun setOn(
        isoTimestamp: String,
        exercise: Exercise,
        actualReps: Int,
        formScore: Int,
        variant: ExerciseVariant? = null,
    ) = SetEntity(
        id = "set-$isoTimestamp-${exercise.name}-${variant?.name}",
        sessionId = "session-1",
        exercise = exercise,
        targetReps = actualReps,
        actualReps = actualReps,
        formScore = formScore,
        goodSet = formScore >= 80,
        timestamp = Instant.parse(isoTimestamp),
        exerciseVariant = variant,
    )
}
