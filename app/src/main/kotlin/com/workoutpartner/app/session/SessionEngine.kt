package com.workoutpartner.app.session

import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.core.repcounting.FormScore
import com.workoutpartner.core.repcounting.RepCounter
import com.workoutpartner.core.repcounting.RepEvent

/**
 * One step of a Routine, in this engine's own minimal vocabulary rather than
 * `data`'s `RoutineStepEntity` directly — the same "no persistence
 * dependency in the pure engine" seam boundary core-rep-counting/
 * core-streaks use. [SessionViewModel] maps `RoutineStepEntity` onto this.
 *
 * [variant] (`workout-partner-v3` ticket 08) picks an [ExerciseVariant] of
 * [exercise] instead of its standard form — null by default, since no
 * Routine content or UI produces one yet (that's ticket 11's job; this
 * ticket only wires the engine so a caller that does pass one works end to
 * end).
 */
data class RoutineStep(
    val exercise: Exercise,
    val targetReps: Int,
    val restIntervalSeconds: Int,
    val variant: ExerciseVariant? = null,
)

/** A completed Set — reps vs. target, Form Score, a form note (spec.md story 20: "a form note, so that I know what to improve"), and Good Set (CONTEXT.md: both conditions required). [variant] mirrors the [RoutineStep] it completed. */
data class CompletedSet(
    val exercise: Exercise,
    val targetReps: Int,
    val actualReps: Int,
    val formScore: Int,
    val formNote: String,
    val goodSet: Boolean,
    val variant: ExerciseVariant? = null,
)

sealed interface SessionPhase {
    data class Countdown(val stepIndex: Int, val secondsRemaining: Int) : SessionPhase
    /** [targetReps] is the already difficulty-adjusted target — the same value [SessionEngine] itself is tracking a Good Set against, not the Routine's raw unscaled number. */
    data class Tracking(val stepIndex: Int, val repCount: Int, val trackable: Boolean, val targetReps: Int) : SessionPhase
    data class SetSummary(val stepIndex: Int, val completedSet: CompletedSet) : SessionPhase
    data class Resting(val stepIndex: Int, val secondsRemaining: Int) : SessionPhase
    data class SessionComplete(val completedSets: List<CompletedSet>) : SessionPhase
    /** [SessionViewModel] pushes this in directly when [com.workoutpartner.core.posetracking.PoseTracker.errors] fires — not something [SessionEngine] itself can reach. */
    data class CameraUnavailable(val message: String) : SessionPhase
}

/**
 * Drives the Session flow's state machine (ticket 09, spec.md's "Specific
 * interactions"): manual start -> 5s countdown -> live tracking (rep
 * counter fed by the Pose Tracking Engine's signal) -> Set complete ->
 * per-Set summary -> skippable rest timer -> next step's countdown -> ... ->
 * Session summary.
 *
 * Pure Kotlin — no Android framework, camera, or persistence dependency —
 * so it's testable with fixture [PoseTrackingSignal] sequences and manual
 * ticks, the same "engine behind the UI" pattern as core-rep-counting/
 * core-streaks. Ticks (the countdown/rest timers) and pose signals are fed
 * in by the caller ([SessionViewModel] in production) rather than this
 * class reading a clock or camera itself.
 *
 * No partial Rep is ever counted across a lost-tracking gap: frames only
 * reach [RepCounter] while [SessionPhase.Tracking], and the *same*
 * [RepCounter] instance keeps running across a [PoseTrackingSignal.Lost] ->
 * [PoseTrackingSignal.Trackable] transition — auto-resume without
 * restarting the Set, per ticket 03's design.
 *
 * A step's [RoutineStep.variant] (`workout-partner-v3` ticket 08) flows into
 * both the [RepCounter] built for it and the resulting [CompletedSet] —
 * [finishSet]'s Good Set/Form Score judgement is only ever the *aggregate*
 * of each Rep's [RepEvent.passedFormThreshold] against the uniform
 * [GOOD_SET_FORM_SCORE_THRESHOLD], but each Rep's own pass/fail already
 * came from the Variant's own angle thresholds if it has one — see
 * [com.workoutpartner.core.repcounting.ExerciseProfiles].
 */
class SessionEngine(private val steps: List<RoutineStep>) {
    init {
        require(steps.isNotEmpty()) { "a Session needs at least one Routine step" }
    }

    private var stepIndex = 0
    private var repCounter = RepCounter.forExercise(steps[0].exercise, steps[0].variant)
    private var repEvents = mutableListOf<RepEvent>()

    var phase: SessionPhase = SessionPhase.Countdown(stepIndex = 0, secondsRemaining = COUNTDOWN_SECONDS)
        private set

    /** One second of the pre-Set countdown or the post-Set rest timer has elapsed. No-op in any other phase. */
    fun onTick() {
        phase = when (val current = phase) {
            is SessionPhase.Countdown -> {
                val remaining = current.secondsRemaining - 1
                if (remaining <= 0) startTrackingCurrentStep() else current.copy(secondsRemaining = remaining)
            }
            is SessionPhase.Resting -> {
                val remaining = current.secondsRemaining - 1
                if (remaining <= 0) advanceToNextStepOrFinish() else current.copy(secondsRemaining = remaining)
            }
            else -> current
        }
    }

