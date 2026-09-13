package com.workoutpartner.app.quickcount

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

class QuickCountEngineTest {

    private val restingSquatFrame = squatFrameAtAngle(170f)
    private val deepSquatFrame = squatFrameAtAngle(80f)

    @Test
    fun `starts Running with zero reps`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = null)

        assertEquals(QuickCountPhase.Running(repCount = 0, trackable = true), engine.phase)
    }

    @Test
    fun `counts a full Rep cycle without any Form Score gating`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = null)

        // Never even reaches the form threshold — still counts, since Quick
        // Count has no Form Score gate (spec.md story 38).
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(squatFrameAtAngle(120f)))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))

        assertEquals(QuickCountPhase.Running(repCount = 1, trackable = true), engine.phase)
    }

    @Test
    fun `auto-stops once the optional target is reached`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = 2)

        completeOneRep(engine)
        assertTrue(engine.phase is QuickCountPhase.Running)
        completeOneRep(engine)

        assertEquals(QuickCountPhase.Finished(repCount = 2), engine.phase)
    }

    @Test
    fun `keeps running past any count when there is no target`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = null)

        repeat(5) { completeOneRep(engine) }

        assertEquals(QuickCountPhase.Running(repCount = 5, trackable = true), engine.phase)
    }

    @Test
    fun `manual stop ends the run at any time, even short of the target`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = 10)
        completeOneRep(engine)

        engine.stop()

        assertEquals(QuickCountPhase.Finished(repCount = 1), engine.phase)
    }

    @Test
    fun `Lost then Trackable auto-resumes without losing the rep already in progress`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = null)

        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(deepSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Lost)
        assertEquals(QuickCountPhase.Running(repCount = 0, trackable = false), engine.phase)

        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))

        assertEquals(QuickCountPhase.Running(repCount = 1, trackable = true), engine.phase)
    }

    @Test
    fun `signals after Finished are no-ops`() {
        val engine = QuickCountEngine(Exercise.SQUAT, target = 1)
        completeOneRep(engine)
        assertEquals(QuickCountPhase.Finished(repCount = 1), engine.phase)

        completeOneRep(engine)
        engine.stop()

        assertEquals(QuickCountPhase.Finished(repCount = 1), engine.phase)
    }

    private fun completeOneRep(engine: QuickCountEngine) {
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(deepSquatFrame))
        engine.onPoseSignal(PoseTrackingSignal.Trackable(restingSquatFrame))
    }

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
