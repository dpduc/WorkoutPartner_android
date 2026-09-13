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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.RoutineWithSteps
import com.workoutpartner.data.SetRepository
import com.workoutpartner.core.posetracking.PoseTracker

/** Routine picker (ticket 09): the entry point into a Session — bundled Routines only, per spec.md's Out of Scope (no custom Routine authoring). */
@Composable
fun RoutinePickerScreen(routines: List<RoutineWithSteps>, onRoutineSelected: (RoutineWithSteps) -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(routines, key = { it.routine.id }) { routine ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = routine.routine.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = routine.steps.joinToString(" • ") { "${it.exercise.name.lowercase().replace('_', ' ')} x${it.targetReps}" },
                            style = MaterialTheme.typography.bodyMedium,
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
 * Account prompt when [accountId] is null (spec.md user story 3); that
 * prompt itself is out of this ticket's scope.
 */
@Composable
fun SessionScreen(
    routine: RoutineWithSteps,
    accountId: String?,
    setRepository: SetRepository,
    accountRepository: AccountRepository,
    poseTrackerFactory: () -> PoseTracker,
    onSessionComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer { SessionViewModel(routine, accountId, setRepository, accountRepository, poseTrackerFactory()) }
            }
        },
    )
    val phase by viewModel.phase.collectAsState()
    val account by viewModel.account.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
        when (val current = phase) {
            is SessionPhase.Countdown -> CountdownContent(current)
            is SessionPhase.Tracking -> TrackingContent(current, viewModel::startCamera, onFinishSet = viewModel::finishSet)
            is SessionPhase.SetSummary -> SetSummaryContent(current, onContinue = viewModel::acknowledgeSetSummary)
            is SessionPhase.Resting -> RestingContent(current, onSkip = viewModel::skipRest)
            is SessionPhase.SessionComplete -> SessionSummaryContent(current, account?.currentStreak, account?.weeklyTarget, onDone = onSessionComplete)
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

@Composable
private fun TrackingContent(
    phase: SessionPhase.Tracking,
    onCameraReady: (androidx.lifecycle.LifecycleOwner, Preview.SurfaceProvider) -> Unit,
    onFinishSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
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
                Text(text = phase.repCount.toString(), style = MaterialTheme.typography.displayLarge)
                Button(onClick = onFinishSet) { Text("Finish Set") }
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
