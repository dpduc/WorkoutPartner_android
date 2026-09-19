package com.workoutpartner.app.session

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.RoutineWithSteps
import com.workoutpartner.data.SetRepository
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.ExerciseVariant

/**
 * Routine picker (ticket 09): the entry point into a Session — bundled
 * Routines only, per spec.md's Out of Scope (no custom Routine authoring).
 * Each card shows its Routine's format tag and the account's computed
 * [difficultyTier] (`workout-partner-v2` ticket 02) as badges — purely
 * informational here; the actual rep/rest scaling happens when
 * [SessionViewModel] builds its engine steps.
 */
@Composable
fun RoutinePickerScreen(
    routines: List<RoutineWithSteps>,
    difficultyTier: DifficultyTier,
    onRoutineSelected: (RoutineWithSteps) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(routines, key = { it.routine.id }) { routine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = routine.routine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${routine.routine.format.name} • ${difficultyTier.name.lowercase().replaceFirstChar(Char::uppercase)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = routine.steps.joinToString(" • ") { "${it.exercise.name.lowercase().replace('_', ' ')} x${it.targetReps}" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(onClick = { onRoutineSelected(routine) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Start")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hosts [SessionViewModel] and switches between the phases spec.md's
 * "Specific interactions" describes. [onSessionComplete] fires once the
 * Session summary is dismissed — ticket 13 wires this to the Guest ->
 * Account prompt when [accountId] is null (spec.md user story 3) — that
 * prompt's own UI is out of this ticket's scope, but [onSetFinished] is the
 * trigger point ticket 13 hooks into: story 3 says "after finishing **a
 * Set**," not after the whole Session, so it fires once per completed Set
 * (when its summary is acknowledged), not just once at [onSessionComplete].
 */
@Composable
fun SessionScreen(
    routine: RoutineWithSteps,
    accountId: String?,
    setRepository: SetRepository,
    accountRepository: AccountRepository,
    poseTrackerFactory: () -> PoseTracker,
    difficultyTier: DifficultyTier = DifficultyTier.STANDARD,
    /** The Athlete's Overview toggle choice for this Session's Jumping Jack steps (`workout-partner-v3` ticket 11) — see [SessionViewModel]. */
    jumpingJackVariant: ExerciseVariant? = null,
    onSetFinished: () -> Unit = {},
    onSessionComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer {
                    SessionViewModel(routine, accountId, setRepository, accountRepository, poseTrackerFactory(), difficultyTier, jumpingJackVariant)
                }
            }
        },
    )
    val phase by viewModel.phase.collectAsState()
    val account by viewModel.account.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
        when (val current = phase) {
            is SessionPhase.Countdown -> CountdownContent(current)
            is SessionPhase.Tracking -> TrackingContent(
                current,
                routine = routine,
                onCameraReady = viewModel::startCamera,
                onFinishSet = viewModel::finishSet,
            )
            is SessionPhase.SetSummary -> SetSummaryContent(
                current,
                onContinue = {
                    onSetFinished()
                    viewModel.acknowledgeSetSummary()
                },
            )
            is SessionPhase.Resting -> RestingContent(current, onSkip = viewModel::skipRest)
            is SessionPhase.SessionComplete -> SessionSummaryContent(current, account?.currentStreak, account?.weeklyTarget, onDone = onSessionComplete)
            is SessionPhase.CameraUnavailable -> CameraUnavailableContent(current, onDone = onSessionComplete)
        }
    }
}

@Composable
private fun CountdownContent(phase: SessionPhase.Countdown, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Get ready", style = MaterialTheme.typography.titleLarge)
            Text(text = phase.secondsRemaining.toString(), style = MaterialTheme.typography.displayLarge)
        }
    }
}

/**
 * The live rep-tracking layout (`workout-partner-v3` ticket 04): sized for
 * an Athlete standing ~2m from the phone, per the phone-down/camera-at-a-
 * distance setup Before You Start's Position Check (ticket 12) will confirm
 * — huge rep count, large Exercise (or Exercise Variant, once ticket 08
 * adds one) name, a wide progress bar toward the rep target, smaller
 * secondary info (the Set's position in the Routine). [SessionPhase.Tracking.targetReps]
 * is already the difficulty-adjusted value [SessionEngine] itself is
 * tracking toward — read from the engine's own phase rather than
 * recomputed here, so this layout can never drift from what a Good Set
 * actually requires.
 */
@Composable
private fun TrackingContent(
    phase: SessionPhase.Tracking,
    routine: RoutineWithSteps,
    onCameraReady: (androidx.lifecycle.LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    onFinishSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val step = routine.steps[phase.stepIndex]
    val progress = (phase.repCount.toFloat() / phase.targetReps.coerceAtLeast(1)).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).also { previewView ->
                    onCameraReady(lifecycleOwner, previewView.surfaceProvider)
                }
            },
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!phase.trackable) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Lost track of you — step back into frame",
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Set ${phase.stepIndex + 1} of ${routine.steps.size}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp),
                )
                Text(
                    text = step.exercise.name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = phase.repCount.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp, fontWeight = FontWeight.Bold),
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Text(
                    text = "${phase.targetReps} reps",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp),
                )
                Button(onClick = onFinishSet, modifier = Modifier.padding(top = 12.dp)) { Text("Finish Set") }
            }
        }
    }
}

@Composable
private fun SetSummaryContent(phase: SessionPhase.SetSummary, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val set = phase.completedSet
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (set.goodSet) "Good Set!" else "Set complete", style = MaterialTheme.typography.titleLarge)
            Text("${set.actualReps} / ${set.targetReps} reps")
            Text("Form Score: ${set.formScore}")
            Text(text = set.formNote, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onContinue, modifier = Modifier.padding(top = 16.dp)) { Text("Continue") }
        }
    }
}

@Composable
private fun CameraUnavailableContent(phase: SessionPhase.CameraUnavailable, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera unavailable", style = MaterialTheme.typography.titleLarge)
            Text(text = phase.message, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text("Back") }
        }
    }
}

@Composable
private fun RestingContent(phase: SessionPhase.Resting, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Rest", style = MaterialTheme.typography.titleLarge)
            Text(text = phase.secondsRemaining.toString(), style = MaterialTheme.typography.displayLarge)
            TextButton(onClick = onSkip, modifier = Modifier.padding(top = 16.dp)) { Text("Skip rest") }
        }
    }
}

@Composable
private fun SessionSummaryContent(
    phase: SessionPhase.SessionComplete,
    currentStreak: Int?,
    weeklyTarget: Int?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Session complete", style = MaterialTheme.typography.titleLarge)
        LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(phase.completedSets) { set ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(set.exercise.name.lowercase().replace('_', ' '))
                        Text("${set.actualReps} / ${set.targetReps} reps — Form Score ${set.formScore}${if (set.goodSet) " — Good Set" else ""}")
                        Text(text = set.formNote, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        // Weekly Target progress (ticket 04's Streak Calculator output) —
        // only shown for a signed-in Account; a Guest has no Streak yet.
        if (currentStreak != null && weeklyTarget != null) {
            Text("Streak: $currentStreak weeks (Weekly Target: $weeklyTarget Active Days)")
        }
        Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text("Done") }
    }
}
