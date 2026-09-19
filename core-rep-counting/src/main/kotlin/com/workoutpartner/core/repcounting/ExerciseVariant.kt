package com.workoutpartner.core.repcounting

/**
 * An Exercise Variant (CONTEXT.md): an alternative way of performing an
 * [Exercise] — same movement family and tracked joint angle, but its own
 * rep-counting/Form Score thresholds ([ExerciseProfiles]) — scored and
 * ranked separately from [parentExercise]. `workout-partner-v3` ticket 08's
 * only value, [STEP_JACK], is a low-impact Jumping Jack that steps out to
 * the side instead of jumping; this ticket has no UI entry point for
 * choosing it yet (that's ticket 11) — it's reachable only by passing it
 * explicitly to [ExerciseProfiles.forExercise]/[RepCounter.forExercise].
 */
enum class ExerciseVariant(val parentExercise: Exercise) {
    STEP_JACK(Exercise.JUMPING_JACK),
}
