package com.workoutpartner.app.framing

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Maps a (smoothed) [FramingScorer] closeness score to the framing border's
 * color: green at the ideal distance, shading continuously toward red the
 * further off in either direction (camera-framing-indicator ticket 02,
 * spec.md's "green... shading toward orange/red"). [UNTRACKABLE] is a
 * separate, fixed color for the border's dashed/gray override — not a point
 * on this gradient, since "can't see what's needed" isn't a distance
 * reading at all (see ADR-0011).
 */
object FramingColor {
    /** The border's color at [FramingDistance.closeness] == 1.0 (exactly the ideal distance) — matches Position Check's old binary pass color. */
    val IDEAL = Color(0xFF2E7D32)

    /** The border's color at [FramingDistance.closeness] == 0.0 (as far off the ideal distance as the score registers). */
    val FAR_OFF = Color(0xFFC62828)

    /** The border's fixed, non-color state while the current Exercise's required joints aren't visible — overrides [forCloseness] entirely, a shape (dashed) as well as a color change so it reads independently of red/green to a colorblind Athlete. */
    val UNTRACKABLE = Color(0xFF9E9E9E)

    /** The border's color for a given (smoothed) closeness score, linearly interpolated between [FAR_OFF] and [IDEAL]. */
    fun forCloseness(closeness: Float): Color = lerp(FAR_OFF, IDEAL, closeness.coerceIn(0f, 1f))
}
