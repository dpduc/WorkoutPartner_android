package com.workoutpartner.app.session

import com.workoutpartner.app.speech.Announcement
import com.workoutpartner.app.speech.SpeechPriority
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAnnouncerTest {

    private val steps = listOf(
        RoutineStep(Exercise.SQUAT, targetReps = 12, restIntervalSeconds = 30),
        RoutineStep(Exercise.PUSH_UP, targetReps = 10, restIntervalSeconds = 30),
        RoutineStep(Exercise.JUMPING_JACK, targetReps = 20, restIntervalSeconds = 0, variant = ExerciseVariant.STEP_JACK),
    )
    private val announcer = SessionAnnouncer(steps, EnglishPhrases)

    private fun countdown(step: Int, seconds: Int = 5) = SessionPhase.Countdown(step, seconds)
    private fun tracking(step: Int, trackable: Boolean = true) = SessionPhase.Tracking(step, repCount = 0, trackable = trackable, targetReps = steps[step].targetReps)
    private fun resting(step: Int, seconds: Int) = SessionPhase.Resting(step, seconds)

    @Test
    fun `announces the next Set's Exercise and target as its countdown begins`() {
        val lines = announcer.announce(resting(0, 1), countdown(1))

        assertEquals(listOf(Announcement("Push-up. 10 reps.", SpeechPriority.HIGH)), lines)
    }

    @Test
    fun `uses the Variant's name and the already difficulty-adjusted target`() {
        val adjusted = listOf(RoutineStep(Exercise.JUMPING_JACK, targetReps = 24, restIntervalSeconds = 0, variant = ExerciseVariant.STEP_JACK))

        val lines = SessionAnnouncer(adjusted, EnglishPhrases).announce(resting(0, 1), countdown(0))

        assertEquals(listOf(Announcement("Step Jack. 24 reps.", SpeechPriority.HIGH)), lines)
    }

    @Test
    fun `setStart is the same line the Before You Start countdown speaks for the first Set`() {
        assertEquals(Announcement("Squat. 12 reps.", SpeechPriority.HIGH), announcer.setStart(steps[0]))
    }

    @Test
    fun `stays quiet on ordinary countdown ticks`() {
        assertEquals(emptyList<Announcement>(), announcer.announce(countdown(0, 5), countdown(0, 4)))
    }

    @Test
    fun `announces the rest when it begins`() {
        val lines = announcer.announce(SessionPhase.SetSummary(0, completedSet()), resting(0, 30))

        assertEquals(listOf(Announcement("Rest 30 seconds.", SpeechPriority.NORMAL)), lines)
    }

    @Test
    fun `says get ready with the next Exercise 5 seconds before the rest ends`() {
        val lines = announcer.announce(resting(0, 6), resting(0, 5))

        assertEquals(listOf(Announcement("Get ready. Next: Push-up.", SpeechPriority.LOW)), lines)
    }

    @Test
    fun `names the Variant in the get ready line`() {
        val lines = announcer.announce(resting(1, 6), resting(1, 5))

        assertEquals(listOf(Announcement("Get ready. Next: Step Jack.", SpeechPriority.LOW)), lines)
    }

    @Test
    fun `no get ready line after the last step, since nothing is next`() {
        assertEquals(emptyList<Announcement>(), announcer.announce(resting(2, 6), resting(2, 5)))
    }

    @Test
    fun `no get ready line on other rest ticks`() {
        assertEquals(emptyList<Announcement>(), announcer.announce(resting(0, 20), resting(0, 19)))
        assertEquals(emptyList<Announcement>(), announcer.announce(resting(0, 5), resting(0, 4)))
    }

    @Test
    fun `tracking lost is Critical and announced once`() {
        val lost = announcer.announce(tracking(0, trackable = true), tracking(0, trackable = false))
        val stillLost = announcer.announce(tracking(0, trackable = false), tracking(0, trackable = false))

        assertEquals(listOf(Announcement("Tracking lost. Step back into frame.", SpeechPriority.CRITICAL)), lost)
        assertEquals(emptyList<Announcement>(), stillLost)
    }

    @Test
    fun `regaining tracking says nothing`() {
        assertEquals(emptyList<Announcement>(), announcer.announce(tracking(0, trackable = false), tracking(0, trackable = true)))
    }

    @Test
    fun `announces the Session complete once`() {
        val complete = SessionPhase.SessionComplete(emptyList())

        assertEquals(listOf(Announcement("Workout complete.", SpeechPriority.HIGH)), announcer.announce(resting(2, 1), complete))
        assertEquals(emptyList<Announcement>(), announcer.announce(complete, complete))
    }

    @Test
    fun `still announces the next Set's start when the previous step has no rest`() {
        val lines = announcer.announce(SessionPhase.SetSummary(1, completedSet()), countdown(2))

        assertEquals(listOf(Announcement("Step Jack. 20 reps.", SpeechPriority.HIGH)), lines)
    }

    @Test
    fun `announces the Session complete straight after the last Set when it has no rest`() {
        val lines = announcer.announce(SessionPhase.SetSummary(2, completedSet()), SessionPhase.SessionComplete(emptyList()))

        assertEquals(listOf(Announcement("Workout complete.", SpeechPriority.HIGH)), lines)
    }

    @Test
    fun `a camera failure is not announced`() {
        assertTrue(announcer.announce(tracking(0), SessionPhase.CameraUnavailable("no camera")).isEmpty())
    }

    private fun completedSet() = CompletedSet(Exercise.SQUAT, targetReps = 12, actualReps = 12, formScore = 90, formNote = "", goodSet = true)

    /** The English lines as `strings.xml` words them — the real resources are Android-side, so this mirrors them for the pure test. */
    private object EnglishPhrases : AnnouncerPhrases {
        override fun exerciseName(exercise: Exercise, variant: ExerciseVariant?) = when {
            variant == ExerciseVariant.STEP_JACK -> "Step Jack"
            exercise == Exercise.SQUAT -> "Squat"
            exercise == Exercise.PUSH_UP -> "Push-up"
            exercise == Exercise.JUMPING_JACK -> "Jumping Jack"
            else -> exercise.name
        }

        override fun setStart(exerciseName: String, reps: Int) = "$exerciseName. $reps ${if (reps == 1) "rep" else "reps"}."
        override fun rest(seconds: Int) = "Rest $seconds ${if (seconds == 1) "second" else "seconds"}."
        override fun getReady(exerciseName: String) = "Get ready. Next: $exerciseName."
        override fun trackingLost() = "Tracking lost. Step back into frame."
        override fun sessionComplete() = "Workout complete."
    }
}
