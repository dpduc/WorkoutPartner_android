package com.workoutpartner.data

/**
 * A [RoutineEntity] with its ordered [RoutineStepEntity] sequence loaded —
 * the shape CONTEXT.md's Routine definition actually describes ("an ordered
 * sequence of Exercises"). Assembled by [RoutineDao] from two plain
 * `ORDER BY orderIndex` queries rather than a Room `@Relation` — simpler to
 * reason about than relying on a `@Relation` query's unspecified row order.
 */
data class RoutineWithSteps(
    val routine: RoutineEntity,
    val steps: List<RoutineStepEntity>,
)
