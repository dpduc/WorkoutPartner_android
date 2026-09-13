package com.workoutpartner.app.session

import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.PoseLandmarkFrame
import com.workoutpartner.core.repcounting.Point3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class SessionEngineTest {

    // Squat's angle triplet (ExerciseProfiles.kt): hip-knee-ankle, engaged =
    // smaller angle. Resting ~170 degrees, past the 100-degree form
    // threshold at ~80 degrees.
    private val restingSquatFrame = squatFrameAtAngle(170f)
    private val deepSquatFrame = squatFrameAtAngle(80f)

    @Test
    fun `starts in a 5-second Countdown for the first step`() {
        val engine = SessionEngine(listOf(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30)))

        assertEquals(SessionPhase.Countdown(stepIndex = 0, secondsRemaining = 5), engine.phase)
    }

    @Test
    fun `counts the countdown down to Tracking`() {
        val engine = SessionEngine(listOf(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30)))

        repeat(4) { engine.onTick() }
        assertEquals(SessionPhase.Countdown(stepIndex = 0, secondsRemaining = 1), engine.phase)

        engine.onTick()
        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 0, trackable = true), engine.phase)
    }

    @Test
    fun `a full Rep cycle increments the rep counter while Tracking`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30))

        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(deepSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))

        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 1, trackable = true), engine.phase)
    }

    @Test
    fun `Lost then Trackable auto-resumes without losing the rep already in progress`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30))

        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(deepSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Lost)
        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 0, trackable = false), engine.phase)

        // Resumes on the same RepCounter — the rep completes normally once
        // tracking comes back, no restart needed.
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))

        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 1, trackable = true), engine.phase)
    }

    @Test
    fun `finishing a Set that met target and form is a Good Set`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 30))
        completeOneGoodRep(engine)

        engine.finishSet()

        val phase = engine.phase as SessionPhase.SetSummary
        assertEquals(1, phase.completedSet.actualReps)
        assertEquals(100, phase.completedSet.formScore)
        assertTrue(phase.completedSet.goodSet)
    }

    @Test
    fun `finishing a Set short of the rep target is not a Good Set, even with perfect form`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 5, restIntervalSeconds = 30))
        completeOneGoodRep(engine)

        engine.finishSet()

        val phase = engine.phase as SessionPhase.SetSummary
        assertEquals(1, phase.completedSet.actualReps)
        assertFalse(phase.completedSet.goodSet)
        assertTrue(phase.completedSet.formNote.contains("target", ignoreCase = true))
    }

    @Test
    fun `finishing a Set with no Reps counted at all gets a distinct form note, not a form score comment`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 5, restIntervalSeconds = 30))

        engine.finishSet()

        val phase = engine.phase as SessionPhase.SetSummary
        assertEquals(0, phase.completedSet.actualReps)
        assertTrue(phase.completedSet.formNote.contains("frame", ignoreCase = true))
    }

    @Test
    fun `finishing a Set that met target with poor form gets a form-focused note, not a generic one`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 30))
        // Reaches the rep threshold (130 degrees) but well short of the form
        // threshold (100 degrees) — a Rep that counts but fails form.
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(squatFrameAtAngle(120f)))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))

        engine.finishSet()

        val phase = engine.phase as SessionPhase.SetSummary
        assertEquals(1, phase.completedSet.actualReps)
        assertEquals(0, phase.completedSet.formScore)
        assertFalse(phase.completedSet.goodSet)
        assertTrue(phase.completedSet.formNote.contains("range of motion", ignoreCase = true))
    }

    @Test
    fun `acknowledging a Set summary moves to the Resting phase for the step's rest interval`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 20))
        completeOneGoodRep(engine)
        engine.finishSet()

        engine.acknowledgeSetSummary()

        assertEquals(SessionPhase.Resting(stepIndex = 0, secondsRemaining = 20), engine.phase)
    }

    @Test
    fun `a step with no rest interval skips straight to the next step's countdown`() {
        val engine = trackingEngine(
            RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 0),
            RoutineStep(Exercise.PUSH_UP, targetReps = 5, restIntervalSeconds = 30),
        )
        completeOneGoodRep(engine)
        engine.finishSet()

        engine.acknowledgeSetSummary()

        assertEquals(SessionPhase.Countdown(stepIndex = 1, secondsRemaining = 5), engine.phase)
    }

    @Test
    fun `skipping rest moves straight to the next step's countdown`() {
        val engine = trackingEngine(
            RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 30),
            RoutineStep(Exercise.PUSH_UP, targetReps = 5, restIntervalSeconds = 30),
        )
        completeOneGoodRep(engine)
        engine.finishSet()
        engine.acknowledgeSetSummary()

        engine.skipRest()

        assertEquals(SessionPhase.Countdown(stepIndex = 1, secondsRemaining = 5), engine.phase)
    }

    @Test
    fun `the rest timer counts down to the next step on its own if not skipped`() {
        val engine = trackingEngine(
            RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 2),
            RoutineStep(Exercise.PUSH_UP, targetReps = 5, restIntervalSeconds = 30),
        )
        completeOneGoodRep(engine)
        engine.finishSet()
        engine.acknowledgeSetSummary()

        engine.onTick()
        assertEquals(SessionPhase.Resting(stepIndex = 0, secondsRemaining = 1), engine.phase)
        engine.onTick()

        assertEquals(SessionPhase.Countdown(stepIndex = 1, secondsRemaining = 5), engine.phase)
    }

    @Test
    fun `finishing the last step's rest reaches SessionComplete with every Set's summary`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 1, restIntervalSeconds = 30))
        completeOneGoodRep(engine)
        engine.finishSet()
        engine.acknowledgeSetSummary()

        engine.skipRest()

        val phase = engine.phase as SessionPhase.SessionComplete
        assertEquals(1, phase.completedSets.size)
        assertEquals(Exercise.SQUAT, phase.completedSets.single().exercise)
    }

    private fun trackingEngine(vararg steps: RoutineStep): SessionEngine {
        val engine = SessionEngine(steps.toList())
        repeat(SessionEngine.COUNTDOWN_SECONDS) { engine.onTick() }
        return engine
    }

    private fun completeOneGoodRep(engine: SessionEngine) {
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(deepSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
    }

    /** Builds a frame where the Squat's hip-knee-ankle angle reads exactly [angleDegrees] — same construction trick core-rep-counting's own fixtures use. */
    private fun squatFrameAtAngle(angleDegrees: Float): PoseLandmarkFrame {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return PoseLandmarkFrame(
            mapOf(
                Landmark.HIP to Point3D(1f, 0f, 0f),
                Landmark.KNEE to Point3D(0f, 0f, 0f),
                Landmark.ANKLE to Point3D(cos(radians).toFloat(), sin(radians).toFloat(), 0f),
            ),
        )
    }
}
