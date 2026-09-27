package com.workoutpartner.app.beforeyoustart

import androidx.annotation.StringRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.workoutpartner.app.R
import com.workoutpartner.app.debug.isDebuggableBuild
import com.workoutpartner.app.framing.DistanceStatus
import com.workoutpartner.app.framing.FramingScoreSmoother
import com.workoutpartner.app.framing.next
import com.workoutpartner.app.speech.PromptSpeaker
import com.workoutpartner.app.ui.components.FramingBorder
import com.workoutpartner.app.ui.components.PoseOverlay
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.posetracking.RawPoseFrame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Position Check phase's UI (`workout-partner-v3` ticket 12): a live
 * front-camera preview with a live-color framing border and a distance text
 * label. All the actual judgement lives in [engine] ([BeforeYouStartEngine],
 * tested by `BeforeYouStartEngineTest`), including when to give up waiting
 * and proceed anyway — this composable only feeds it camera frames and
 * once-a-second ticks, speaks whatever [PositionCue]s it emits, and calls
 * [onPhaseChanged] the moment the engine leaves Position Check. No button for
 * that: see [BeforeYouStartEngine]'s own doc comment for why a tap-gated
 * override doesn't work for what this screen is asking the Athlete to do
 * (step back out of the phone's reach).
 *
 * Camera-framing-indicator ticket 02 replaced the old static body-silhouette
 * outline with [FramingBorder] (shared with Session Tracking and Quick Count
 * Run) and this screen's own whole-body `bodyInFrame` readiness check with
 * [poseTracker]'s [PoseTracker.signals] — the same exercise-aware
 * Trackable/Lost the "Lost track of the person" banner uses elsewhere (see
 * ADR-0011) — collected here alongside the [PoseTracker.rawFrames] this
 * screen already needed for [BeforeYouStartEngine.onPoseFrame] and, now,
 * the border's continuous distance color.
 *
 * Unlike `SessionScreen`, this owns its tracker and ticker in the
 * composition (no ViewModel): the whole app's navigation state is plain
 * `remember`, so a ViewModel wouldn't survive a configuration change any
 * better here, and an activity-scoped one wouldn't stop the camera when the
 * Athlete backs out — [DisposableEffect] does. Not unit-tested — camera
 * preview, TTS and Compose layout, per this ticket's own "thin framework
 * adapters" line; unverified on a real device in this session.
 */
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
    val showPoseOverlay = remember { context.isDebuggableBuild() }
    var lastFrame by remember { mutableStateOf<RawPoseFrame?>(null) }
    var trackable by remember { mutableStateOf(false) }
    var framingCloseness by remember { mutableStateOf(0f) }
    val smoother = remember { FramingScoreSmoother() }

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
                if (showPoseOverlay) lastFrame = frame
                framingCloseness = smoother.next(frame)
                publish()
            }
        }
        launch(start = CoroutineStart.UNDISPATCHED) {
            poseTracker.signals.collect { signal -> trackable = signal is PoseTrackingSignal.Trackable }
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
    // Computed once and shared by the banner text below, so it can't disagree with the border about
    // whether the checks are actually passing right now.
    val allChecksPassing = trackable && status.distance == DistanceStatus.OK

    // Only while the Position Check is actively shown — the early return above means leaving this
    // composable drops the modifier automatically, letting the screen time out normally again.
    Box(modifier = modifier.fillMaxSize().keepScreenOn()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )
        if (showPoseOverlay) PoseOverlay(lastFrame, mirrored = poseTracker.mirrorsPreview, modifier = Modifier.fillMaxSize())
        FramingBorder(trackable = trackable, closeness = framingCloseness, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (allChecksPassing) {
                        "Looking good — hold still to begin."
                    } else if (!trackable) {
                        "Step into frame so we can see you clearly."
                    } else {
                        "Adjust your distance until the border turns green."
                    },
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
                // Hidden once distance passes, matching the spoken distance cue's own hide-when-passing behavior.
                if (status.distance != DistanceStatus.OK) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            distanceLabel(status.distance),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun distanceLabel(distance: DistanceStatus) = when (distance) {
    DistanceStatus.OK -> "Distance OK"
    DistanceStatus.TOO_FAR -> "Distance: too far — come closer"
    DistanceStatus.TOO_CLOSE -> "Distance: too close — step back"
    DistanceStatus.UNKNOWN -> "Distance: not detected yet"
}

@StringRes
private fun PositionCue.stringRes(): Int = when (this) {
    PositionCue.BODY_DETECTED -> R.string.position_check_body_detected
    PositionCue.STEP_BACK -> R.string.position_check_step_back
    PositionCue.MOVE_CLOSER -> R.string.position_check_move_closer
}
