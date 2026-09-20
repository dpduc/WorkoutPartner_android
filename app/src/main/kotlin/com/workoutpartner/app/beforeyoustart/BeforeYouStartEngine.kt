package com.workoutpartner.app.beforeyoustart

import com.workoutpartner.app.progress.TrackedExercise
import com.workoutpartner.core.posetracking.RawPoseFrame

/**
 * Where the Position Check (`workout-partner-v3` ticket 12) currently stands
 * — what the screen's "whole body in frame"/"distance OK" indicators and its
 * "Start anyway" button render. [secondsElapsed] counts engine ticks spent in
 * the phase; [startAnywayAvailable] flips on once that reaches
 * [BeforeYouStartEngine.START_ANYWAY_AFTER_SECONDS].
 */
data class PositionCheckStatus(
    val bodyInFrame: Boolean,
    val distance: DistanceStatus,
    val secondsElapsed: Int,
    val startAnywayAvailable: Boolean,
)

/** A line the Position Check wants spoken aloud — the caller (not this pure engine) turns it into speech via [BeforeYouStartEngine.takeCue]. */
enum class PositionCue { BODY_DETECTED, STEP_BACK, MOVE_CLOSER }

/**
 * The Before You Start flow's phases (CONTEXT.md): Workout Overview -> Form
 * Guides -> Position Check -> Countdown -> Ready, at which point the caller
 * starts the Session (or Quick Count run). `workout-partner-v3` ticket 10
 * gives [FormGuides] its real payload — the unseen [TrackedExercise]s to
 * show, per [FormGuidePrefs] — since the phase is skipped entirely rather
 * than shown empty; ticket 12 does the same for [PositionCheck] with its live
 * [PositionCheckStatus]. Countdown is still a plain marker, for ticket 13.
 */
sealed interface BeforeYouStartPhase {
    data object Overview : BeforeYouStartPhase
    data class FormGuides(val guides: List<TrackedExercise>) : BeforeYouStartPhase
    data class PositionCheck(val status: PositionCheckStatus) : BeforeYouStartPhase
    data object Countdown : BeforeYouStartPhase
    data object Ready : BeforeYouStartPhase
}

/**
 * Drives the Before You Start flow (CONTEXT.md), the same pure-Kotlin
 * "engine behind the UI" pattern as [com.workoutpartner.app.session.SessionEngine] —
 * no Android, camera, or clock dependency, so it's testable with manual
 * ticks/calls.
 *
 * [unseenGuides] — the Routine's [TrackedExercise]s the Athlete hasn't seen
 * a Form Guide for yet (`workout-partner-v3` ticket 10) — is what
 * [advance] uses to decide whether [BeforeYouStartPhase.FormGuides] is
 * shown at all: an Athlete who's already seen every guide in this Routine
 * skips it entirely and lands straight on [BeforeYouStartPhase.PositionCheck].
 *
 * The Position Check (ticket 12) is fed [onPoseFrame]s and once-a-second
 * [onTick]s, and advances itself to Countdown after [REQUIRED_STABLE_SECONDS]
 * of every check passing while standing still — or [startAnyway] once
 * [START_ANYWAY_AFTER_SECONDS] have passed, for an imperfect setup. Spoken
 * guidance is emitted as [PositionCue]s for the caller to [takeCue] and
 * speak. Countdown still advances unconditionally (ticket 13).
 */
class BeforeYouStartEngine(private val unseenGuides: List<TrackedExercise>) {
    var phase: BeforeYouStartPhase = BeforeYouStartPhase.Overview
        private set

    private var lastEvaluation = FrameEvaluation(bodyInFrame = false, distance = DistanceStatus.UNKNOWN)
    /** The frame stillness is measured against: the previous window's last frame (the first frame ever, before that) — not the previous frame, so slow drift and sway (tiny per frame at ~30fps) still add up to movement. */
    private var windowAnchorFrame: RawPoseFrame? = null
    private var latestFrame: RawPoseFrame? = null
    private var movedSinceTick = false
    private var frameSinceTick = false
    private var checkFailedSinceTick = false
    private var bodyDetectedAnnounced = false
    private var stableSeconds = 0
    private var secondsElapsed = 0
    private var lastDistanceCue: PositionCue? = null
    private var secondsSinceDistanceCue = 0
    private val cues = ArrayDeque<PositionCue>()

    /** Moves from the current phase to the next one in the fixed sequence. No-op once [BeforeYouStartPhase.Ready]. */
    fun advance() {
        phase = when (phase) {
            BeforeYouStartPhase.Overview ->
                if (unseenGuides.isEmpty()) initialPositionCheck() else BeforeYouStartPhase.FormGuides(unseenGuides)
            is BeforeYouStartPhase.FormGuides -> initialPositionCheck()
            is BeforeYouStartPhase.PositionCheck -> BeforeYouStartPhase.Countdown
            BeforeYouStartPhase.Countdown -> BeforeYouStartPhase.Ready
            BeforeYouStartPhase.Ready -> BeforeYouStartPhase.Ready
        }
    }

