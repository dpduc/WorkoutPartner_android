package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.ExerciseProfiles
import com.workoutpartner.core.repcounting.Landmark
import com.workoutpartner.core.repcounting.Point3D
import com.workoutpartner.core.repcounting.PoseLandmarkFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStateMachineTest {

    // Sit-Up's profile (SHOULDER/HIP/KNEE, see ExerciseProfiles) exactly matches the
    // three landmarks these fixtures already used before ticket 01 made the machine
    // exercise-aware — kept as the default profile for the pre-existing debounce
    // cases below so their frames/assertions stay the same as before this change.
    private val sitUpProfile = ExerciseProfiles.forExercise(Exercise.SIT_UP)

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
        val machine = TrackingStateMachine(initialProfile = sitUpProfile, lostAfterConsecutiveUntrackedFrames = 5)

        val signals = listOf(trackedFrame, emptyFrame, trackedFrame, emptyFrame).map(machine::accept)

        assertTrue(signals.all { it is PoseTrackingSignal.Trackable })
    }

    @Test
    fun `reports Lost only after enough consecutive untracked frames`() {
        val machine = TrackingStateMachine(initialProfile = sitUpProfile, lostAfterConsecutiveUntrackedFrames = 3)

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
    fun `a mostly-occluded frame missing needed joints counts as untracked, not just a fully empty one`() {
        val machine = TrackingStateMachine(initialProfile = sitUpProfile, lostAfterConsecutiveUntrackedFrames = 1)
        // Sit-Up needs SHOULDER/HIP/KNEE — a lone KNEE is missing two of the three.
        val barelyVisibleFrame = PoseLandmarkFrame(mapOf(Landmark.KNEE to Point3D(0f, 0f, 0f)))

        val signal = machine.accept(barelyVisibleFrame)

        assertEquals(PoseTrackingSignal.Lost, signal)
    }

    @Test
    fun `auto-resumes once enough consecutive tracked frames arrive`() {
        val machine = TrackingStateMachine(
            initialProfile = sitUpProfile,
            lostAfterConsecutiveUntrackedFrames = 1,
            resumeAfterConsecutiveTrackedFrames = 2,
        )

        val lost = machine.accept(emptyFrame)
        val stillLost = machine.accept(trackedFrame)
        val resumed = machine.accept(trackedFrame)

        assertEquals(PoseTrackingSignal.Lost, lost)
        assertEquals(PoseTrackingSignal.Lost, stillLost)
        assertEquals(PoseTrackingSignal.Trackable(trackedFrame), resumed)
    }

    @Test
    fun `a single dropped frame right after resuming does not immediately re-trigger Lost`() {
        val machine = TrackingStateMachine(
            initialProfile = sitUpProfile,
            lostAfterConsecutiveUntrackedFrames = 2,
            resumeAfterConsecutiveTrackedFrames = 1,
        )

        machine.accept(emptyFrame)
        machine.accept(emptyFrame) // -> Lost
        machine.accept(trackedFrame) // -> resumed (Trackable)
        val afterOneDrop = machine.accept(emptyFrame)

        assertEquals(PoseTrackingSignal.Trackable(emptyFrame), afterOneDrop)
    }

    @Test
    fun `no Rep-bearing frame is ever produced while Lost`() {
        val machine = TrackingStateMachine(initialProfile = sitUpProfile, lostAfterConsecutiveUntrackedFrames = 1)

        val signal = machine.accept(emptyFrame)

        assertEquals(PoseTrackingSignal.Lost, signal)
        assertTrue(signal !is PoseTrackingSignal.Trackable)
    }

    // --- camera-framing-indicator ticket 01: exercise-aware joint checks ---

    @Test
    fun `a frame missing only joints irrelevant to the current profile still reads Trackable`() {
        // Push-Up only needs SHOULDER/ELBOW/WRIST (see ExerciseProfiles) — legs never come into it.
        val pushUpProfile = ExerciseProfiles.forExercise(Exercise.PUSH_UP)
        val machine = TrackingStateMachine(initialProfile = pushUpProfile, lostAfterConsecutiveUntrackedFrames = 2)
        val legsOutOfFrame = PoseLandmarkFrame(
            mapOf(
                Landmark.SHOULDER to Point3D(0f, 0f, 0f),
                Landmark.ELBOW to Point3D(0f, 0f, 0f),
                Landmark.WRIST to Point3D(0f, 0f, 0f),
            ),
        )

        // Well past lostAfterConsecutiveUntrackedFrames worth of frames — HIP/KNEE/ANKLE
        // never show up at all, and it still never goes Lost.
        val signals = List(5) { machine.accept(legsOutOfFrame) }

        assertTrue(signals.all { it is PoseTrackingSignal.Trackable })
    }

    @Test
    fun `a frame missing a joint the current profile does need eventually reads Lost`() {
        // Push-Up needs SHOULDER/ELBOW/WRIST — this frame has everything else (a
        // whole lower body) but never the wrist.
        val pushUpProfile = ExerciseProfiles.forExercise(Exercise.PUSH_UP)
        val machine = TrackingStateMachine(initialProfile = pushUpProfile, lostAfterConsecutiveUntrackedFrames = 2)
        val wristNeverTracked = PoseLandmarkFrame(
            mapOf(
                Landmark.SHOULDER to Point3D(0f, 0f, 0f),
                Landmark.ELBOW to Point3D(0f, 0f, 0f),
                Landmark.HIP to Point3D(0f, 0f, 0f),
                Landmark.KNEE to Point3D(0f, 0f, 0f),
                Landmark.ANKLE to Point3D(0f, 0f, 0f),
            ),
        )

        val signals = listOf(machine.accept(wristNeverTracked), machine.accept(wristNeverTracked))

        assertEquals(PoseTrackingSignal.Trackable(wristNeverTracked), signals[0])
        assertEquals(PoseTrackingSignal.Lost, signals[1])
    }

    @Test
    fun `updating the profile mid-instance changes which joints are checked, without resetting existing debounce state`() {
        val squatProfile = ExerciseProfiles.forExercise(Exercise.SQUAT) // HIP/KNEE/ANKLE
        val pushUpProfile = ExerciseProfiles.forExercise(Exercise.PUSH_UP) // SHOULDER/ELBOW/WRIST
        val machine = TrackingStateMachine(
            initialProfile = squatProfile,
            lostAfterConsecutiveUntrackedFrames = 1,
            resumeAfterConsecutiveTrackedFrames = 2,
        )
        val upperBodyOnlyFrame = PoseLandmarkFrame(
            mapOf(
                Landmark.SHOULDER to Point3D(0f, 0f, 0f),
                Landmark.ELBOW to Point3D(0f, 0f, 0f),
                Landmark.WRIST to Point3D(0f, 0f, 0f),
            ),
        )

        // Under Squat's requirement (HIP/KNEE/ANKLE), an upper-body-only frame is
        // untracked -> Lost after just one frame.
        val beforeUpdate = machine.accept(upperBodyOnlyFrame)
        assertEquals(PoseTrackingSignal.Lost, beforeUpdate)

        machine.updateProfile(pushUpProfile)

        // Swapping the profile alone doesn't resume Trackable by itself — state and
        // the resume counter both carry over unchanged, so the very same frame shape
        // (now satisfying Push-Up's requirement) still has to clear
        // resumeAfterConsecutiveTrackedFrames like any other resume.
        val stillLost = machine.accept(upperBodyOnlyFrame)
        val resumed = machine.accept(upperBodyOnlyFrame)

        assertEquals(PoseTrackingSignal.Lost, stillLost)
        assertEquals(PoseTrackingSignal.Trackable(upperBodyOnlyFrame), resumed)
    }
}
