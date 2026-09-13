package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A Session (CONTEXT.md): one instance of an Account working through a
 * Routine, start to finish, made up of one or more [SetEntity] rows.
 *
 * [accountId] is nullable — this is this schema's answer to ticket 05's
 * "Guest local record: same shape as an Account's local data, unowned until
 * migration assigns it an Account id." Per ADR-0004 ("Guest Sets... are
 * written to Room like an Account's would be," an "ownerless local
 * record"), a Guest's Session has no owning Account yet; there's no
 * separate Guest table, just this null column, re-pointed in place at a
 * real [AccountEntity.id] once migration (ticket 08) runs.
 *
 * [id] is a client-generated UUID (not an auto-generated row id) so a
 * Session can be created fully offline, before any server round-trip —
 * required by ADR-0002's offline-first model, and it doubles as the
 * Firestore document id once synced (ADR-0001).
 *
 * The FK to [AccountEntity] intentionally has no `onDelete` (default
 * `NO_ACTION`), unlike this schema's owned-child relationships (Set->Session,
 * RoutineStep->Routine, Tally->TrackedProfile, all `CASCADE`): no ticket
 * defines an "delete Account" feature in v1, so inventing cascade-vs-orphan
 * behavior for it here would be guessing at an unasked question rather than
 * documenting a real decision.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
        ),
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
        ),
    ],
    indices = [Index("accountId"), Index("routineId")],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val accountId: String?,
    val routineId: String,
    val timestamp: Instant,
)
