package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Tracked Profile (CONTEXT.md): a lightweight person record an Account
 * creates to run Quick Count for someone else — a display name, no
 * login/Account of its own. Together, an Account's rows here are its Roster.
 *
 * [accountId] is non-null, unlike [SessionEntity.accountId]: Quick Count and
 * the Roster are Account-holder-only features (spec.md user stories 34-41
 * all read "As an Account holder..."), so — unlike a Session — there's no
 * "unowned Guest" state for a Tracked Profile to be in.
 *
 * Known spec tension, not resolved here: spec.md user story 2 and
 * ADR-0004 both describe a Guest's "Sets and Tallies" being saved locally,
 * which would require a Guest to have Tracked Profiles too — in apparent
 * conflict with stories 34-41 restricting Quick Count to Account holders.
 * This schema follows ticket 05's own literal scope list ("Roster/Tracked
 * Profile: owning Account id") and stories 34-41, i.e. Quick Count is
 * Account-only; flagging this rather than silently picking a side. If a
 * later ticket settles it the other way, this column becomes nullable the
 * same way [SessionEntity.accountId] already is — no other schema change
 * needed.
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
    val accountId: String,
    val displayName: String,
)
