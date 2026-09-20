package com.workoutpartner.app.session

import com.workoutpartner.app.speech.Announcement
import com.workoutpartner.app.speech.SpeechPriority
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant

/**
 * The words behind [SessionAnnouncer]'s lines — an interface so the announcer
 * itself stays pure Kotlin (no `Context`); [ResourceAnnouncerPhrases] fills it
 * from string resources (`workout-partner-v3` ticket 13: every spoken line
 * translatable later with no code changes), and the tests use plain English.
 */
interface AnnouncerPhrases {
    fun exerciseName(exercise: Exercise, variant: ExerciseVariant?): String
    fun setStart(exerciseName: String, reps: Int): String
    fun rest(seconds: Int): String
    fun getReady(exerciseName: String): String
    fun trackingLost(): String
    fun sessionComplete(): String
}

/**
 * Maps a [SessionPhase] transition (previous -> next) to the spoken lines it
 * should trigger (`workout-partner-v3` ticket 13), pure like `ReminderPolicy`.
 * [steps] must be the same difficulty-adjusted, Variant-resolved steps
 * [SessionEngine] runs, so a line says "Step Jack. 24 reps." — not the
 * Routine's raw numbers.
 *
 * Priorities are placeholders: tracking lost is [SpeechPriority.CRITICAL]
 * (worth cutting in), Set start and Session complete [SpeechPriority.HIGH],
 * the rest line [SpeechPriority.NORMAL], and the get-ready nudge
 * [SpeechPriority.LOW] (fine to lose if something else is being said).
 *
 * The first Set's line isn't produced by a transition here: it's spoken at
 * the start of the Before You Start countdown via [setStart], well before
 * [SessionEngine]'s own first Countdown phase exists to transition into.
 */
class SessionAnnouncer(private val steps: List<RoutineStep>, private val phrases: AnnouncerPhrases) {

    fun setStart(step: RoutineStep): Announcement = Announcement(
        phrases.setStart(phrases.exerciseName(step.exercise, step.variant), step.targetReps),
        SpeechPriority.HIGH,
    )

    fun announce(previous: SessionPhase, next: SessionPhase): List<Announcement> = when {
        next is SessionPhase.Countdown && previous !is SessionPhase.Countdown -> listOf(setStart(steps[next.stepIndex]))

        next is SessionPhase.Resting && previous !is SessionPhase.Resting ->
            listOf(Announcement(phrases.rest(next.secondsRemaining), SpeechPriority.NORMAL))

        next is SessionPhase.Resting && previous is SessionPhase.Resting &&
            previous.secondsRemaining > GET_READY_SECONDS && next.secondsRemaining == GET_READY_SECONDS ->
            steps.getOrNull(next.stepIndex + 1)?.let { upcoming ->
                listOf(Announcement(phrases.getReady(phrases.exerciseName(upcoming.exercise, upcoming.variant)), SpeechPriority.LOW))
            } ?: emptyList()

        next is SessionPhase.Tracking && previous is SessionPhase.Tracking && previous.trackable && !next.trackable ->
            listOf(Announcement(phrases.trackingLost(), SpeechPriority.CRITICAL))

        next is SessionPhase.SessionComplete && previous !is SessionPhase.SessionComplete ->
            listOf(Announcement(phrases.sessionComplete(), SpeechPriority.HIGH))

        else -> emptyList()
    }

    companion object {
        /** Seconds left in a rest at which the "Get ready. Next: ..." line is spoken. */
        const val GET_READY_SECONDS = 5
    }
}
