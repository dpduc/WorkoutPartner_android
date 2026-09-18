package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.workoutpartner.core.streaks.StreakCalculator

/**
 * A Guest's own local record (CONTEXT.md): body-stats answers (name, age,
 * height, weight, [ActivityLevel]) captured during onboarding, plus —
 * accumulated for real since `workout-partner-v3` ticket 06 (ADR-0007) —
 * the same Weekly Target/Streak/Streak Shield/notification state an
 * [AccountEntity] carries. Always a single row (fixed [id]): a device has
 * at most one Guest in progress at a time, same as
 * [SessionEntity.accountId] `null` represents "the" current Guest, not a
 * set of Guests.
 *
 * Body-stats are nullable, not defaulted, mirroring [AccountEntity]'s own
 * reasoning: a Guest can now have Streak state before — or without ever —
 * answering onboarding, and a fabricated default would misrepresent that
 * as a real answer.
 *
 * [AccountRepository.claimGuestData] (sign-up) and
 * [AccountRepository.mergeGuestData] (sign-in to an existing Account, ticket
 * 05) both copy this row's state onto an [AccountEntity] and clear it, in
 * the same transaction that claims unowned Sessions/Tracked Profiles — see
 * those functions' doc comments.
 */
@Entity(tableName = "guest_profile")
data class GuestProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String? = null,
    val age: Int? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val weeklyTarget: Int = StreakCalculator.DEFAULT_WEEKLY_TARGET,
    val currentStreak: Int = 0,
    val bankedShields: Int = 0,
    val notificationsEnabled: Boolean = true,
) {
    /**
     * Whether this row carries any real answer/state beyond a freshly
     * `GuestProfileEntity()`-constructed row's defaults — what
     * [AccountRepository.hasUnclaimedGuestData] (`workout-partner-v3`
     * ticket 05) checks alongside unowned Sessions/Tracked Profiles, since
     * a Guest can now have a customized Weekly Target or a banked Shield
     * with no body-stats answered at all.
     */
    fun hasNonDefaultState(): Boolean = this != GuestProfileEntity(id = id)

    companion object {
        const val SINGLETON_ID = 0
    }
}
