package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.ExerciseProfile
import com.workoutpartner.core.repcounting.PoseLandmarkFrame

/**
 * Debounces MediaPipe's per-frame detection into the two-state signal
 * ticket 03 asks for: pause on lost tracking, auto-resume on re-detection,
 * without flapping to [PoseTrackingSignal.Lost] on a single dropped frame
 * (motion blur, one bad frame) or resuming on a single lucky one.
 *
 * A frame counts as untracked once it's missing any of the *current*
 * [ExerciseProfile]'s three joints ([ExerciseProfile.jointA],
 * [ExerciseProfile.vertex], [ExerciseProfile.jointC]) — not a fixed count of
 * the six generic [com.workoutpartner.core.repcounting.Landmark]s
 * (camera-framing-indicator ticket 01; see ADR-0011). This is deliberately
 * the same gate [com.workoutpartner.core.repcounting.RepCounter.process]
 * already applies via [com.workoutpartner.core.repcounting.Angle.between]
 * (all three joints or nothing) — so a frame this machine reports
 * [PoseTrackingSignal.Trackable] for is, joint-wise, exactly a frame the Rep
 * Counting Engine can actually turn into an angle, not a looser or stricter
 * guess at it. [PoseLandmarkFrame] arrives already side-resolved by
 * [PoseFrameMapper] (one entry per generic
 * [com.workoutpartner.core.repcounting.Landmark]), so no left/right picking
 * happens here — this is just presence-checking the three joints the
 * current profile names.
 *
 * The current profile can be swapped mid-instance via [updateProfile] — e.g.
 * a Session moving from one Set's Exercise into the next's — without
 * resetting [state] or either consecutive-frame counter; only which joints
 * count toward "tracked" changes, starting from the very next [accept] call.
 *
 * Not itself responsible for skipping partial Reps — a [PoseLandmarkFrame]
 * is only ever handed to the Rep Counting Engine while this machine reports
 * [PoseTrackingSignal.Trackable]; the gap while [PoseTrackingSignal.Lost] is
 * simply never fed to it (see [CameraPoseTracker]).
 *
 * One instance tracks one camera session — plain state, fed frame by frame
 * via [accept], the same shape as
 * `com.workoutpartner.core.repcounting.RepCounter`'s one-instance-per-Exercise
 * state machine.
 */
class TrackingStateMachine(
    initialProfile: ExerciseProfile,
    private val lostAfterConsecutiveUntrackedFrames: Int = DEFAULT_LOST_AFTER_FRAMES,
    private val resumeAfterConsecutiveTrackedFrames: Int = DEFAULT_RESUME_AFTER_FRAMES,
) {
    private var profile: ExerciseProfile = initialProfile
    private var state: State = State.Trackable
    private var consecutiveUntracked = 0
    private var consecutiveTracked = 0

    /**
     * Swaps which [ExerciseProfile]'s joints [accept] checks against, from
     * this point forward — e.g. a Routine's next Set starting a different
     * Exercise. Deliberately leaves [state] and both consecutive-frame
     * counters untouched: this is a change to *what* counts as tracked, not
     * a fresh camera session, so any Lost/Trackable debounce already in
     * progress keeps running exactly as it was.
     */
    fun updateProfile(profile: ExerciseProfile) {
        this.profile = profile
    }

    /** Feed the next analyzed camera frame's mapping. */
    fun accept(frame: PoseLandmarkFrame): PoseTrackingSignal {
        val requiredLandmarks = setOf(profile.jointA, profile.vertex, profile.jointC)
        if (frame.landmarks.keys.containsAll(requiredLandmarks)) {
            consecutiveTracked++
            consecutiveUntracked = 0
        } else {
            consecutiveUntracked++
            consecutiveTracked = 0
        }

        state = when {
            state == State.Trackable && consecutiveUntracked >= lostAfterConsecutiveUntrackedFrames -> State.Lost
            state == State.Lost && consecutiveTracked >= resumeAfterConsecutiveTrackedFrames -> State.Trackable
            else -> state
        }

        return if (state == State.Trackable) PoseTrackingSignal.Trackable(frame) else PoseTrackingSignal.Lost
    }

    private enum class State { Trackable, Lost }

    companion object {
        /** ~0.5s of dropped frames at a 30fps analyzer before surfacing the warning banner — placeholder, expected to be tuned against real device data like ticket 02's angle thresholds. */
        const val DEFAULT_LOST_AFTER_FRAMES = 15

        /** A couple of good frames before resuming, so a single flicker right after a real loss doesn't bounce the banner. */
        const val DEFAULT_RESUME_AFTER_FRAMES = 2
    }
}
