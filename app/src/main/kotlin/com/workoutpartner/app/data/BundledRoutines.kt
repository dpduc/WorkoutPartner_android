package com.workoutpartner.app.data

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.RoutineDao
import com.workoutpartner.data.RoutineEntity
import com.workoutpartner.data.RoutineFormat
import com.workoutpartner.data.RoutineStepEntity

/**
 * The bundled Routines (CONTEXT.md: "a fixed, bundled sequence... not
 * user-editable in v1") seeded into Room on first run. This is the 10-Routine
 * catalogue from `docs/workout-routines-system.md`, adopted by ADR-0008 and
 * `.scratch/bundled-routine-catalogue/spec.md` — each Routine's "Rounds" are
 * unrolled into a flat, ordered step sequence here (a Routine has no round
 * concept of its own). Every step uses the base [Exercise]; none hard-wire
 * [com.workoutpartner.core.repcounting.ExerciseVariant.STEP_JACK] — that
 * substitution happens through the Workout Overview's low-impact switch, not
 * seed data. [RoutineFormat] tags are display labels only (`workout-partner-v2`
 * ticket 02): `rt_09` is the one Routine the source doc itself names for
 * HIIT; every other Routine is [RoutineFormat.STANDARD].
 *
 * A few of the source doc's values needed a concrete pick where it gave a
 * range or a per-leg count — see the spec's Implementation Decisions for
 * each one (rep ranges collapsed to their upper bound; Lunge reps stored as
 * the two-leg total).
 *
 * [seedIfEmpty] only ever seeds once, when the table is empty — an install
 * that already seeded the previous 3-Routine catalogue does not pick this
 * one up automatically (`RoutineDao`'s own doc comment already anticipates
 * this as "a future reseed on update"; out of scope here).
 */
object BundledRoutines {
    suspend fun seedIfEmpty(routineDao: RoutineDao) {
        if (routineDao.getAllRoutinesWithSteps().isNotEmpty()) return
        all.forEach { (routine, steps) -> routineDao.insertRoutineWithSteps(routine, steps) }
    }

    /**
     * One flattened (Exercise, target reps, rest interval) step, before
     * [steps] assigns its [RoutineStepEntity.orderIndex] — its own named type
     * rather than a bare `Triple`, both so `.restIntervalSeconds` reads
     * better than `.third` in [rounds] and so `BundledRoutinesTest` can share
     * this exact shape instead of re-deriving its own. Named `StepSpec`, not
     * `Rep`, to avoid colliding with CONTEXT.md's own "Rep" glossary entry
     * (one cycle of an Exercise's motion) — this is a whole step, not a rep.
     */
    internal data class StepSpec(val exercise: Exercise, val targetReps: Int, val restIntervalSeconds: Int)

    private fun Exercise.step(targetReps: Int, restIntervalSeconds: Int) = StepSpec(this, targetReps, restIntervalSeconds)

    /**
     * Assigns contiguous [RoutineStepEntity.orderIndex]s to [specs] in the
     * order given — the doc's Rounds are unrolled by repeating a round's
     * specs (see [rounds]) before calling this, not by any round concept
     * here.
     */
    private fun steps(routineId: String, specs: List<StepSpec>): List<RoutineStepEntity> =
        specs.mapIndexed { index, spec ->
            RoutineStepEntity(routineId = routineId, orderIndex = index, exercise = spec.exercise, targetReps = spec.targetReps, restIntervalSeconds = spec.restIntervalSeconds)
        }

    /**
     * Repeats [round] [count] times back to back, then zeroes the rest of
     * the very last entry — the doc gives every round the same rest after
     * its last exercise, but that's the transition into the *next* round,
     * not into cool-down; only the true final step of the whole Routine
     * should carry 0 rest.
     */
    private fun rounds(count: Int, round: List<StepSpec>): List<StepSpec> =
        List(count) { round }.flatten().let { it.dropLast(1) + it.last().copy(restIntervalSeconds = 0) }

