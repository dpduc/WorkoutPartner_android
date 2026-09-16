package com.workoutpartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A Guest's body-stats answers (name, age, height, weight, [ActivityLevel]),
 * captured during onboarding before an Account exists — mirrors how Guest
 * Sessions/Sets already persist locally, unowned, until sign-up claims them
 * (ADR-0004). Always a single row (fixed [id]): a device has at most one
 * Guest in progress at a time, same as [SessionEntity.accountId] `null`
 * represents "the" current Guest, not a set of Guests.
 *
 * [AccountRepository.claimGuestData] copies this row's fields onto the new
 * [AccountEntity] and clears it, in the same transaction that claims unowned
 * Sessions — see that function's doc comment.
 */
@Entity(tableName = "guest_profile")
data class GuestProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String,
    val age: Int,
    val heightCm: Int,
    val weightKg: Double,
    val activityLevel: ActivityLevel,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
