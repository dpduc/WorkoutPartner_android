package com.workoutpartner.core.repcounting

import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds a frame where [ExerciseProfile.jointA]/[ExerciseProfile.vertex]/
 * [ExerciseProfile.jointC] read exactly [angleDegrees] apart, regardless of
 * which anatomical joints they are — the vertex sits at the origin, jointA
 * along the +x axis, and jointC placed [angleDegrees] around from it.
 */
fun frameAtAngle(profile: ExerciseProfile, angleDegrees: Float): PoseLandmarkFrame {
    val radians = Math.toRadians(angleDegrees.toDouble())
    return PoseLandmarkFrame(
        mapOf(
            profile.jointA to Point3D(1f, 0f, 0f),
            profile.vertex to Point3D(0f, 0f, 0f),
            profile.jointC to Point3D(cos(radians).toFloat(), sin(radians).toFloat(), 0f),
        ),
    )
}

/** Clearly on the not-yet-engaged side of the rep threshold. */
fun ExerciseProfile.restingAngle(): Float = direction.towardResting(repThresholdDegrees, 40f)

/** Short of the rep threshold — a partial movement that never engages. */
fun ExerciseProfile.shortOfRepThreshold(margin: Float = 10f): Float = direction.towardResting(repThresholdDegrees, margin)

/** Past the rep threshold but short of the form threshold — a Rep with bad form. */
fun ExerciseProfile.betweenRepAndFormThreshold(): Float = (repThresholdDegrees + formThresholdDegrees) / 2f

/** Past the form threshold — deep enough for a Rep to pass form. */
fun ExerciseProfile.beyondFormThreshold(margin: Float = 10f): Float = direction.towardEngaged(formThresholdDegrees, margin)
