package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.data.RoutineStepEntity

/**
 * Estimates how long a Routine will take (spec.md user story 48's "estimated
 * duration"), from the same difficulty-adjusted rep targets/rest intervals
 * [com.workoutpartner.app.session.SessionViewModel] builds its engine steps
 * from — this never invents its own numbers the Session itself won't use.
 *
 * [SECONDS_PER_REP] is an assumed constant, not measured from real Sessions
 * — the same "defensible default, documented as a placeholder" spirit as
 * [RoutineDifficulty]'s own thresholds and [com.workoutpartner.app.session.SessionEngine]'s
 * Good Set threshold.
 */
object RoutineDurationEstimator {
    private const val SECONDS_PER_REP = 3

    fun estimatedDurationSeconds(steps: List<RoutineStepEntity>, difficultyTier: DifficultyTier): Int =
        steps.sumOf { step ->
            val reps = RoutineDifficulty.adjustedTargetReps(step.targetReps, difficultyTier)
            val restSeconds = RoutineDifficulty.adjustedRestIntervalSeconds(step.restIntervalSeconds, difficultyTier)
            reps * SECONDS_PER_REP + restSeconds
        }
}