    /** A new signal from the Pose Tracking Engine — only meaningful while [SessionPhase.Tracking]; a no-op otherwise. */
    fun onPoseSignal(signal: PoseTrackingSignal) {
        val current = phase as? SessionPhase.Tracking ?: return
        phase = when (signal) {
            is PoseTrackingSignal.Trackable -> {
                repCounter.process(signal.frame)?.let { repEvents.add(it) }
                current.copy(repCount = repEvents.size, trackable = true)
            }
            PoseTrackingSignal.Lost -> current.copy(trackable = false)
        }
    }

    /** Manually ends the current Set (CONTEXT.md's Set definition: "ending at rest or a manual stop"). No-op unless [SessionPhase.Tracking]. */
    fun finishSet() {
        phase as? SessionPhase.Tracking ?: return
        val step = steps[stepIndex]
        val formScore = FormScore.compute(repEvents)
        val completed = CompletedSet(
            exercise = step.exercise,
            targetReps = step.targetReps,
            actualReps = repEvents.size,
            formScore = formScore,
            formNote = formNoteFor(formScore, repEvents.size, step.targetReps),
            goodSet = repEvents.size >= step.targetReps && formScore >= GOOD_SET_FORM_SCORE_THRESHOLD,
            variant = step.variant,
        )
        completedSets.add(completed)
        phase = SessionPhase.SetSummary(stepIndex, completed)
    }

    /** Acknowledges the per-Set summary and moves to the rest timer — or straight to the next step if this one has no rest interval. No-op unless [SessionPhase.SetSummary]. */
    fun acknowledgeSetSummary() {
        phase as? SessionPhase.SetSummary ?: return
        val restSeconds = steps[stepIndex].restIntervalSeconds
        phase = if (restSeconds > 0) SessionPhase.Resting(stepIndex, restSeconds) else advanceToNextStepOrFinish()
    }

    /** Skips the remainder of the rest timer immediately. No-op unless [SessionPhase.Resting]. */
    fun skipRest() {
        phase as? SessionPhase.Resting ?: return
        phase = advanceToNextStepOrFinish()
    }

    private val completedSets = mutableListOf<CompletedSet>()

    private fun startTrackingCurrentStep(): SessionPhase.Tracking {
        repCounter = RepCounter.forExercise(steps[stepIndex].exercise, steps[stepIndex].variant)
        repEvents = mutableListOf()
        return SessionPhase.Tracking(stepIndex, repCount = 0, trackable = true, targetReps = steps[stepIndex].targetReps)
    }

    private fun advanceToNextStepOrFinish(): SessionPhase {
        stepIndex++
        return if (stepIndex >= steps.size) {
            SessionPhase.SessionComplete(completedSets.toList())
        } else {
            SessionPhase.Countdown(stepIndex, COUNTDOWN_SECONDS)
        }
    }

    /** A short, actionable note per spec.md story 20 ("a form note, so that I know what to improve") — not just the raw Form Score number. */
    private fun formNoteFor(formScore: Int, actualReps: Int, targetReps: Int): String = when {
        actualReps == 0 -> "No Reps counted — make sure you're fully in frame."
        actualReps < targetReps -> "Short of your target — go for the full count next time."
        formScore >= GOOD_SET_FORM_SCORE_THRESHOLD -> "Great form — keep it up."
        formScore >= PARTIAL_FORM_SCORE_THRESHOLD -> "Decent form — try going a little deeper next time."
        else -> "Focus on full range of motion next time."
    }

    companion object {
        /** Per spec.md's "Specific interactions": "5s countdown after tapping start." */
        const val COUNTDOWN_SECONDS = 5

        /**
         * Not specified numerically by CONTEXT.md's Good Set definition
         * ("Form Score is at or above the Exercise's threshold") beyond
         * naming that a threshold exists. "The Exercise's" wording could be
         * read as calling for a threshold that varies per Exercise, the way
         * [com.workoutpartner.core.repcounting.ExerciseProfile.formThresholdDegrees]
         * already does for grading individual Reps — but with no per-Exercise
         * data yet to justify different numbers, one uniform value across all
         * five is picked here instead, the same "defensible default,
         * documented as a placeholder, not a settled per-Exercise config" spirit
         * as ticket 02's angle thresholds and ticket 04's Shield cap. Flagging
         * this reading rather than silently picking one — a later ticket with
         * real device data may need to split this into a per-Exercise map.
         */
        const val GOOD_SET_FORM_SCORE_THRESHOLD = 80

        /** Below [GOOD_SET_FORM_SCORE_THRESHOLD] but still "decent" rather than "poor" — same placeholder spirit. */
        const val PARTIAL_FORM_SCORE_THRESHOLD = 40
    }
}
