package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FormGuidesTest {

    @Test
    fun `resourcesFor covers all five Exercises plus Step Jack, per ticket 10's checklist`() {
        val expected = setOf(
            TrackedExercise(Exercise.SQUAT),
            TrackedExercise(Exercise.PUSH_UP),
            TrackedExercise(Exercise.SIT_UP),
            TrackedExercise(Exercise.LUNGE),
            TrackedExercise(Exercise.JUMPING_JACK),
            TrackedExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK),
        )

        val resolved = expected.map { FormGuides.resourcesFor(it) }.toSet()

        assertEquals(expected.size, resolved.size) // no two Exercises/Variants share the same bundled resources
    }

    @Test
    fun `Step Jack has its own Form Guide, distinct from Jumping Jack's`() {
        val jumpingJack = FormGuides.resourcesFor(TrackedExercise(Exercise.JUMPING_JACK))
        val stepJack = FormGuides.resourcesFor(TrackedExercise(Exercise.JUMPING_JACK, ExerciseVariant.STEP_JACK))

        assertNotEquals(jumpingJack, stepJack)
    }
}
