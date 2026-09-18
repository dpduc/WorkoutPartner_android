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
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.data.RoutineWithSteps

/**
 * The Workout Overview (CONTEXT.md's Before You Start): the first thing an
 * Athlete sees after picking a Routine, before its first Set — name,
 * estimated duration, difficulty tier, each Exercise with its
 * difficulty-adjusted rep target, and safety notes (spec.md stories 47-49).
 *
 * Doesn't touch [BeforeYouStartEngine] yet: Form Guides/Position Check/
 * Countdown have no real UI (tickets 10/12/13 add it), so this ticket's
 * flow is just Overview then straight into the Session — the engine's own
 * tests are what prove out the phase sequence for now; wiring a screen to
 * it starts once a phase after Overview actually has something to show.
 *
 * "Review form" (spec.md story 50: "always available... even after I've
 * seen it") is disabled here — ticket 10 is what gives it Form Guides to
 * actually open.
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

            OutlinedButton(onClick = onReviewForm, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Review form (coming soon)")
            }
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Start")
            }
        }
    }
}

private data class StepDisplay(val exerciseLabel: String, val adjustedReps: Int)

/** `JUMPING_JACK` -> `Jumping jack`; shared by the difficulty tier and Exercise labels above. */
private fun humanizeEnumName(name: String): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
