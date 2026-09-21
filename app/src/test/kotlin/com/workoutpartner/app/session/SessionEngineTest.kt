package com.workoutpartner.app.session

import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseVariant
import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.PoseLandmarkFrame
import com.workoutpartner.core.repcounting.RepCounter
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
        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 0, trackable = true, targetReps = 10), engine.phase)
    }

    @Test
    fun `a full Rep cycle increments the rep counter while Tracking`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30))

        engine.holdFrame(restingSquatFrame)
        engine.holdFrame(deepSquatFrame)
        engine.holdFrame(restingSquatFrame)

        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 1, trackable = true, targetReps = 10), engine.phase)
    }

    @Test
    fun `Lost then Trackable auto-resumes without losing the rep already in progress`() {
        val engine = trackingEngine(RoutineStep(Exercise.SQUAT, targetReps = 10, restIntervalSeconds = 30))

        engine.holdFrame(restingSquatFrame)
        engine.holdFrame(deepSquatFrame)
        engine.onPoseSignal(PoseTrackingSignal.Lost)
        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 0, trackable = false, targetReps = 10), engine.phase)

        // Resumes on the same RepCounter — the rep completes normally once
        // tracking comes back, no restart needed.
        engine.holdFrame(restingSquatFrame)

        assertEquals(SessionPhase.Tracking(stepIndex = 0, repCount = 1, trackable = true, targetReps = 10), engine.phase)
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
        engine.holdFrame(restingSquatFrame)
        engine.holdFrame(squatFrameAtAngle(120f))
        engine.holdFrame(restingSquatFrame)

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

    @Test
    fun `a Step Jack step judges Good Set against Step Jack's own 110-degree form threshold, not Jumping Jack's 125-degree one`() {
        // 118 degrees clears Step Jack's form threshold (110) but falls
        // short of Jumping Jack's (125) — the exact same physical sweep
        // should grade differently depending on which the step is.
        val stepJackEngine = trackingEngine(
            RoutineStep(Exercise.JUMPING_JACK, targetReps = 1, restIntervalSeconds = 30, variant = ExerciseVariant.STEP_JACK),
        )
        completeOneJumpingJackFamilyRepAt(stepJackEngine, sweepAngle = 118f)
        stepJackEngine.finishSet()
        val stepJackSet = (stepJackEngine.phase as SessionPhase.SetSummary).completedSet
        assertEquals(ExerciseVariant.STEP_JACK, stepJackSet.variant)
        assertEquals(100, stepJackSet.formScore)
        assertTrue(stepJackSet.goodSet)

        val jumpingJackEngine = trackingEngine(RoutineStep(Exercise.JUMPING_JACK, targetReps = 1, restIntervalSeconds = 30))
        completeOneJumpingJackFamilyRepAt(jumpingJackEngine, sweepAngle = 118f)
        jumpingJackEngine.finishSet()
        val jumpingJackSet = (jumpingJackEngine.phase as SessionPhase.SetSummary).completedSet
        assertEquals(null, jumpingJackSet.variant)
        assertEquals(0, jumpingJackSet.formScore)
        assertFalse(jumpingJackSet.goodSet)
    }

    private fun trackingEngine(vararg steps: RoutineStep): SessionEngine {
        val engine = SessionEngine(steps.toList())
        repeat(SessionEngine.COUNTDOWN_SECONDS) { engine.onTick() }
        return engine
    }

    private fun completeOneGoodRep(engine: SessionEngine) {
        engine.holdFrame(restingSquatFrame)
        engine.holdFrame(deepSquatFrame)
        engine.holdFrame(restingSquatFrame)
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

    /** Jumping Jack's (and Step Jack's — same joints, ticket 08) elbow-shoulder-hip angle, reading exactly [angleDegrees]. */
    private fun jumpingJackFamilyFrameAtAngle(angleDegrees: Float): PoseLandmarkFrame {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return PoseLandmarkFrame(
            mapOf(
                Landmark.ELBOW to Point3D(1f, 0f, 0f),
                Landmark.SHOULDER to Point3D(0f, 0f, 0f),
                Landmark.HIP to Point3D(cos(radians).toFloat(), sin(radians).toFloat(), 0f),
            ),
        )
    }

    /** A rep from well below both Jumping Jack's (90) and Step Jack's (75) rep thresholds, up to [sweepAngle], and back. */
    private fun completeOneJumpingJackFamilyRepAt(engine: SessionEngine, sweepAngle: Float) {
        engine.holdFrame(jumpingJackFamilyFrameAtAngle(40f))
        engine.holdFrame(jumpingJackFamilyFrameAtAngle(sweepAngle))
        engine.holdFrame(jumpingJackFamilyFrameAtAngle(40f))
    }

    /** Each phase of a movement is held for a full smoothing window: the counter now needs a sustained change, not a one-frame blip. */
    private fun SessionEngine.holdFrame(frame: PoseLandmarkFrame) =
        repeat(RepCounter.SMOOTHING_WINDOW) { onPoseSignal(PoseTrackingSignal.Trackable(frame)) }
}
