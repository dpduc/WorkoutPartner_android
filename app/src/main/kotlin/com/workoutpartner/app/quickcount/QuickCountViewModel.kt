package com.workoutpartner.app.quickcount

import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.TallyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * The impure glue for one Quick Count run (ticket 11): drives
 * [QuickCountEngine] with the real [PoseTracker] camera stream and saves
 * exactly one [com.workoutpartner.data.TallyEntity] once the run finishes
 * (target reached or manually [stop]ped) — never a Form Score, per spec.md
 * story 38. Not unit-tested, the same "impure shell around a tested pure
 * engine" split as `SessionViewModel`/`CameraPoseTracker`.
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

    private val _phase = MutableStateFlow(engine.phase)
    val phase: StateFlow<QuickCountPhase> = _phase.asStateFlow()

    private var tallySaved = false

    init {
        viewModelScope.launch {
            poseTracker.signals.collect { signal ->
                engine.onPoseSignal(signal)
                _phase.value = engine.phase
                saveTallyIfJustFinished()
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
        viewModelScope.launch {
            tallyRepository.recordTally(
                trackedProfileId = trackedProfileId,
                exercise = exercise,
                repsAchieved = finished.repCount,
                target = target,
                timestamp = Instant.now(clock),
            )
        }
    }

    override fun onCleared() {
        poseTracker.stop()
    }
}
