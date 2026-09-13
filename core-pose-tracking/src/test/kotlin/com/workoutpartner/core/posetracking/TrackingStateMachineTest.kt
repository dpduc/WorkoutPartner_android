package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.Point3D
import com.workoutpartner.core.repcounting.PoseLandmarkFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStateMachineTest {

    private val trackedFrame = PoseLandmarkFrame(
        mapOf(
            Landmark.SHOULDER to Point3D(0f, 0f, 0f),
            Landmark.HIP to Point3D(0f, 0f, 0f),
            Landmark.KNEE to Point3D(0f, 0f, 0f),
        ),
    )
    private val emptyFrame = PoseLandmarkFrame(emptyMap())

    @Test
    fun `stays Trackable through occasional dropped frames`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 5)

        val signals = listOf(trackedFrame, emptyFrame, trackedFrame, emptyFrame).map(machine::accept)

        assertTrue(signals.all { it is PoseTrackingSignal.Trackable })
    }

    @Test
    fun `reports Lost only after enough consecutive untracked frames`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 3)

        val signals = listOf(emptyFrame, emptyFrame, emptyFrame).map(machine::accept)

        assertEquals(
            listOf(
                PoseTrackingSignal.Trackable(emptyFrame),
                PoseTrackingSignal.Trackable(emptyFrame),
                PoseTrackingSignal.Lost,
            ),
            signals,
        )
    }

    @Test
    fun `a mostly-occluded frame with too few landmarks counts as untracked, not just a fully empty one`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 1, minimumTrackedLandmarks = 3)
        val barelyVisibleFrame = PoseLandmarkFrame(mapOf(Landmark.KNEE to Point3D(0f, 0f, 0f)))

        val signal = machine.accept(barelyVisibleFrame)

        assertEquals(PoseTrackingSignal.Lost, signal)
    }

    @Test
    fun `auto-resumes once enough consecutive tracked frames arrive`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 1, resumeAfterConsecutiveTrackedFrames = 2)

        val lost = machine.accept(emptyFrame)
        val stillLost = machine.accept(trackedFrame)
        val resumed = machine.accept(trackedFrame)

        assertEquals(PoseTrackingSignal.Lost, lost)
        assertEquals(PoseTrackingSignal.Lost, stillLost)
        assertEquals(PoseTrackingSignal.Trackable(trackedFrame), resumed)
    }

    @Test
    fun `a single dropped frame right after resuming does not immediately re-trigger Lost`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 2, resumeAfterConsecutiveTrackedFrames = 1)

        machine.accept(emptyFrame)
        machine.accept(emptyFrame) // -> Lost
        machine.accept(trackedFrame) // -> resumed (Trackable)
        val afterOneDrop = machine.accept(emptyFrame)

        assertEquals(PoseTrackingSignal.Trackable(emptyFrame), afterOneDrop)
    }

    @Test
    fun `no Rep-bearing frame is ever produced while Lost`() {
        val machine = TrackingStateMachine(lostAfterConsecutiveUntrackedFrames = 1)

        val signal = machine.accept(emptyFrame)

        assertEquals(PoseTrackingSignal.Lost, signal)
        assertTrue(signal !is PoseTrackingSignal.Trackable)
    }
}
