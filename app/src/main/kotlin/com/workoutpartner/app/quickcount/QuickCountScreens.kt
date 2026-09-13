package com.workoutpartner.app.quickcount

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.TallyRepository
import com.workoutpartner.data.TrackedProfileEntity

/** Pick an Exercise and an optional target count for [profile] (spec.md story 35/36), then start the run. */
@Composable
fun QuickCountSetupScreen(profile: TrackedProfileEntity, onStart: (Exercise, Int?) -> Unit, modifier: Modifier = Modifier) {
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    var targetText by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Quick Count for ${profile.displayName}", style = MaterialTheme.typography.titleLarge)
            LazyRow(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Exercise.entries.toList()) { exercise ->
                    Button(onClick = { selectedExercise = exercise }) {
                        Text(exercise.name.lowercase().replace('_', ' '))
                    }
                }
            }
            Text("Selected: ${selectedExercise?.name?.lowercase()?.replace('_', ' ') ?: "none"}", modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it.filter(Char::isDigit) },
                label = { Text("Target count (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Button(
                onClick = { selectedExercise?.let { onStart(it, targetText.toIntOrNull()) } },
                enabled = selectedExercise != null,
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Start") }
        }
    }
}

/** The Quick Count run itself: camera + live counter, auto-stopping at the target or manually via [QuickCountViewModel.stop]. */
@Composable
fun QuickCountRunScreen(
    trackedProfileId: String,
    exercise: Exercise,
    target: Int?,
    tallyRepository: TallyRepository,
    poseTrackerFactory: () -> PoseTracker,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuickCountViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer { QuickCountViewModel(trackedProfileId, exercise, target, tallyRepository, poseTrackerFactory()) }
            }
        },
    )
    val phase by viewModel.phase.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    Surface(modifier = modifier.fillMaxSize()) {
        when (val current = phase) {
            is QuickCountPhase.Running -> Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        PreviewView(context).also { previewView ->
                            viewModel.startCamera(lifecycleOwner, previewView.surfaceProvider)
                        }
                    },
                )
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    if (!current.trackable) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text("Lost track of the person — step back into frame", modifier = Modifier.padding(12.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(text = current.repCount.toString(), style = MaterialTheme.typography.displayLarge)
                        if (target != null) Text("Target: $target")
                        Button(onClick = viewModel::stop) { Text("Stop") }
                    }
                }
            }
            is QuickCountPhase.Finished -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tally saved", style = MaterialTheme.typography.titleLarge)
                    Text("${current.repCount} reps")
                    Button(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) { Text("Done") }
                }
            }
        }
    }
}
