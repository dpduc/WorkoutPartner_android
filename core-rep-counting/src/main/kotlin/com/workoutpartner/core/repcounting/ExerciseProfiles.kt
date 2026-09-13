package com.workoutpartner.core.repcounting

/**
 * The angle thresholds for each Exercise. None of these come from the spec —
 * it explicitly leaves them unspecified (ticket 02) — they're reasonable
 * defaults from standard form cues, expected to be tuned later against real
 * device data. Standing/resting joints read roughly 160-180 degrees; these
 * pick a rep-detection depth and a stricter good-form depth within that.
 */
object ExerciseProfiles {
    private val all: Map<Exercise, ExerciseProfile> = listOf(
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
            formThresholdDegrees = 150f, // roughly arms-fully-overhead depth
        ),
    ).associateBy { it.exercise }

    fun forExercise(exercise: Exercise): ExerciseProfile = all.getValue(exercise)
}
