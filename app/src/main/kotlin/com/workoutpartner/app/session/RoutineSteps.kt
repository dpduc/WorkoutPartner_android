package com.workoutpartner.app.session

import com.workoutpartner.app.beforeyoustart.resolveVariant
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.RoutineWithSteps

/**
 * A Routine's steps as [SessionEngine] runs them: rep targets and rests
 * scaled by [difficultyTier] ([RoutineDifficulty]), and the Athlete's
 * per-Session Jumping Jack/Step Jack choice ([jumpingJackVariant],
 * `workout-partner-v3` ticket 11) resolved onto each step. Shared by
 * [SessionViewModel] and the Before You Start countdown's first-Set
 * announcement (ticket 13), so both speak and run the very same numbers.
 */
fun RoutineWithSteps.toRoutineSteps(difficultyTier: DifficultyTier, jumpingJackVariant: ExerciseVariant?): List<RoutineStep> =
    steps.map {
        RoutineStep(
            exercise = it.exercise,
            targetReps = RoutineDifficulty.adjustedTargetReps(it.targetReps, difficultyTier),
            restIntervalSeconds = RoutineDifficulty.adjustedRestIntervalSeconds(it.restIntervalSeconds, difficultyTier),
            variant = resolveVariant(it.exercise, jumpingJackVariant),
        )
    }