    /** Evaluates one camera frame against the Position Check's checks; ignored outside [BeforeYouStartPhase.PositionCheck]. */
    fun onPoseFrame(frame: RawPoseFrame) {
        if (phase !is BeforeYouStartPhase.PositionCheck) return
        val evaluation = PositionCheckEvaluator.evaluate(frame)

        val anchor = windowAnchorFrame
        if (anchor == null) windowAnchorFrame = frame else if (!PositionCheckEvaluator.isStill(anchor, frame)) movedSinceTick = true
        latestFrame = frame
        frameSinceTick = true
        if (!evaluation.allChecksPass) checkFailedSinceTick = true

        // "Body detected" is spoken once per Position Check, not on every flicker of the in-frame count.
        if (evaluation.bodyInFrame && !bodyDetectedAnnounced) {
            bodyDetectedAnnounced = true
            cues.addLast(PositionCue.BODY_DETECTED)
        }
        val distanceCue = distanceCueFor(evaluation)
        if (distanceCue != null && distanceCue != lastDistanceCue) {
            cues.addLast(distanceCue)
            secondsSinceDistanceCue = 0
        }
        // A merely suppressed cue (too far, but body not fully in frame) keeps the previous one, so
        // flicker across that boundary doesn't re-announce it; only a passing distance clears it.
        lastDistanceCue = distanceCue ?: if (evaluation.distance == DistanceStatus.OK) null else lastDistanceCue
        lastEvaluation = evaluation

        publishPositionCheck()
    }

    /** One second passed; ignored outside [BeforeYouStartPhase.PositionCheck]. */
    fun onTick() {
        if (phase !is BeforeYouStartPhase.PositionCheck) return
        secondsElapsed++

        // A second is stable only if frames arrived (a quiet camera isn't stillness), every one of
        // them passed all checks, and none drifted from the window's first frame.
        stableSeconds = if (frameSinceTick && !checkFailedSinceTick && !movedSinceTick) stableSeconds + 1 else 0
        windowAnchorFrame = latestFrame
        movedSinceTick = false
        frameSinceTick = false
        checkFailedSinceTick = false

        secondsSinceDistanceCue++
        lastDistanceCue?.let {
            if (secondsSinceDistanceCue >= DISTANCE_CUE_REPEAT_SECONDS) {
                cues.addLast(it)
                secondsSinceDistanceCue = 0
            }
        }

        if (stableSeconds >= REQUIRED_STABLE_SECONDS) advance() else publishPositionCheck()
    }

    /** Proceeds to Countdown despite failing checks, once [START_ANYWAY_AFTER_SECONDS] have passed in the Position Check. Ignored before then, or outside that phase. */
    fun startAnyway() {
        if (phase is BeforeYouStartPhase.PositionCheck && secondsElapsed >= START_ANYWAY_AFTER_SECONDS) advance()
    }

    /** The oldest not-yet-spoken [PositionCue], removing it; null when there's nothing to say. */
    fun takeCue(): PositionCue? = cues.removeFirstOrNull()

    /**
     * Advances through every remaining phase in one call. A placeholder for
     * ticket 13 to replace with the Athlete actually watching the countdown
     * instead of skipping straight through.
     */
    fun skipToReady() {
        while (phase != BeforeYouStartPhase.Ready) advance()
    }

    private fun initialPositionCheck(): BeforeYouStartPhase.PositionCheck = BeforeYouStartPhase.PositionCheck(currentStatus())

    private fun publishPositionCheck() {
        phase = BeforeYouStartPhase.PositionCheck(currentStatus())
    }

    private fun currentStatus() = PositionCheckStatus(
        bodyInFrame = lastEvaluation.bodyInFrame,
        distance = lastEvaluation.distance,
        secondsElapsed = secondsElapsed,
        startAnywayAvailable = secondsElapsed >= START_ANYWAY_AFTER_SECONDS,
    )

    /** "Come a bit closer" only once the whole body is in frame: a too-short *visible* skeleton otherwise usually means part of the body is cut off or occluded, not that the Athlete is far away. */
    private fun distanceCueFor(evaluation: FrameEvaluation): PositionCue? = when {
        evaluation.distance == DistanceStatus.TOO_CLOSE -> PositionCue.STEP_BACK
        evaluation.distance == DistanceStatus.TOO_FAR && evaluation.bodyInFrame -> PositionCue.MOVE_CLOSER
        else -> null
    }

    companion object {
        /** Seconds of every check passing while standing still before the Position Check auto-advances (spec.md story 64). */
        const val REQUIRED_STABLE_SECONDS = 2

        /** Seconds in the Position Check before "Start anyway" appears. */
        const val START_ANYWAY_AFTER_SECONDS = 15

        /** An unresolved distance cue is repeated this often — placeholder, tune on device. */
        const val DISTANCE_CUE_REPEAT_SECONDS = 5
    }
}
