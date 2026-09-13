package com.workoutpartner.core.repcounting

/**
 * The joints this engine's angle calculations read. Deliberately generic
 * (no left/right side, no MediaPipe landmark indices) — this module has no
 * camera or MediaPipe dependency at all (Seam 1). Mapping the pose-tracking
 * engine's actual per-side landmarks onto these is core-pose-tracking's job
 * (ticket 03), e.g. picking whichever side/leg is more visible, or the front
 * leg for a Lunge.
 */
enum class Landmark {
    SHOULDER,
    ELBOW,
    WRIST,
    HIP,
    KNEE,
    ANKLE,
}
