package com.workoutpartner.core.repcounting

import kotlin.math.acos

/** Computes joint angles from a [PoseLandmarkFrame]. */
object Angle {
    /**
     * The angle at [vertex], in degrees, between the rays to [a] and [c] —
     * e.g. the knee angle formed by the hip, knee, and ankle. Returns `null`
     * if any of the three landmarks wasn't detected in this frame, or if [a]
     * or [c] coincides with [vertex] (a degenerate, zero-length ray).
     */
    fun between(frame: PoseLandmarkFrame, a: Landmark, vertex: Landmark, c: Landmark): Float? {
        val pointA = frame.landmarks[a] ?: return null
        val pointVertex = frame.landmarks[vertex] ?: return null
        val pointC = frame.landmarks[c] ?: return null

        val toA = pointA - pointVertex
        val toC = pointC - pointVertex
        val magnitudeA = toA.magnitude()
        val magnitudeC = toC.magnitude()
        if (magnitudeA == 0f || magnitudeC == 0f) return null

        val cosine = ((toA dot toC) / (magnitudeA * magnitudeC)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }
}
