package com.workoutpartner.core.repcounting

/**
 * A single Exercise's rep-detection state machine (Seam 1). Feed it the
 * pose-landmark-frame stream via [process]; it emits a [RepEvent] on the
 * frame where a Rep completes, `null` otherwise.
 *
 * One instance tracks one Exercise's motion cycle: Resting (waiting for the
 * tracked angle to cross [ExerciseProfile.repThresholdDegrees]) -> Engaged
 * (tracking how far it goes) -> back to Resting, at which point the Rep is
 * counted and graded against [ExerciseProfile.formThresholdDegrees].
 *
 * Frames with a missing joint (untracked landmark) are skipped rather than
 * treated as a phase change — deciding what to do about lost tracking
 * mid-Set (pause, warn, resume) is the pose-tracking/UI layer's job (tickets
 * 03/09), not this pure engine's.
 *
 * Real MediaPipe angles are noisy — on recorded clips a single joint's angle
 * jumps by 100 degrees or more between adjacent frames — so two things sit
 * between the raw angle and the state machine, both found by replaying real
 * clips against hand-counted ground truth (`ClipReplayTest`): the angle is
 * the median of the last [SMOOTHING_WINDOW] frames, so a one-frame spike
 * can't fake a phase change, and a Rep only ends once the angle has come
 * back [RELEASE_MARGIN_DEGREES] past the rep threshold, so jitter around the
 * threshold can't count one movement as several Reps. Before this, one clip
 * of 4 jumping jacks counted 6 and one of 7 push-ups counted 32.
 */
class RepCounter private constructor(private val profile: ExerciseProfile) {
    private var phase: Phase = Phase.Resting
    private val recentAngles = ArrayDeque<Float>()

    private fun smoothed(rawAngle: Float): Float {
        recentAngles.addLast(rawAngle)
        if (recentAngles.size > SMOOTHING_WINDOW) recentAngles.removeFirst()
        return recentAngles.sorted()[recentAngles.size / 2]
    }

    fun process(frame: PoseLandmarkFrame): RepEvent? {
        val rawAngle = Angle.between(frame, profile.jointA, profile.vertex, profile.jointC) ?: return null
        val angle = smoothed(rawAngle)
        val direction = profile.direction
        val releaseThreshold = direction.towardResting(profile.repThresholdDegrees, RELEASE_MARGIN_DEGREES)

        return when (val current = phase) {
            is Phase.Resting -> {
                if (direction.hasPassedTowardEngaged(angle, profile.repThresholdDegrees)) {
                    phase = Phase.Engaged(extremeAngle = angle)
                }
                null
            }

            is Phase.Engaged -> {
                val extreme = direction.furtherFromRest(current.extremeAngle, angle)
                if (direction.hasPassedTowardResting(angle, releaseThreshold)) {
                    phase = Phase.Resting
                    val passedForm = direction.hasPassedTowardEngaged(extreme, profile.formThresholdDegrees)
                    RepEvent(profile.exercise, passedFormThreshold = passedForm)
                } else {
                    phase = Phase.Engaged(extreme)
                    null
                }
            }
        }
    }

    private sealed interface Phase {
        data object Resting : Phase
        data class Engaged(val extremeAngle: Float) : Phase
    }

    companion object {
        /** Frames the tracked angle is median-smoothed over (~1/3 second at 15fps) — see the class doc. */
        const val SMOOTHING_WINDOW = 5

        /** How far back toward rest, past the rep threshold, the angle must come before a Rep counts as finished. */
        const val RELEASE_MARGIN_DEGREES = 20f

        /** [variant] resolves an Exercise Variant's own profile instead of [exercise]'s (`workout-partner-v3` ticket 08) — see [ExerciseProfiles.forExercise]. */
        fun forExercise(exercise: Exercise, variant: ExerciseVariant? = null): RepCounter =
            RepCounter(ExerciseProfiles.forExercise(exercise, variant))
    }
}
