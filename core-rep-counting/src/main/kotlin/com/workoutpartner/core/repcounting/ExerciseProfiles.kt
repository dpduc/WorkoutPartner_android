package com.workoutpartner.core.repcounting

/**
 * The angle thresholds for each Exercise. None of these come from the spec —
 * it explicitly leaves them unspecified (ticket 02) — they're reasonable
 * defaults from standard form cues, expected to be tuned later against real
 * device data. Standing/resting joints read roughly 160-180 degrees; these
 * pick a rep-detection depth and a stricter good-form depth within that.
 */
object ExerciseProfiles {
    private val all: Map<Pair<Exercise, ExerciseVariant?>, ExerciseProfile> = listOf(
        ExerciseProfile(
            exercise = Exercise.SQUAT,
            // Knee angle: hip-knee-ankle.
            jointA = Landmark.HIP,
            vertex = Landmark.KNEE,
            jointC = Landmark.ANKLE,
            direction = RepDirection.DECREASING,
            repThresholdDegrees = 130f,
            formThresholdDegrees = 100f, // roughly thighs-parallel depth
        ),
        ExerciseProfile(
            exercise = Exercise.PUSH_UP,
            // Elbow angle: shoulder-elbow-wrist.
            jointA = Landmark.SHOULDER,
            vertex = Landmark.ELBOW,
            jointC = Landmark.WRIST,
            direction = RepDirection.DECREASING,
            repThresholdDegrees = 130f,
            formThresholdDegrees = 90f, // roughly chest-near-floor depth
        ),
        ExerciseProfile(
            exercise = Exercise.SIT_UP,
            // Hip/torso angle: shoulder-hip-knee.
            jointA = Landmark.SHOULDER,
            vertex = Landmark.HIP,
            jointC = Landmark.KNEE,
            direction = RepDirection.DECREASING,
            repThresholdDegrees = 130f,
            formThresholdDegrees = 70f, // roughly torso-upright-over-knees depth
        ),
        ExerciseProfile(
            exercise = Exercise.LUNGE,
            // Front-knee angle: hip-knee-ankle — same joints AND the same
            // placeholder thresholds as Squat. A Lunge only loads one leg,
            // but with no real device data yet to justify a different depth,
            // guessing a different number here would be false precision.
            jointA = Landmark.HIP,
            vertex = Landmark.KNEE,
            jointC = Landmark.ANKLE,
            direction = RepDirection.DECREASING,
            repThresholdDegrees = 130f,
            formThresholdDegrees = 100f, // roughly front-thigh-parallel depth
        ),
        ExerciseProfile(
            exercise = Exercise.JUMPING_JACK,
            // Shoulder-abduction angle: elbow-shoulder-hip. Arms at rest hang
            // alongside the torso (small angle); arms overhead swing the
            // angle wide (large angle) — the only Exercise whose engaged
            // phase is a larger angle, not a smaller one.
            jointA = Landmark.ELBOW,
            vertex = Landmark.SHOULDER,
            jointC = Landmark.HIP,
            direction = RepDirection.INCREASING,
            repThresholdDegrees = 90f,
            // Was 150 ("arms fully overhead"), but MediaPipe's 3D elbow-shoulder-hip angle
            // rarely reads that high even on good jacks: four hand-verified good (if not perfect)
            // jacks on a recorded clip peaked at 130-137 degrees once smoothed (`ClipReplayTest`),
            // and every one failed form. 125 sits just under the lowest of them.
            formThresholdDegrees = 125f,
        ),
        ExerciseProfile(
            exercise = Exercise.JUMPING_JACK,
            variant = ExerciseVariant.STEP_JACK,
            // Same elbow-shoulder-hip angle and INCREASING direction as
            // Jumping Jack (ticket 08) — Step Jack steps out to the side
            // instead of jumping, so it never needs the arms as wide: both
            // thresholds are shallower than Jumping Jack's.
            jointA = Landmark.ELBOW,
            vertex = Landmark.SHOULDER,
            jointC = Landmark.HIP,
            direction = RepDirection.INCREASING,
            repThresholdDegrees = 75f,
            // Lowered from 135 in step with Jumping Jack's 150 -> 125 (both are placeholders): Step Jack
            // must keep the shallower bar, since it never needs the arms as wide.
            formThresholdDegrees = 110f,
        ),
    ).associateBy { it.exercise to it.variant }

    /**
     * [variant] `null` (the default) resolves [exercise]'s own profile; a
     * non-null value resolves that Exercise Variant's profile instead
     * (`workout-partner-v3` ticket 08) — the caller is expected to pass a
     * [variant] whose [ExerciseVariant.parentExercise] matches [exercise].
     */
    fun forExercise(exercise: Exercise, variant: ExerciseVariant? = null): ExerciseProfile {
        require(variant == null || variant.parentExercise == exercise) {
            "$variant is not a variant of $exercise (its parent is ${variant?.parentExercise})"
        }
        return all.getValue(exercise to variant)
    }
}
