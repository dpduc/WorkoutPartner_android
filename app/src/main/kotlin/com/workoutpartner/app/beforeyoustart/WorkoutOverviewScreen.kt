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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
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
 * phase and reports the two things the Athlete can do from it.
 *
 * "Review form" (spec.md story 50: "always available... even after I've
 * seen it") always opens the full set of guides via [onReviewForm]
 * (ticket 10) — unlike [onStart], never gated on seen-state.
 */
@Composable
fun WorkoutOverviewScreen(
    routine: RoutineWithSteps,
    difficultyTier: DifficultyTier,
    onReviewForm: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estimatedMinutes = remember(routine, difficultyTier) {
        (RoutineDurationEstimator.estimatedDurationSeconds(routine.steps, difficultyTier) / 60.0).let {
            if (it < 1.0) 1 else it.toInt()
        }
    }
    val stepDisplays = remember(routine, difficultyTier) {
        routine.steps.map { step ->
            StepDisplay(
                exerciseLabel = humanizeEnumName(step.exercise.name),
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

/**
 * The distinct [TrackedExercise]s a Routine's steps cover (`workout-partner-v3`
 * ticket 10) — what [BeforeYouStartScreen] checks against [FormGuidePrefs]
 * to decide which Form Guides are unseen. Always variant-less for now:
 * [com.workoutpartner.data.RoutineStepEntity] has no Variant column of its
 * own (a Routine's Jumping Jack steps aren't switched to Step Jack until
 * ticket 11 picks one per-Session), so every entry here is a base Exercise
 * until that ticket threads a chosen Variant through.
 */
fun RoutineWithSteps.trackedExercises(): List<TrackedExercise> = steps.map { TrackedExercise(it.exercise) }.distinct()

/** `JUMPING_JACK` -> `Jumping jack`; shared by the difficulty tier and Exercise labels above. */
private fun humanizeEnumName(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
