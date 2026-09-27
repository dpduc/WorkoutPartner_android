package com.workoutpartner.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.workoutpartner.app.framing.FramingColor

/**
 * The live camera-framing border shared by Position Check, Session Tracking
 * and Quick Count Run (camera-framing-indicator ticket 02) — drawn over the
 * camera preview, replacing Position Check's old static body-silhouette
 * outline. Its color continuously reflects [closeness] to the ideal camera
 * distance (green at 1.0, shading toward red at 0.0), animated via
 * [animateColorAsState] so it drifts rather than jumps frame to frame.
 *
 * Whenever [trackable] is false — the current Exercise's required joints
 * aren't visible, per [com.workoutpartner.core.posetracking.TrackingStateMachine]
 * (ticket 01) — the border switches to a fixed dashed/gray state that
 * overrides [closeness]'s color entirely: distance can't be meaningfully
 * judged for a joint that isn't tracked at all, and the shape change (not
 * just a color) keeps this "can't tell" state unmistakable to a colorblind
 * Athlete (spec.md stories 11-12, 16).
 *
 * Callers own smoothing [closeness] themselves (see
 * [com.workoutpartner.app.framing.FramingScoreSmoother]) — this composable
 * only animates the *transition* between already-smoothed values, and does
 * not smooth per-frame noise on its own.
 */
@Composable
fun FramingBorder(trackable: Boolean, closeness: Float, modifier: Modifier = Modifier) {
    val targetColor = if (trackable) FramingColor.forCloseness(closeness) else FramingColor.UNTRACKABLE
    val animatedColor by animateColorAsState(targetColor, label = "framing-border-color")
    val stroke = if (trackable) {
        Stroke(width = STROKE_WIDTH)
    } else {
        Stroke(width = STROKE_WIDTH, pathEffect = PathEffect.dashPathEffect(DASH_PATTERN))
    }

    Canvas(modifier = modifier) {
        // Inset by a full stroke width on each side: a rect drawn exactly at the
        // canvas's own boundary would straddle it with a centered Stroke, putting
        // half the stroke's width right at the literal screen edge — where this
        // device's own edge compositing (confirmed present via on-device testing,
        // independent of anything this app draws) swallows it, making the border
        // functionally invisible against real content. Insetting keeps the whole
        // stroke safely inside the canvas, regardless of any edge effect.
        drawRect(
            color = animatedColor,
            topLeft = Offset(STROKE_WIDTH, STROKE_WIDTH),
            size = Size(size.width - 2 * STROKE_WIDTH, size.height - 2 * STROKE_WIDTH),
            style = stroke,
        )
    }
}

private const val STROKE_WIDTH = 12f
private val DASH_PATTERN = floatArrayOf(40f, 24f)
