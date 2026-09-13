package com.workoutpartner.core.posetracking

import com.workoutpartner.core.repcounting.PoseLandmarkFrame

/**
 * Debounces MediaPipe's per-frame detection into the two-state signal
 * ticket 03 asks for: pause on lost tracking, auto-resume on re-detection,
 * without flapping to [PoseTrackingSignal.Lost] on a single dropped frame
 * (motion blur, one bad frame) or resuming on a single lucky one.
 *
 * A frame counts as untracked once fewer than [minimumTrackedLandmarks] of
 * the six generic joints came back from [PoseFrameMapper] — not only when
 * the map is fully empty. That covers the ticket's "steps out of frame
 * **or is occluded**" both ways: a fully missing subject and a mostly (but
 * not completely) occluded one both degrade to too few usable joints.
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
    private val lostAfterConsecutiveUntrackedFrames: Int = DEFAULT_LOST_AFTER_FRAMES,
    private val resumeAfterConsecutiveTrackedFrames: Int = DEFAULT_RESUME_AFTER_FRAMES,
    private val minimumTrackedLandmarks: Int = DEFAULT_MINIMUM_TRACKED_LANDMARKS,
) {
    private var state: State = State.Trackable
    private var consecutiveUntracked = 0
    private var consecutiveTracked = 0

    /** Feed the next analyzed camera frame's mapping. */
    fun accept(frame: PoseLandmarkFrame): PoseTrackingSignal {
        if (frame.landmarks.size >= minimumTrackedLandmarks) {
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

        /** At least half of the six generic joints ([com.workoutpartner.core.repcounting.Landmark] has six) must be tracked for a frame to count as usable. */
        const val DEFAULT_MINIMUM_TRACKED_LANDMARKS = 3
    }
}
