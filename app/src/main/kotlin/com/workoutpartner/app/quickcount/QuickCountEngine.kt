package com.workoutpartner.app.quickcount

import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.RepCounter

sealed interface QuickCountPhase {
    data class Running(val repCount: Int, val trackable: Boolean) : QuickCountPhase
    data class Finished(val repCount: Int) : QuickCountPhase
}

/**
 * Drives one Quick Count run (ticket 11, CONTEXT.md's Quick Count/Tally
 * definitions): reuses [RepCounter] exactly like [com.workoutpartner.app.session.SessionEngine]
 * does, but skips Form Score gating entirely — a raw rep count only (per
 * spec.md user story 38: "no Form Score, so that it stays fast and
 * simple"), so it never reads [com.workoutpartner.core.repcounting.RepEvent.passedFormThreshold].
 *
 * Auto-stops once [target] is reached (story 36); [stop] ends the run
 * manually at any time regardless (story 37). Pure Kotlin, same "engine
 * behind the UI" split as `SessionEngine` — no persistence or camera
 * dependency, testable with fixture [PoseTrackingSignal] sequences.
 *
 * Single-person-in-frame (ADR-0003, this ticket's own scope line) is
 * enforced entirely upstream, in `core-pose-tracking`, not here — there is
 * no "which person is the Tracked Profile" branch in this engine to write,
 * because [PoseTrackingSignal] never carries more than one candidate by the
 * time it reaches this engine. Two layers make that true: `CameraPoseTracker`
 * explicitly configures MediaPipe's Pose Landmarker with `setNumPoses(1)`
 * (not just relying on whatever the library's own default happens to be),
 * and `PoseLandmarkerResultMapping.toRawLandmarks()` additionally only ever
 * reads the first detected pose. Ticket 11's review caught an earlier draft
 * of this comment overstating this as "configured single-pose only" when
 * the explicit `setNumPoses(1)` call didn't actually exist yet — it does
 * now.
 */
class QuickCountEngine(exercise: Exercise, private val target: Int?) {
    private val repCounter = RepCounter.forExercise(exercise)
    private var repCount = 0

    var phase: QuickCountPhase = QuickCountPhase.Running(repCount = 0, trackable = true)
        private set

    fun onPoseSignal(signal: PoseTrackingSignal) {
        val current = phase as? QuickCountPhase.Running ?: return
        phase = when (signal) {
            is PoseTrackingSignal.Trackable -> {
                if (repCounter.process(signal.frame) != null) repCount++
                if (target != null && repCount >= target) {
                    QuickCountPhase.Finished(repCount)
                } else {
                    current.copy(repCount = repCount, trackable = true)
                }
            }
            PoseTrackingSignal.Lost -> current.copy(trackable = false)
        }
    }

    /** Manually ends the run at any time (spec.md story 37). No-op once already [QuickCountPhase.Finished]. */
    fun stop() {
        val current = phase as? QuickCountPhase.Running ?: return
        phase = QuickCountPhase.Finished(current.repCount)
    }
}
