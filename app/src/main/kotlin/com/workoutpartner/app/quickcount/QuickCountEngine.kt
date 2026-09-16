package com.workoutpartner.app.quickcount

import com.workoutpartner.core.posetracking.PoseTrackingSignal
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.repcounting.FormScore
import com.workoutpartner.core.repcounting.RepCounter
import com.workoutpartner.core.repcounting.RepEvent

sealed interface QuickCountPhase {
    data class Running(val repCount: Int, val trackable: Boolean) : QuickCountPhase

    /** [formScore] (`workout-partner-v2` ticket 03) is the average across every Rep counted this run — see [QuickCountEngine.formScore]. */
    data class Finished(val repCount: Int, val formScore: Int) : QuickCountPhase
    /** [QuickCountViewModel] pushes this in directly when [com.workoutpartner.core.posetracking.PoseTracker.errors] fires — not something [QuickCountEngine] itself can reach. */
    data class CameraUnavailable(val message: String) : QuickCountPhase
}

/**
 * Drives one Quick Count run (ticket 11, CONTEXT.md's Quick Count/Tally
 * definitions): reuses [RepCounter] exactly like [com.workoutpartner.app.session.SessionEngine]
 * does. Still no Form Score *gating* — every completed Rep counts toward
 * [QuickCountPhase.Running.repCount]/[QuickCountPhase.Finished.repCount]
 * regardless of form, per spec.md user story 38's "no gating, so it stays
 * fast and simple." What changed (`workout-partner-v2` ticket 03, reversing
 * the earlier "Quick Count Tallies never have a Form Score" decision): each
 * Rep's [RepEvent.passedFormThreshold] is now recorded and exposed via
 * [formScore] — the average, computed the same way [com.workoutpartner.core.repcounting.FormScore]
 * already does for a Session's Sets — so the caller can persist it once the
 * run finishes.
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
    private val repEvents = mutableListOf<RepEvent>()

    var phase: QuickCountPhase = QuickCountPhase.Running(repCount = 0, trackable = true)
        private set

    /** The average Form Score across every Rep counted so far — safe to read at any point, including mid-run; the caller reads it once [phase] reaches [QuickCountPhase.Finished]. */
    val formScore: Int get() = FormScore.compute(repEvents)

    fun onPoseSignal(signal: PoseTrackingSignal) {
        val current = phase as? QuickCountPhase.Running ?: return
        phase = when (signal) {
            is PoseTrackingSignal.Trackable -> {
                repCounter.process(signal.frame)?.let { repEvents.add(it) }
                if (target != null && repEvents.size >= target) {
                    QuickCountPhase.Finished(repEvents.size, formScore)
                } else {
                    current.copy(repCount = repEvents.size, trackable = true)
                }
            }
            PoseTrackingSignal.Lost -> current.copy(trackable = false)
        }
    }

    /** Manually ends the run at any time (spec.md story 37). No-op once already [QuickCountPhase.Finished]. */
    fun stop() {
        val current = phase as? QuickCountPhase.Running ?: return
        phase = QuickCountPhase.Finished(current.repCount, formScore)
    }
}
