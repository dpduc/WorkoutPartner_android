package com.workoutpartner.core.repcounting

/**
 * The joint-angle configuration [RepCounter] uses to count Reps and grade
 * form for one [Exercise].
 *
 * @property jointA one end of the angle, together with [jointC]
 * @property vertex the joint the angle is measured at (see [Angle.between])
 * @property jointC the other end of the angle
 * @property repThresholdDegrees crossing this (in [direction]) marks the
 *   start of a Rep's engaged phase; crossing back marks it complete. This is
 *   deliberately looser than [formThresholdDegrees] — reaching it is enough
 *   to count as a Rep at all, not to count as good form.
 * @property formThresholdDegrees how far into the engaged phase (in
 *   [direction]) a Rep's extreme angle must reach to pass the Exercise's
 *   range-of-motion check (CONTEXT.md's Form Score).
 */
data class ExerciseProfile(
    val exercise: Exercise,
    val jointA: Landmark,
    val vertex: Landmark,
    val jointC: Landmark,
    val direction: RepDirection,
    val repThresholdDegrees: Float,
    val formThresholdDegrees: Float,
)
