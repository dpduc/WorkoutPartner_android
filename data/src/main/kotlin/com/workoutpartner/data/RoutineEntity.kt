package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Routine (CONTEXT.md): a fixed, bundled sequence of target Exercises,
 * rep targets, and rest intervals — authored into the app, not
 * user-editable in v1. See [RoutineStepEntity] for the sequence itself.
 *
 * [id] is a stable slug (e.g. "full_body_beginner") rather than a UUID:
 * these rows are seeded app content, not user-generated data, and — unlike
 * [SessionEntity]/[SetEntity]/[TrackedProfileEntity]/[TallyEntity] — have no
 * per-Account copy to sync through Firestore (ADR-0001's "mirrors the
 * above" concerns Account-owned data; bundled content ships with the app).
 *
 * [format] (`workout-partner-v2` ticket 02) is a display tag only — see
 * [RoutineFormat]'s doc comment for why this doesn't change how a Session
 * actually runs.
 */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val format: RoutineFormat = RoutineFormat.STANDARD,
)
