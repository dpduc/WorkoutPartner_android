package com.workoutpartner.app.beforeyoustart

import androidx.annotation.StringRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.workoutpartner.app.R
import com.workoutpartner.app.speech.PromptSpeaker
import com.workoutpartner.core.posetracking.PoseTracker
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Position Check phase's UI (`workout-partner-v3` ticket 12): a live
 * front-camera preview with a body outline, "whole body in frame" and
 * "distance OK" indicators, and a "Start anyway" button once
 * [PositionCheckStatus.startAnywayAvailable]. All the actual judgement lives
 * in [engine] ([BeforeYouStartEngine], tested by `BeforeYouStartEngineTest`);
 * this composable only feeds it camera frames and once-a-second ticks,
 * speaks whatever [PositionCue]s it emits, and calls [onPhaseChanged] the
 * moment the engine leaves Position Check (auto-advance or "Start anyway").
 *
 * Unlike `SessionScreen`, this owns its tracker and ticker in the
 * composition (no ViewModel): the whole app's navigation state is plain
 * `remember`, so a ViewModel wouldn't survive a configuration change any
 * better here, and an activity-scoped one wouldn't stop the camera when the
 * Athlete backs out — [DisposableEffect] does. Not unit-tested — camera
 * preview, TTS and Compose layout, per this ticket's own "thin framework
 * adapters" line; unverified on a real device in this session.
 */
private val PASS_COLOR = Color(0xFF2E7D32)
private const val OUTLINE_STROKE_WIDTH = 6f

@Composable
fun PositionCheckScreen(
    engine: BeforeYouStartEngine,
    poseTracker: PoseTracker,
    /** Speaks the Position Check's cues; owned by [BeforeYouStartScreen] so it outlives this phase (the countdown's first-Set line uses it too). */
    speaker: PromptSpeaker,
    onPhaseChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var phase by remember { mutableStateOf(engine.phase) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    fun publish() {
        while (true) {
            val cue = engine.takeCue() ?: break
            speaker.speak(context.getString(cue.stringRes()))
        }
        phase = engine.phase
        if (phase !is BeforeYouStartPhase.PositionCheck) onPhaseChanged()
    }

    DisposableEffect(poseTracker) {
        onDispose {
            poseTracker.stop()
        }
    }
    LaunchedEffect(poseTracker) {
        // UNDISPATCHED: each collector must be attached before start(), or an immediate start
        // error / the first frames would have nobody listening (SessionViewModel collects first, too).
        launch(start = CoroutineStart.UNDISPATCHED) {
            poseTracker.rawFrames.collect { frame ->
                engine.onPoseFrame(frame)
                publish()
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) { poseTracker.errors.collect { cameraError = it } }
        poseTracker.start(lifecycleOwner, previewView.surfaceProvider)
        while (true) {
            delay(1000)
            engine.onTick()
            publish()
        }
    }

    // Every effect above is already registered; once the engine leaves Position Check there's nothing left to draw.
    val status = (phase as? BeforeYouStartPhase.PositionCheck)?.status ?: return

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )
        BodyOutline(
            color = if (status.bodyInFrame && status.distance == DistanceStatus.OK) PASS_COLOR else Color.White,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Put your phone down, then step back until your whole body fits the outline.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cameraError?.let {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Indicator(passed = status.bodyInFrame, label = "Whole body in frame")
                        Indicator(passed = status.distance == DistanceStatus.OK, label = distanceLabel(status.distance))
                    }
                }
                if (status.startAnywayAvailable) {
                    Button(onClick = { engine.startAnyway(); publish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start anyway")
                    }
                }
            }
        }
    }
}

@Composable
private fun Indicator(passed: Boolean, label: String) {
    Text(
        text = "${if (passed) "✓" else "✗"}  $label",
        style = MaterialTheme.typography.titleMedium,
        color = if (passed) PASS_COLOR else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

private fun distanceLabel(distance: DistanceStatus) = when (distance) {
    DistanceStatus.OK -> "Distance OK"
    DistanceStatus.TOO_FAR -> "Distance: too far — come closer"
    DistanceStatus.TOO_CLOSE -> "Distance: too close — step back"
    DistanceStatus.UNKNOWN -> "Distance: not detected yet"
}

/** A simple head-and-body silhouette outline, centered and sized to the middle of the 40–80% skeleton-height window the check wants. */
@Composable
private fun BodyOutline(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bodyHeight = size.height * (PositionCheckEvaluator.MIN_SKELETON_HEIGHT_FRACTION + PositionCheckEvaluator.MAX_SKELETON_HEIGHT_FRACTION) / 2f
        val top = (size.height - bodyHeight) / 2f
        val centerX = size.width / 2f
        val headRadius = bodyHeight * 0.07f
        val stroke = Stroke(width = OUTLINE_STROKE_WIDTH)

        drawCircle(color, headRadius, Offset(centerX, top + headRadius), style = stroke)
        val torsoTop = top + headRadius * 2.4f
        val torsoWidth = bodyHeight * 0.28f
        val torsoHeight = bodyHeight * 0.36f
        drawRect(color, Offset(centerX - torsoWidth / 2f, torsoTop), Size(torsoWidth, torsoHeight), style = stroke)
        drawLine(color, Offset(centerX - torsoWidth / 2f, torsoTop), Offset(centerX - torsoWidth, torsoTop + torsoHeight), strokeWidth = OUTLINE_STROKE_WIDTH)
        drawLine(color, Offset(centerX + torsoWidth / 2f, torsoTop), Offset(centerX + torsoWidth, torsoTop + torsoHeight), strokeWidth = OUTLINE_STROKE_WIDTH)
        val legTop = torsoTop + torsoHeight
        val legBottom = top + bodyHeight
        drawLine(color, Offset(centerX - torsoWidth / 4f, legTop), Offset(centerX - torsoWidth / 4f, legBottom), strokeWidth = OUTLINE_STROKE_WIDTH)
        drawLine(color, Offset(centerX + torsoWidth / 4f, legTop), Offset(centerX + torsoWidth / 4f, legBottom), strokeWidth = OUTLINE_STROKE_WIDTH)
    }
}

@StringRes
private fun PositionCue.stringRes(): Int = when (this) {
    PositionCue.BODY_DETECTED -> R.string.position_check_body_detected
    PositionCue.STEP_BACK -> R.string.position_check_step_back
    PositionCue.MOVE_CLOSER -> R.string.position_check_move_closer
}
