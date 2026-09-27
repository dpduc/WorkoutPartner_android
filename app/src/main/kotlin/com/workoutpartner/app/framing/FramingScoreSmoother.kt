package com.workoutpartner.app.framing

/**
 * Exponential moving average over [FramingScorer]'s raw per-frame
 * [FramingDistance.closeness] score, so the border's color drifts across a
 * few frames of momentary tracking noise instead of jumping frame to frame
 * (camera-framing-indicator ticket 02, spec.md story 7). Plain stateful
 * class, not tied to Compose — one instance per camera session, fed frame by
 * frame via [next], the same "small hand-rolled state machine" shape as
 * [com.workoutpartner.core.posetracking.TrackingStateMachine].
 */
class FramingScoreSmoother(private val smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR) {
    private var smoothed: Float? = null

    /** Feeds the next raw closeness score and returns the smoothed value so far — the first call returns [rawScore] unchanged, since there's nothing yet to smooth against. */
    fun next(rawScore: Float): Float {
        val previous = smoothed
        val updated = if (previous == null) rawScore else previous + smoothingFactor * (rawScore - previous)
        smoothed = updated
        return updated
    }

    companion object {
        /** How much weight the newest frame's score gets on each step — placeholder, tune on device like every other threshold in this feature. */
        const val DEFAULT_SMOOTHING_FACTOR = 0.2f
    }
}
