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
 */
class RepCounter private constructor(private val profile: ExerciseProfile) {
    private var phase: Phase = Phase.Resting

    fun process(frame: PoseLandmarkFrame): RepEvent? {
        val angle = Angle.between(frame, profile.jointA, profile.vertex, profile.jointC) ?: return null
        val direction = profile.direction

        return when (val current = phase) {
            is Phase.Resting -> {
                if (direction.hasPassedTowardEngaged(angle, profile.repThresholdDegrees)) {
                    phase = Phase.Engaged(extremeAngle = angle)
                }
                null
            }

            is Phase.Engaged -> {
                val extreme = direction.furtherFromRest(current.extremeAngle, angle)
                if (direction.hasPassedTowardResting(angle, profile.repThresholdDegrees)) {
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
        fun forExercise(exercise: Exercise): RepCounter = RepCounter(ExerciseProfiles.forExercise(exercise))
    }
}
