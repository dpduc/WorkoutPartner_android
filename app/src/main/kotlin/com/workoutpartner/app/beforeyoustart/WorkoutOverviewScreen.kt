package com.workoutpartner.app.beforeyoustart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.RoutineWithSteps

/**
 * The Workout Overview (CONTEXT.md's Before You Start): the first thing an
 * Athlete sees after picking a Routine, before its first Set — name,
 * estimated duration, difficulty tier, each Exercise with its
 * difficulty-adjusted rep target, and safety notes (spec.md stories 47-49).
 *
 * Purely presentational — doesn't touch [BeforeYouStartEngine] itself;
 * [BeforeYouStartScreen] is what owns the engine and decides, from
 * [onStart], whether Form Guides needs to run before the Session starts
 * (`workout-partner-v3` ticket 10). This screen only renders the Overview
 * phase and reports the things the Athlete can do from it.
 *
 * "Review form" (spec.md story 50: "always available... even after I've
 * seen it") always opens the full set of guides via [onReviewForm]
 * (ticket 10) — unlike [onStart], never gated on seen-state.
 *
 * The Jumping Jack/Step Jack toggle (`workout-partner-v3` ticket 11) is
 * hoisted state, not owned here: [jumpingJackVariant]/[onJumpingJackVariantChange]
 * let [BeforeYouStartScreen] keep the Athlete's choice alive across a
 * detour into Form Guides (a different composable in that screen's `when`,
 * which would otherwise forget local state on the way back). Shown only
 * when [routine] actually has a Jumping Jack step.
 */
@Composable
fun WorkoutOverviewScreen(
    routine: RoutineWithSteps,
    difficultyTier: DifficultyTier,
    jumpingJackVariant: ExerciseVariant?,
    onJumpingJackVariantChange: (ExerciseVariant?) -> Unit,
    onReviewForm: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasJumpingJack = remember(routine) { routine.hasJumpingJack }
    val estimatedMinutes = remember(routine, difficultyTier) {
        (RoutineDurationEstimator.estimatedDurationSeconds(routine.steps, difficultyTier) / 60.0).let {
            if (it < 1.0) 1 else it.toInt()
        }
    }
    val stepDisplays = remember(routine, difficultyTier, jumpingJackVariant) {
        routine.steps.map { step ->
            val exerciseOrVariant = resolveVariant(step.exercise, jumpingJackVariant)?.name ?: step.exercise.name
            StepDisplay(
                exerciseLabel = humanizeEnumName(exerciseOrVariant),
                adjustedReps = RoutineDifficulty.adjustedTargetReps(step.targetReps, difficultyTier),
            )
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(routine.routine.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${routine.routine.format.name} • ${humanizeEnumName(difficultyTier.name)} • ~$estimatedMinutes min",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("What you'll do", style = MaterialTheme.typography.titleMedium)
                    stepDisplays.forEach { step ->
                        Text(
                            "${step.exerciseLabel} — ${step.adjustedReps} reps",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            if (hasJumpingJack) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Jumping Jack style", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            SegmentedButton(
                                selected = jumpingJackVariant != ExerciseVariant.STEP_JACK,
                                onClick = { onJumpingJackVariantChange(null) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) { Text("Jumping Jack") }
                            SegmentedButton(
                                selected = jumpingJackVariant == ExerciseVariant.STEP_JACK,
                                onClick = { onJumpingJackVariantChange(ExerciseVariant.STEP_JACK) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) { Text("Step Jack") }
                        }
                        Text(
                            "Step Jack is a low-impact alternative — step out to the side instead of jumping.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Before you start", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Make sure you have about 2m × 2m of clear space, good lighting, and fitted clothing so the camera can track you accurately.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            HorizontalDivider()

            OutlinedButton(onClick = onReviewForm, modifier = Modifier.fillMaxWidth()) {
                Text("Review form")
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }
        }
    }
}

private data class StepDisplay(val exerciseLabel: String, val adjustedReps: Int)

/** Whether [RoutineWithSteps] has a Jumping Jack step at all (`workout-partner-v3` ticket 11) — gates whether the Overview's Jumping Jack/Step Jack toggle shows up, shared with [BeforeYouStartScreen] rather than each recomputing it. */
val RoutineWithSteps.hasJumpingJack: Boolean get() = steps.any { it.exercise == Exercise.JUMPING_JACK }

/**
 * The distinct [TrackedExercise]s a Routine's steps cover (`workout-partner-v3`
 * ticket 10) — what [BeforeYouStartScreen] checks against [FormGuidePrefs]
 * to decide which Form Guides are unseen, and what "Review form" opens.
 * [jumpingJackVariant] (ticket 11's Overview toggle) is resolved per step by
 * [resolveVariant], since [com.workoutpartner.data.RoutineStepEntity] carries
 * no Variant of its own — the Routine only ever names the base Exercise;
 * the Session's chosen Variant for this run isn't part of it. Defaults to
 * `null` (no substitution) so ticket 10's own call sites, made before this
 * toggle existed, are unaffected.
 */
fun RoutineWithSteps.trackedExercises(jumpingJackVariant: ExerciseVariant? = null): List<TrackedExercise> =
    steps.map { step -> TrackedExercise(step.exercise, resolveVariant(step.exercise, jumpingJackVariant)) }.distinct()

/**
 * The single "does this chosen Variant apply to this step" rule
 * (`workout-partner-v3` ticket 11) — a [chosenVariant] only ever substitutes
 * in for a step of its own [ExerciseVariant.parentExercise], so a chosen
 * Step Jack never bleeds onto, say, a Squat step. Shared by every place
 * that needs to resolve a step's effective Variant: this file's own
 * [trackedExercises] and Overview labels, and
 * [com.workoutpartner.app.session.SessionViewModel]'s `RoutineStep` building
 * — rather than each re-deriving the same `exercise == JUMPING_JACK` check.
 */
fun resolveVariant(exercise: Exercise, chosenVariant: ExerciseVariant?): ExerciseVariant? =
    chosenVariant?.takeIf { it.parentExercise == exercise }

/** `JUMPING_JACK` -> `Jumping jack`; shared by the difficulty tier and Exercise labels above. */
private fun humanizeEnumName(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
