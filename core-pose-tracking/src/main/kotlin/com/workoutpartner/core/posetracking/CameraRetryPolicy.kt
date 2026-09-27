package com.workoutpartner.core.posetracking

/**
 * The "should we retry, and after how many failures should we give up"
 * decision behind [CameraPoseTracker]'s reconnect logic (camera-session-robustness
 * ticket 03), pulled out as plain Kotlin — no CameraX types — so it's
 * unit-testable without a real camera, the same way [TrackingStateMachine]
 * is. [CameraPoseTracker] is the only thing that has to know
 * `CameraState`/`StateError` exist; this class only tracks "another
 * recoverable loss happened, was that one too many."
 *
 * One instance per bound camera session, mirroring [TrackingStateMachine]'s
 * own one-instance-per-session shape. [reset] is called both when a fresh
 * [CameraPoseTracker.start] begins and when the camera reports itself `OPEN`
 * again after a retry succeeded — a *later*, unrelated loss should get its
 * own full retry budget rather than inherit an already-spent one.
 *
 * Bounded with backoff deliberately, per this ticket's own research: CameraX's
 * issue tracker has real examples of apps stuck retrying an open/error/reopen
 * cycle forever when this isn't bounded.
 */
class CameraRetryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val backoffMs: (attempt: Int) -> Long = ::defaultBackoffMs,
) {
    private var attempt = 0

    /**
     * Call once per recoverable `CameraState.StateError`. Returns the delay
     * (in milliseconds) to wait before retrying the bind, or `null` once
     * [maxAttempts] have been spent — the caller should report a permanent
     * failure instead of scheduling another retry.
     */
    fun onRecoverableError(): Long? {
        attempt++
        return if (attempt > maxAttempts) null else backoffMs(attempt)
    }

    /** Call once the camera is confirmed `OPEN` again, so the next loss gets a fresh budget. */
    fun reset() {
        attempt = 0
    }

    companion object {
        /** Three attempts before giving up and surfacing a permanent failure — enough to ride out a brief camera hand-off to another app without retrying forever. */
        const val DEFAULT_MAX_ATTEMPTS = 3

        /** 1s, 2s, 4s — doubles each attempt so a camera that's genuinely stuck isn't hammered with rebind attempts. */
        fun defaultBackoffMs(attempt: Int): Long = 1_000L * (1L shl (attempt - 1))
    }
}
