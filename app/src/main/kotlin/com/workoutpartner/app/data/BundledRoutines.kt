package com.workoutpartner.app.data

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.RoutineDao
import com.workoutpartner.data.RoutineEntity
import com.workoutpartner.data.RoutineFormat
import com.workoutpartner.data.RoutineStepEntity

/**
 * The bundled Routines (CONTEXT.md: "a fixed, bundled sequence... not
 * user-editable in v1") seeded into Room on first run. Exact content isn't
 * specified by spec.md beyond "a small set of Routines" — picked here, the
 * same "reasonable default, not a settled product decision" spirit as
 * ticket 02's angle thresholds.
 *
 * [RoutineEntity.format] tags (`workout-partner-v2` ticket 02) are display
 * labels only — the step sequences below still run through the same
 * rep-target + rest-interval engine regardless of tag; see
 * [RoutineFormat]'s doc comment.
 */
object BundledRoutines {
    suspend fun seedIfEmpty(routineDao: RoutineDao) {
        if (routineDao.getAllRoutinesWithSteps().isNotEmpty()) return
        all.forEach { (routine, steps) -> routineDao.insertRoutineWithSteps(routine, steps) }
    }

    private val all: List<Pair<RoutineEntity, List<RoutineStepEntity>>> = listOf(
        RoutineEntity(id = "full_body_basics", name = "Full Body Basics", format = RoutineFormat.HIIT) to listOf(
            RoutineStepEntity(routineId = "full_body_basics", orderIndex = 0, exercise = Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "full_body_basics", orderIndex = 1, exercise = Exercise.PUSH_UP, targetReps = 10, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "full_body_basics", orderIndex = 2, exercise = Exercise.SIT_UP, targetReps = 10, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "full_body_basics", orderIndex = 3, exercise = Exercise.JUMPING_JACK, targetReps = 20, restIntervalSeconds = 0),
        ),
        RoutineEntity(id = "lower_body_focus", name = "Lower Body Focus", format = RoutineFormat.TABATA) to listOf(
            RoutineStepEntity(routineId = "lower_body_focus", orderIndex = 0, exercise = Exercise.SQUAT, targetReps = 15, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "lower_body_focus", orderIndex = 1, exercise = Exercise.LUNGE, targetReps = 10, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "lower_body_focus", orderIndex = 2, exercise = Exercise.SQUAT, targetReps = 15, restIntervalSeconds = 0),
        ),
        RoutineEntity(id = "quick_upper_body", name = "Quick Upper Body", format = RoutineFormat.AMRAP) to listOf(
            RoutineStepEntity(routineId = "quick_upper_body", orderIndex = 0, exercise = Exercise.PUSH_UP, targetReps = 12, restIntervalSeconds = 30),
            RoutineStepEntity(routineId = "quick_upper_body", orderIndex = 1, exercise = Exercise.JUMPING_JACK, targetReps = 20, restIntervalSeconds = 0),
        ),
    )
}
