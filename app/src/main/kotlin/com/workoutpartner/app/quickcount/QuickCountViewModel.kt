package com.workoutpartner.app.quickcount

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.app.framing.FramingScoreSmoother
import com.workoutpartner.app.framing.FramingScorer
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.TallyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The impure glue for one Quick Count run (ticket 11): drives
 * [QuickCountEngine] with the real [PoseTracker] camera stream and saves
 * exactly one [com.workoutpartner.data.TallyEntity] once the run finishes
 * (target reached or manually [stop]ped) — including its average Form Score
 * and elapsed duration (`workout-partner-v2` ticket 03; the engine computes
 * the former, this class the latter, since [QuickCountEngine] stays pure
 * and doesn't read a clock itself). Not unit-tested, the same "impure shell
 * around a tested pure engine" split as `SessionViewModel`/`CameraPoseTracker`.
 *
 * Camera-framing-indicator ticket 04 additionally collects [PoseTracker.rawFrames]
 * (new — this class had no use for them before) through a [FramingScoreSmoother]
 * into [closeness], so [QuickCountRunScreen] can drive the shared
 * [com.workoutpartner.app.ui.components.FramingBorder] the same way
 * `PositionCheckScreen` already does; [QuickCountPhase.Running.trackable]
 * (already collected from [PoseTracker.signals] via [QuickCountEngine]) is
 * the border's other input. Quick Count tracks a single Exercise for its
 * whole run, so unlike `SessionViewModel` there's no per-step profile update
 * to push to [poseTracker] here.
 */
class QuickCountViewModel(
    private val trackedProfileId: String,
    private val exercise: Exercise,
    private val target: Int?,
    private val tallyRepository: TallyRepository,
    private val poseTracker: PoseTracker,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val engine = QuickCountEngine(exercise, target)
    private val startedAt: Instant = Instant.now(clock)

    private val _phase = MutableStateFlow(engine.phase)
    val phase: StateFlow<QuickCountPhase> = _phase.asStateFlow()

    /** Set once, alongside the Tally save, when the run finishes — the engine has no clock of its own to compute this (see this class's own doc comment), so it can't live on [QuickCountPhase.Finished] the way [QuickCountPhase.Finished.formScore] does. */
    private val _durationSeconds = MutableStateFlow<Int?>(null)
    val durationSeconds: StateFlow<Int?> = _durationSeconds.asStateFlow()

    private val smoother = FramingScoreSmoother()

    /** [FramingScorer]'s continuous distance-closeness score, smoothed across [poseTracker]'s [PoseTracker.rawFrames] — feeds the run screen's [com.workoutpartner.app.ui.components.FramingBorder] color (camera-framing-indicator ticket 04). */
    private val _closeness = MutableStateFlow(0f)
    val closeness: StateFlow<Float> = _closeness.asStateFlow()

    private var tallySaved = false

    init {
        viewModelScope.launch {
            poseTracker.signals.collect { signal ->
                engine.onPoseSignal(signal)
                _phase.value = engine.phase
                saveTallyIfJustFinished()
            }
        }
        viewModelScope.launch {
            poseTracker.rawFrames.collect { frame ->
                _closeness.value = smoother.next(FramingScorer.evaluate(frame).closeness)
            }
        }
        viewModelScope.launch {
            poseTracker.errors.collect { message ->
                _phase.value = QuickCountPhase.CameraUnavailable(message)
            }
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        poseTracker.start(lifecycleOwner, previewSurfaceProvider)
    }

    /** Manually ends the run (spec.md story 37). No-op if already finished. */
    fun stop() {
        engine.stop()
        _phase.value = engine.phase
        saveTallyIfJustFinished()
    }

    private fun saveTallyIfJustFinished() {
        if (tallySaved) return
        val finished = _phase.value as? QuickCountPhase.Finished ?: return
        tallySaved = true
        val elapsedSeconds = Duration.between(startedAt, Instant.now(clock)).seconds.toInt().coerceAtLeast(0)
        _durationSeconds.value = elapsedSeconds
        viewModelScope.launch {
            tallyRepository.recordTally(
                trackedProfileId = trackedProfileId,
                exercise = exercise,
                repsAchieved = finished.repCount,
                target = target,
                timestamp = Instant.now(clock),
                formScore = finished.formScore,
                durationSeconds = elapsedSeconds,
            )
        }
    }

    /**
     * Stops the camera/pose tracker — released by [QuickCountRunScreen]'s own
     * `DisposableEffect` (same pattern as `SessionScreens.kt`'s), not just
     * [onCleared]: this app has no back-stack-scoped `ViewModelStoreOwner`
     * (`MainActivity`'s screen switch is a plain `mutableStateOf<AppScreen>`),
     * so a [ViewModel] created via `viewModel()` is Activity-scoped and
     * [onCleared] alone would leave the camera — and camera-session-robustness
     * ticket 02's Foreground Service — running until the whole Activity dies,
     * not when the Athlete actually navigates away from this run. Safe to
     * call more than once ([PoseTracker.stop]'s own doc comment promises
     * this), so [onCleared] calling it too is not a double-stop hazard.
     */
    fun release() {
        poseTracker.stop()
    }

    override fun onCleared() = release()
}
