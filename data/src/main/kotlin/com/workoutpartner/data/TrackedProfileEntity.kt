package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Tracked Profile (CONTEXT.md): a lightweight person record an Athlete
 * creates to run Quick Count for someone else — a display name, no
 * login/Account of its own. Together, an Athlete's rows here are their
 * Roster.
 *
 * [accountId] is nullable (`workout-partner-v3` ticket 02), mirroring
 * [SessionEntity.accountId]: a Guest can now own a Roster of their own
 * (this ticket's own predecessor left Quick Count Account-holder-only,
 * per the resolved spec tension recorded in the old revision of this
 * comment — ticket 06 is what actually lets a Guest create one; this
 * ticket only widens the column so that's possible).
 *
 * The FK to [AccountEntity] intentionally has no `onDelete` — see
 * [SessionEntity]'s doc comment for why (no v1 feature deletes an Account).
 */
@Entity(
    tableName = "tracked_profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
        ),
    ],
    indices = [Index("accountId")],
)
data class TrackedProfileEntity(
    @PrimaryKey val id: String,
    val accountId: String?,
    val displayName: String,
)
