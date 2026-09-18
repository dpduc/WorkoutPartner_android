package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.workoutpartner.core.streaks.StreakCalculator

/**
 * A Guest's own local record (CONTEXT.md): body-stats answers (name, age,
 * height, weight, [ActivityLevel]) captured during onboarding, plus —
 * as of `workout-partner-v3` ticket 02 — the same Weekly Target/Streak/
 * Streak Shield/notification state an [AccountEntity] carries, since a
 * Guest now accumulates that too (ticket 06's job to actually populate;
 * this ticket only makes room for it). Always a single row (fixed [id]): a
 * device has at most one Guest in progress at a time, same as
 * [SessionEntity.accountId] `null` represents "the" current Guest, not a
 * set of Guests.
 *
 * Body-stats are nullable, not defaulted, mirroring [AccountEntity]'s own
 * reasoning: a Guest can now have Streak state before — or without ever —
 * answering onboarding, and a fabricated default would misrepresent that
 * as a real answer.
 *
 * [AccountRepository.claimGuestData] copies this row's body-stats onto the
 * new [AccountEntity] and clears it, in the same transaction that claims
 * unowned Sessions — see that function's doc comment. Caution for whoever
 * builds ticket 06: [AccountRepository.saveGuestProfile] currently
 * `REPLACE`s this entire row every call, which was harmless when the row
 * only held body-stats; once a caller starts writing real
 * [weeklyTarget]/[currentStreak]/[bankedShields] values here, that call
 * needs to stop blindly overwriting them back to defaults.
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
    companion object {
        const val SINGLETON_ID = 0
    }
}
