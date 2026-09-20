package com.workoutpartner.app.session

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.app.routines.DifficultyTier
import com.workoutpartner.app.routines.RoutineDifficulty
import com.workoutpartner.app.speech.PromptSpeaker
import com.workoutpartner.core.posetracking.PoseTracker
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.data.AccountEntity
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.RoutineWithSteps
import com.workoutpartner.data.SetRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The impure glue for the Session flow (ticket 09): drives [SessionEngine]
 * with a real 1-second ticker, the real [PoseTracker] camera stream, a beep
 * per counted Rep, and persists each completed Set through [SetRepository]
 * — all offline (spec.md user story 23): no network call sits on this path,
 * only local Room writes plus a sync-queue enqueue ticket 06's background
 * sync drains later.
 *
 * Not unit-tested — it's a thin wire-up of Android APIs ([ToneGenerator],
 * CameraX via [PoseTracker], a real coroutine ticker) that need a device to
 * exercise, the same "impure shell around a tested pure engine" split as
 * `CameraPoseTracker` (ticket 03). The logic worth testing — phase
 * transitions, rep counting, Good Set grading — lives in [SessionEngine]
 * and is covered there by `SessionEngineTest`.
 */
class SessionViewModel(
    private val routine: RoutineWithSteps,
    private val accountId: String?,
    private val setRepository: SetRepository,
    private val accountRepository: AccountRepository,
    private val poseTracker: PoseTracker,
    /** Speaks [SessionAnnouncer]'s lines (`workout-partner-v3` ticket 13); shut down in [onCleared], like [toneGenerator]. */
    private val speaker: PromptSpeaker,
    phrases: AnnouncerPhrases,
    /** Computed from the Account's (or Guest's) body stats by the caller — see [RoutineDifficulty]. Defaults to [DifficultyTier.STANDARD] (unscaled) when the caller has no profile data yet. */
    private val difficultyTier: DifficultyTier = DifficultyTier.STANDARD,
    /** The Athlete's Overview toggle choice (`workout-partner-v3` ticket 11) — applied to every Jumping Jack step in [routine], for this Session only. `null` leaves them as plain Jumping Jack. */
    private val jumpingJackVariant: ExerciseVariant? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val steps = routine.toRoutineSteps(difficultyTier, jumpingJackVariant)
    private val engine = SessionEngine(steps)
    private val announcer = SessionAnnouncer(steps, phrases)

    private val _phase = MutableStateFlow(engine.phase)
    val phase: StateFlow<SessionPhase> = _phase.asStateFlow()

    /** The Account's Streak/Weekly Target, refreshed after each Set — null for a Guest Session (no Account to read; ticket 05/06). Session summary hides the Weekly Target section when this stays null. */
    private val _account = MutableStateFlow<AccountEntity?>(null)
    val account: StateFlow<AccountEntity?> = _account.asStateFlow()

    private var sessionId: String? = null
    private var lastRepCountSeen = 0
    private var beepTrackedStepIndex = -1
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME_PERCENT)

    /** Set once [poseTracker]'s camera/model init fails — stops [publishPhase] from stomping the [SessionPhase.CameraUnavailable] phase back to whatever [SessionEngine] still thinks the phase is on the next tick. */
    private var cameraUnavailable = false

    init {
        viewModelScope.launch {
            sessionId = setRepository.startSession(accountId, routine.routine.id, Instant.now(clock)).id
        }
        viewModelScope.launch {
            poseTracker.signals.collect { signal ->
                engine.onPoseSignal(signal)
                publishPhase()
                beepIfNewRep()
            }
        }
        viewModelScope.launch {
            poseTracker.errors.collect { message ->
                cameraUnavailable = true
                _phase.value = SessionPhase.CameraUnavailable(message)
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                engine.onTick()
                publishPhase()
            }
        }
    }

    /** Binds the camera preview once the Live Tracking screen has a surface to draw into. Safe to call again on recomposition — [PoseTracker.start] rebinds. */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewSurfaceProvider: Preview.SurfaceProvider) {
        poseTracker.start(lifecycleOwner, previewSurfaceProvider)
    }

    fun finishSet() {
        engine.finishSet()
        publishPhase()
        val completed = (engine.phase as? SessionPhase.SetSummary)?.completedSet ?: return
        val session = sessionId ?: return
        viewModelScope.launch {
            setRepository.recordSet(
                sessionId = session,
                exercise = completed.exercise,
                targetReps = completed.targetReps,
                actualReps = completed.actualReps,
                formScore = completed.formScore,
                goodSet = completed.goodSet,
                timestamp = Instant.now(clock),
                today = LocalDate.now(clock),
                zone = clock.zone,
                exerciseVariant = completed.variant,
            )
            refreshAccount()
        }
    }

    fun acknowledgeSetSummary() {
        engine.acknowledgeSetSummary()
        publishPhase()
    }

    fun skipRest() {
        engine.skipRest()
        publishPhase()
    }

    private suspend fun refreshAccount() {
        val id = accountId ?: return
        _account.value = accountRepository.getAccount(id)
    }

    private fun publishPhase() {
        if (cameraUnavailable) return
        val previous = _phase.value
        val next = engine.phase
        _phase.value = next
        announcer.announce(previous, next).forEach(speaker::speak)
    }

    private fun beepIfNewRep() {
        val tracking = engine.phase as? SessionPhase.Tracking ?: return
        if (tracking.stepIndex != beepTrackedStepIndex) {
            // A fresh step's Tracking phase always starts its own rep count
            // back at 0 — reset what we compare against so the first Rep of
            // step 2 doesn't get compared against step 1's final count.
            beepTrackedStepIndex = tracking.stepIndex
            lastRepCountSeen = 0
        }
        if (tracking.repCount > lastRepCountSeen) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
        }
        lastRepCountSeen = tracking.repCount
    }

    override fun onCleared() {
        poseTracker.stop()
        toneGenerator.release()
        speaker.shutdown()
    }

    private companion object {
        const val TONE_VOLUME_PERCENT = 80
        const val BEEP_DURATION_MS = 150
    }
}