    /** internal, not private: read directly by `BundledRoutinesTest` rather than round-tripping through a Room [RoutineDao]. */
    internal val all: List<Pair<RoutineEntity, List<RoutineStepEntity>>> = listOf(
        // RT-01: Joint-Safe Mobility & Tone (BMI >= 30, Sedentary/Beginner) — 2 rounds, no jumping.
        RoutineEntity(id = "rt_01", name = "Joint-Safe Mobility & Tone") to steps(
            "rt_01",
            listOf(
                Exercise.SQUAT.step(8, 35),
                Exercise.PUSH_UP.step(6, 35),
                Exercise.JUMPING_JACK.step(15, 45),
                Exercise.SQUAT.step(8, 35),
                Exercise.PUSH_UP.step(6, 35),
                Exercise.JUMPING_JACK.step(15, 0),
            ),
        ),
        // RT-02: Gentle Low-Impact Cardio (BMI 28-33, Beginner) — 2 rounds.
        RoutineEntity(id = "rt_02", name = "Gentle Low-Impact Cardio") to steps(
            "rt_02",
            listOf(
                Exercise.SQUAT.step(10, 30),
                Exercise.JUMPING_JACK.step(20, 30),
                Exercise.LUNGE.step(12, 40),
                Exercise.SQUAT.step(10, 30),
                Exercise.PUSH_UP.step(8, 30),
                Exercise.JUMPING_JACK.step(20, 0),
            ),
        ),
        // RT-03: Lean Muscle Upper & Core (BMI < 18.5, Beginner-Intermediate) — same 3 exercises x3 rounds.
        RoutineEntity(id = "rt_03", name = "Lean Muscle Upper & Core") to steps(
            "rt_03",
            rounds(
                3,
                listOf(
                    Exercise.PUSH_UP.step(10, 45),
                    Exercise.SIT_UP.step(12, 45),
                    Exercise.PUSH_UP.step(6, 60),
                ),
            ),
        ),
        // RT-04: Lower Body Muscle Builder (BMI < 20, Intermediate) — same 3 exercises x3 rounds.
        RoutineEntity(id = "rt_04", name = "Lower Body Muscle Builder") to steps(
            "rt_04",
            rounds(
                3,
                listOf(
                    Exercise.SQUAT.step(12, 40),
                    Exercise.LUNGE.step(20, 40),
                    Exercise.SQUAT.step(10, 60),
                ),
            ),
        ),
        // RT-05: Steady Metabolic Burner (BMI 25-29.9, Moderate) — same 4 exercises x3 rounds.
        RoutineEntity(id = "rt_05", name = "Steady Metabolic Burner") to steps(
            "rt_05",
            rounds(
                3,
                listOf(
                    Exercise.JUMPING_JACK.step(25, 25),
                    Exercise.SQUAT.step(12, 30),
                    Exercise.PUSH_UP.step(8, 30),
                    Exercise.SIT_UP.step(10, 45),
                ),
            ),
        ),
        // RT-06: Core Stability & Flow (BMI 23-28, Moderate) — same 3 exercises x3 rounds.
        RoutineEntity(id = "rt_06", name = "Core Stability & Flow") to steps(
            "rt_06",
            rounds(
                3,
                listOf(
                    Exercise.SIT_UP.step(14, 30),
                    Exercise.SQUAT.step(12, 30),
                    Exercise.JUMPING_JACK.step(20, 40),
                ),
            ),
        ),
        // RT-07: Full Body Basics Plus (BMI 18.5-24.9, Moderate) — same 4 exercises x3 rounds.
        RoutineEntity(id = "rt_07", name = "Full Body Basics Plus") to steps(
            "rt_07",
            rounds(
                3,
                listOf(
                    Exercise.SQUAT.step(12, 25),
                    Exercise.PUSH_UP.step(10, 25),
                    Exercise.SIT_UP.step(12, 25),
                    Exercise.JUMPING_JACK.step(25, 45),
                ),
            ),
        ),
        // RT-08: Athletic Power Circuit (BMI 19-24.5, Active/Advanced) — same 4 exercises x3 rounds.
        RoutineEntity(id = "rt_08", name = "Athletic Power Circuit") to steps(
            "rt_08",
            rounds(
                3,
                listOf(
                    Exercise.LUNGE.step(14, 20),
                    Exercise.PUSH_UP.step(12, 20),
                    Exercise.SQUAT.step(15, 20),
                    Exercise.JUMPING_JACK.step(30, 40),
                ),
            ),
        ),
        // RT-09: High-Intensity Metabolic HIIT (BMI 18.5-26, Advanced) — same 4 exercises x4 rounds, the only HIIT-tagged Routine.
        RoutineEntity(id = "rt_09", name = "High-Intensity Metabolic HIIT", format = RoutineFormat.HIIT) to steps(
            "rt_09",
            rounds(
                4,
                listOf(
                    Exercise.JUMPING_JACK.step(35, 15),
                    Exercise.PUSH_UP.step(12, 15),
                    Exercise.SQUAT.step(15, 15),
                    Exercise.SIT_UP.step(12, 35),
                ),
            ),
        ),
        // RT-10: Express Desk-Worker Reset (all BMI) — 2 rounds.
        RoutineEntity(id = "rt_10", name = "Express Desk-Worker Reset") to steps(
            "rt_10",
            listOf(
                Exercise.SQUAT.step(10, 25),
                Exercise.JUMPING_JACK.step(20, 25),
                Exercise.LUNGE.step(8, 35),
                Exercise.SQUAT.step(10, 25),
                Exercise.JUMPING_JACK.step(20, 25),
                Exercise.SIT_UP.step(10, 0),
            ),
        ),
    )
}
