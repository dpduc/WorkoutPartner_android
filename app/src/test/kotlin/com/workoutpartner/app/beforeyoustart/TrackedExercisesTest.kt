package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.RoutineEntity
import com.workoutpartner.data.RoutineStepEntity
import com.workoutpartner.data.RoutineWithSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackedExercisesTest {

    @Test
    fun `defaults every step to its base Exercise when no Variant is chosen`() {
        val routine = routineOf(Exercise.SQUAT, Exercise.JUMPING_JACK)

        val tracked = routine.trackedExercises()

        assertEquals(listOf(TrackedExercise(Exercise.SQUAT), TrackedExercise(Exercise.JUMPING_JACK)), tracked)
    }

    @Test
    fun `substitutes the chosen Variant only for Jumping Jack steps`() {
        val routine = routineOf(Exercise.SQUAT, Exercise.JUMPING_JACK)

        val tracked = routine.trackedExercises(ExerciseVariant.STEP_JACK)

        assertEquals(
            listOf(TrackedExercise(Exercise.SQUAT), TrackedExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK)),
            tracked,
        )
    }

    @Test
    fun `a Routine with no Jumping Jack step ignores the chosen Variant entirely`() {
        val routine = routineOf(Exercise.SQUAT, Exercise.PUSH_UP)

        val tracked = routine.trackedExercises(ExerciseVariant.STEP_JACK)

        assertEquals(listOf(TrackedExercise(Exercise.SQUAT), TrackedExercise(Exercise.PUSH_UP)), tracked)
    }

    @Test
    fun `hasJumpingJack is true only when a step is Jumping Jack`() {
        assertEquals(true, routineOf(Exercise.SQUAT, Exercise.JUMPING_JACK).hasJumpingJack)
        assertEquals(false, routineOf(Exercise.SQUAT, Exercise.PUSH_UP).hasJumpingJack)
    }

    @Test
    fun `resolveVariant only substitutes for the Variant's own parent Exercise`() {
        assertEquals(ExerciseVariant.STEP_JACK, resolveVariant(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK))
        assertEquals(null, resolveVariant(Exercise.SQUAT, ExerciseVariant.STEP_JACK))
        assertEquals(null, resolveVariant(Exercise.JUMPING_JACK, null))
    }

    private fun routineOf(vararg exercises: Exercise) = RoutineWithSteps(
        routine = RoutineEntity(id = "test-routine", name = "Test Routine"),
        steps = exercises.mapIndexed { index, exercise ->
            RoutineStepEntity(routineId = "test-routine", orderIndex = index, exercise = exercise, targetReps = 10, restIntervalSeconds = 30)
        },
    )
}
