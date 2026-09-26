package com.workoutpartner.app.data

import com.workoutpartner.app.data.BundledRoutines.StepSpec
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.RoutineFormat
import com.workoutpartner.data.RoutineStepEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Structural invariants over `BundledRoutines.all` (`.scratch/bundled-routine-catalogue/spec.md`),
 * plus a full-content spot-check of the shortest Routine (rt_01), the
 * round-heaviest one (rt_09), and the two-BMI-band one (rt_10) against the
 * spec's step table.
 */
class BundledRoutinesTest {

    @Test
    fun `exactly the 10 catalogue Routines exist, by id`() {
        assertEquals((1..10).map { "rt_%02d".format(it) }, BundledRoutines.all.map { (routine, _) -> routine.id })
    }

    @Test
    fun `every Routine's steps have contiguous order starting at 0`() {
        BundledRoutines.all.forEach { (routine, steps) ->
            assertEquals("$routine.id's step order", steps.indices.toList(), steps.map { it.orderIndex })
        }
    }

    @Test
    fun `every Routine's final step has zero rest`() {
        BundledRoutines.all.forEach { (routine, steps) ->
            assertEquals("${routine.id}'s final step rest", 0, steps.last().restIntervalSeconds)
        }
    }

    // No "Step Jack is never hard-wired" assertion here: RoutineStepEntity.exercise is typed
    // Exercise, with no ExerciseVariant field to hold one — the type system already makes that
    // invariant impossible to violate, so a runtime check of it would be vacuously true and add
    // no real signal.

    @Test
    fun `rt_09 is HIIT-tagged, every other Routine is STANDARD`() {
        BundledRoutines.all.forEach { (routine, _) ->
            val expected = if (routine.id == "rt_09") RoutineFormat.HIIT else RoutineFormat.STANDARD
            assertEquals("${routine.id}'s format", expected, routine.format)
        }
    }

    @Test
    fun `rt_01 (Joint-Safe Mobility & Tone) matches the spec's flattened steps`() {
        val (_, steps) = BundledRoutines.all.first { (routine, _) -> routine.id == "rt_01" }
        assertEquals(
            listOf(
                StepSpec(Exercise.SQUAT, 8, 35),
                StepSpec(Exercise.PUSH_UP, 6, 35),
                StepSpec(Exercise.JUMPING_JACK, 15, 45),
                StepSpec(Exercise.SQUAT, 8, 35),
                StepSpec(Exercise.PUSH_UP, 6, 35),
                StepSpec(Exercise.JUMPING_JACK, 15, 0),
            ),
            steps.asStepSpecs(),
        )
    }

    @Test
    fun `rt_09 (High-Intensity Metabolic HIIT) matches the spec's flattened steps, 4 rounds unrolled`() {
        val (_, steps) = BundledRoutines.all.first { (routine, _) -> routine.id == "rt_09" }
        val oneRound = listOf(
            StepSpec(Exercise.JUMPING_JACK, 35, 15),
            StepSpec(Exercise.PUSH_UP, 12, 15),
            StepSpec(Exercise.SQUAT, 15, 15),
            StepSpec(Exercise.SIT_UP, 12, 35),
        )
        val expected = List(4) { oneRound }.flatten().dropLast(1) + StepSpec(Exercise.SIT_UP, 12, 0)
        assertEquals(expected, steps.asStepSpecs())
    }

    @Test
    fun `rt_10 (Express Desk-Worker Reset) matches the spec's flattened steps`() {
        val (_, steps) = BundledRoutines.all.first { (routine, _) -> routine.id == "rt_10" }
        assertEquals(
            listOf(
                StepSpec(Exercise.SQUAT, 10, 25),
                StepSpec(Exercise.JUMPING_JACK, 20, 25),
                StepSpec(Exercise.LUNGE, 8, 35),
                StepSpec(Exercise.SQUAT, 10, 25),
                StepSpec(Exercise.JUMPING_JACK, 20, 25),
                StepSpec(Exercise.SIT_UP, 10, 0),
            ),
            steps.asStepSpecs(),
        )
    }

    /** Maps back to [StepSpec] — the same shape [BundledRoutines] builds its seed data from — rather than asserting field-by-field, so a mismatch anywhere in the sequence prints as one clear list diff. */
    private fun List<RoutineStepEntity>.asStepSpecs(): List<StepSpec> =
        map { StepSpec(it.exercise, it.targetReps, it.restIntervalSeconds) }
}
