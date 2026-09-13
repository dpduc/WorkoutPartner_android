package com.workoutpartner.core.repcounting

import kotlin.math.sqrt

/** A point in the pose-landmark coordinate space this engine reasons in. */
data class Point3D(val x: Float, val y: Float, val z: Float = 0f) {
    operator fun minus(other: Point3D) = Point3D(x - other.x, y - other.y, z - other.z)

    infix fun dot(other: Point3D) = x * other.x + y * other.y + z * other.z

    fun magnitude() = sqrt(this dot this)
}

/**
 * One frame of tracked pose landmarks, as produced by the pose-tracking
 * engine's landmark-frame stream (ticket 03). A landmark absent from [landmarks]
 * means it wasn't detected in this frame (e.g. occluded, out of frame).
 */
data class PoseLandmarkFrame(val landmarks: Map<Landmark, Point3D>)
