package com.workoutpartner.app.beforeyoustart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.RoutineWithSteps

/**
 * Owns the [BeforeYouStartEngine] for one chosen Routine and renders
 * whichever phase it's on (`workout-partner-v3` ticket 03's phase skeleton,
 * ticket 10's first real phase beyond Overview). [onReadyForSession] fires
 * once the flow reaches a phase with no real UI yet — currently
 * [BeforeYouStartPhase.PositionCheck] — the same "proceed straight into the
 * Session" placeholder ticket 03 used for the whole flow before this
 * ticket, now pushed one phase later; tickets 12/13 replace it once
 * Position Check/Countdown have something to show. It carries the
 * Athlete's chosen Jumping Jack/Step Jack Variant (ticket 11) along for the
 * ride, so [onReadyForSession] can hand it to the Session that's about to
 * start.
 *
 * "Review form" is deliberately *not* routed through the engine: the
 * ticket asks for it to reopen every guide "anytime," regardless of the
 * Athlete's progress toward starting the Session, so it's a self-contained
 * detour back to the Overview rather than a phase transition.
 *
 * [jumpingJackVariant] is hoisted to this screen, not [WorkoutOverviewScreen]
 * itself, because the Athlete's choice needs to survive a detour into
 * [FormGuidesScreen] — a different branch of the `when` below, which would
 * discard `remember`ed state scoped to [WorkoutOverviewScreen] alone.
 */
@Composable
fun BeforeYouStartScreen(
    routine: RoutineWithSteps,
    difficultyTier: DifficultyTier,
    /** Whether the Athlete's BMI is ≥ 30 (`workout-partner-v3` ticket 11) — [WorkoutOverviewScreen]'s Jumping Jack/Step Jack toggle defaults to Step Jack when true. See [com.workoutpartner.app.routines.RoutineDifficulty.isObese]. */
    defaultToStepJack: Boolean,
    formGuidePrefs: FormGuidePrefs,
    onReadyForSession: (jumpingJackVariant: ExerciseVariant?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var jumpingJackVariant by remember(routine) {
        mutableStateOf(if (routine.hasJumpingJack && defaultToStepJack) ExerciseVariant.STEP_JACK else null)
    }
    val trackedExercises = remember(routine, jumpingJackVariant) { routine.trackedExercises(jumpingJackVariant) }
    var reviewingAllGuides by remember { mutableStateOf(false) }
    var engine by remember { mutableStateOf<BeforeYouStartEngine?>(null) }

    val currentPhase = engine?.phase

    when {
        reviewingAllGuides -> FormGuidesScreen(
            guides = trackedExercises,
            formGuidePrefs = formGuidePrefs,
            onDone = { reviewingAllGuides = false },
            modifier = modifier,
        )

        currentPhase is BeforeYouStartPhase.FormGuides -> FormGuidesScreen(
            guides = currentPhase.guides,
            formGuidePrefs = formGuidePrefs,
            onDone = {
                engine?.advance() // FormGuides -> PositionCheck
                onReadyForSession(jumpingJackVariant)
            },
            modifier = modifier,
        )

        else -> WorkoutOverviewScreen(
            routine = routine,
            difficultyTier = difficultyTier,
            jumpingJackVariant = jumpingJackVariant,
            onJumpingJackVariantChange = { jumpingJackVariant = it },
            onReviewForm = { reviewingAllGuides = true },
            onStart = {
                val unseen = trackedExercises.filterNot(formGuidePrefs::hasSeen)
                val newEngine = BeforeYouStartEngine(unseen)
                newEngine.advance() // Overview -> FormGuides, or straight to PositionCheck if nothing's unseen
                if (newEngine.phase is BeforeYouStartPhase.FormGuides) {
                    engine = newEngine
                } else {
                    onReadyForSession(jumpingJackVariant)
                }
            },
            modifier = modifier,
        )
    }
}
