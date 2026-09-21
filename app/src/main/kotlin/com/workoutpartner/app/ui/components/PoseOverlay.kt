package com.workoutpartner.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.workoutpartner.core.posetracking.PoseFrameMapper
import com.workoutpartner.core.posetracking.RawPoseFrame

/**
 * A debug view of what the pose tracker sees: MediaPipe's 33 landmarks and
 * skeleton drawn over the screen — green where the landmark is confident
 * enough to be used, red where it isn't. Debug builds only (callers gate it
 * on `isDebuggableBuild()`); it exists to make jitter, flipping sides and
 * dropped joints visible at a glance instead of inferable only from logs.
 *
 * Landmark coordinates are normalized to the analyzed image, so this maps
 * them across the whole view. That lines up exactly with the video source
 * (which draws nothing behind it) and approximately with a live camera
 * preview, whose center-crop scaling isn't accounted for here. [mirrored]
 * flips x for the front camera's selfie-style preview, since the analyzed
 * image is un-mirrored.
 */
@Composable
fun PoseOverlay(frame: RawPoseFrame?, mirrored: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val landmarks = frame?.landmarks ?: return@Canvas
        fun point(i: Int) = landmarks.getOrNull(i)?.let { Offset((if (mirrored) 1f - it.x else it.x) * size.width, it.y * size.height) }
        fun confident(i: Int) = (landmarks.getOrNull(i)?.confidence ?: 0f) >= PoseFrameMapper.DEFAULT_VISIBILITY_THRESHOLD

        CONNECTIONS.forEach { (a, b) ->
            val pa = point(a)
            val pb = point(b)
            if (pa != null && pb != null) {
                drawLine(if (confident(a) && confident(b)) Color(0xCCFFFFFF) else Color(0x66FF5252), pa, pb, strokeWidth = 4f)
            }
        }
        landmarks.indices.forEach { i ->
            point(i)?.let { drawCircle(if (confident(i)) Color(0xFF4CAF50) else Color(0xFFFF5252), radius = 7f, center = it) }
        }
    }
}

/** MediaPipe Pose's standard landmark connections (by landmark index). */
private val CONNECTIONS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 7, 0 to 4, 4 to 5, 5 to 6, 6 to 8, 9 to 10,
    11 to 12, 11 to 13, 13 to 15, 15 to 17, 15 to 19, 15 to 21, 17 to 19,
    12 to 14, 14 to 16, 16 to 18, 16 to 20, 16 to 22, 18 to 20,
    11 to 23, 12 to 24, 23 to 24, 23 to 25, 24 to 26, 25 to 27, 26 to 28,
    27 to 29, 28 to 30, 29 to 31, 30 to 32, 27 to 31, 28 to 32,
)
