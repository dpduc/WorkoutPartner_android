package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.workoutpartner.core.streaks.StreakCalculator

/**
 * An Account (CONTEXT.md): identity, Weekly Target, banked Streak Shield
 * count, current Streak, and notification preference. Created directly at
 * sign-up, or by migrating a Guest (ADR-0004) — there is no row here for a
 * Guest, and deliberately no parallel "Guest-shaped" entity mirroring these
 * columns either. Streak/Weekly Target/Shields are Account-holder-only
 * concepts (spec.md user stories 26-31 all read "As an Account holder...");
 * a Guest simply hasn't started that yet. What a Guest *does* accumulate —
 * [SessionEntity]/[SetEntity] rows with a null [SessionEntity.accountId] —
 * persists in the same tables an Account's would, per ticket 05's "Guest
 * local record: same shape as an Account's local data, unowned until
 * migration assigns it an Account id." Once migration (ticket 08) points
 * those rows at a freshly-created AccountEntity row, ticket 06 can replay
 * the *full* history (Guest period included) through [StreakCalculator] to
 * compute that new row's initial [currentStreak]/[bankedShields] — nothing
 * about the Guest period is lost, it's just not stored as Account columns
 * before an Account exists to own them.
 *
 * [currentStreak] and [bankedShields] mirror
 * `com.workoutpartner.core.streaks.StreakStatus`'s fields — the repository
 * layer (ticket 06) is expected to keep them in sync by recomputing via
 * [StreakCalculator] whenever a new Active Day lands, rather than this
 * schema deriving them itself. Seam 2 stays a pure function; this column is
 * just where its last-known output is cached for fast reads.
 *
 * [notificationsEnabled] is a minimal placeholder for "notification
 * preference" — ticket 12 (daily reminder notification) may need to extend
 * this (e.g. a preferred time) once it's built; not speculated on further
 * here.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    /** Firebase Auth UID (ticket 07 assigns this at sign-up/migration). */
    @PrimaryKey val id: String,
    val weeklyTarget: Int = StreakCalculator.DEFAULT_WEEKLY_TARGET,
    val bankedShields: Int = 0,
    val currentStreak: Int = 0,
    val notificationsEnabled: Boolean = true,
)
