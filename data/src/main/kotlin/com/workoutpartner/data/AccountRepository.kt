package com.workoutpartner.data

import androidx.room.withTransaction
import com.workoutpartner.core.streaks.StreakCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `AccountRepository` (spec.md's Seam 3 module list): local, Room-backed
 * Account CRUD, keeping the cached [AccountEntity.currentStreak]/
 * [AccountEntity.bankedShields] columns in sync via [recomputeStreak], and
 * [claimGuestData] — the atomic re-ownership primitive ticket 08's Guest ->
 * Account migration transaction is built on (per this ticket's own scope:
 * "this ticket just needs to expose whatever primitive the migration needs
 * ... this ticket does not own the migration's business rules").
 *
 * No Firestore sync here: Account rows aren't in this ticket's "Sets and
 * Tallies" sync-queue scope (see [SyncEngine]'s doc comment) — Account
 * Firestore mirroring, and the genuine weeklyTarget/notificationsEnabled
 * two-device conflict that comes with it, is left to whichever later ticket
 * builds it.
 */
class AccountRepository(
    private val database: WorkoutPartnerDatabase,
    private val accountDao: AccountDao = database.accountDao(),
    private val sessionDao: SessionDao = database.sessionDao(),
    private val setDao: SetDao = database.setDao(),
    private val guestProfileDao: GuestProfileDao = database.guestProfileDao(),
) {
    suspend fun getAccount(accountId: String): AccountEntity? = accountDao.getById(accountId)

    suspend fun createAccount(
        accountId: String,
        weeklyTarget: Int = StreakCalculator.DEFAULT_WEEKLY_TARGET,
    ): AccountEntity {
        val account = AccountEntity(id = accountId, weeklyTarget = weeklyTarget)
        accountDao.insert(account)
        return account
    }

    /** [accountId] `null` updates the device's single Guest's Weekly Target instead (`workout-partner-v3` ticket 06, ADR-0007) — a Guest now accumulates the same Streak state an Account does. */
    suspend fun updateWeeklyTarget(accountId: String?, weeklyTarget: Int) {
        if (accountId != null) {
            val account = accountDao.getById(accountId) ?: return
            accountDao.update(account.copy(weeklyTarget = weeklyTarget))
        } else {
            val guest = guestProfileDao.get() ?: GuestProfileEntity()
            guestProfileDao.upsert(guest.copy(weeklyTarget = weeklyTarget))
        }
    }

    /** [accountId] `null` updates the device's single Guest's notification preference instead (`workout-partner-v3` ticket 06, ADR-0007) — a Guest can now enable the daily reminder. */
    suspend fun updateNotificationsEnabled(accountId: String?, enabled: Boolean) {
        if (accountId != null) {
            val account = accountDao.getById(accountId) ?: return
            accountDao.update(account.copy(notificationsEnabled = enabled))
        } else {
            val guest = guestProfileDao.get() ?: GuestProfileEntity()
            guestProfileDao.upsert(guest.copy(notificationsEnabled = enabled))
        }
    }

    /** Updates the Account's own body-stats (`workout-partner-v2` ticket 01) — collected once at onboarding, editable afterward from Settings. */
    suspend fun updateProfile(
        accountId: String,
        name: String,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        activityLevel: ActivityLevel,
    ) {
        val account = accountDao.getById(accountId) ?: return
        accountDao.update(
            account.copy(name = name, age = age, heightCm = heightCm, weightKg = weightKg, activityLevel = activityLevel),
        )
    }

    /**
     * A Guest's body-stats answers — captured before an Account exists, and,
     * since `workout-partner-v3` ticket 06, also editable afterward from
     * Settings the same way an Account's profile is. Reads the existing row
     * (if any) and copies only the body-stats fields onto it, rather than
     * `upsert`ing a fresh [GuestProfileEntity]: once a Guest has real
     * [GuestProfileEntity.weeklyTarget]/[GuestProfileEntity.currentStreak]/
     * [GuestProfileEntity.bankedShields]/[GuestProfileEntity.notificationsEnabled]
     * state, blindly replacing the whole row would silently reset it to
     * those columns' defaults.
     */
    suspend fun saveGuestProfile(
        name: String,
        age: Int,
        heightCm: Int,
        weightKg: Double,
        activityLevel: ActivityLevel,
    ) {
        val existing = guestProfileDao.get() ?: GuestProfileEntity()
        guestProfileDao.upsert(
            existing.copy(name = name, age = age, heightCm = heightCm, weightKg = weightKg, activityLevel = activityLevel),
        )
    }

    suspend fun getGuestProfile(): GuestProfileEntity? = guestProfileDao.get()

    /**
     * Recomputes the owner's cached `currentStreak`/`bankedShields` from
     * their full Set history via `core-streaks.StreakCalculator` — the "keep
     * them in sync... whenever a new Active Day lands" [AccountEntity]'s own
     * doc comment (ticket 05) calls for. [zone] resolves each Set's [Instant]
     * timestamp to the calendar day it counts as an Active Day on; [today]
     * is the caller's injected "today," same as [StreakCalculator] itself
     * requires (this repository doesn't read the system clock on its own).
     *
     * [accountId] `null` recomputes the device's single Guest's
     * [GuestProfileEntity] row instead (`workout-partner-v3` ticket 06,
     * ADR-0007) — a Guest now accumulates the same Streak/Shields state an
     * Account does, just cached on that row rather than [AccountEntity].
     */
    suspend fun recomputeStreak(accountId: String?, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val activeDays = setDao.getForAccount(accountId).map { it.timestamp.atZoneToLocalDate(zone) }.toSet()
        if (accountId != null) {
            val account = accountDao.getById(accountId) ?: return
            val status = StreakCalculator.calculate(activeDays = activeDays, today = today, weeklyTarget = account.weeklyTarget)
            accountDao.update(account.copy(currentStreak = status.currentStreak, bankedShields = status.bankedShields))
        } else {
            val guest = guestProfileDao.get() ?: GuestProfileEntity()
            val status = StreakCalculator.calculate(activeDays = activeDays, today = today, weeklyTarget = guest.weeklyTarget)
            guestProfileDao.upsert(guest.copy(currentStreak = status.currentStreak, bankedShields = status.bankedShields))
        }
    }

    /** Whether this device has any Guest Session ([SessionEntity.accountId] null) still waiting to be claimed — what ticket 07's Auth module checks before triggering migration on sign-up. */
    suspend fun hasUnclaimedGuestData(): Boolean = sessionDao.getUnowned().isNotEmpty()

    /**
     * Re-points every unowned (Guest) Session at [accountId], refreshes that
     * account's Streak from its now-complete history, and — if a
     * [GuestProfileEntity] row exists (`workout-partner-v2` ticket 01) —
     * copies its body-stats onto the new Account and clears the guest row.
     * The Guest period counts in full, since it's the same Session/Set
     * rows, just newly owned (see [AccountEntity]'s doc comment). All steps
     * run in one transaction: a crash partway through should never leave a
     * re-owned account with stale Streak columns or a half-claimed profile.
     *
     * This is the primitive, not the migration itself — ticket 08 also
     * needs to touch Firestore/Auth, which is beyond this repository.
     *
     * **Known gap, left to `workout-partner-v3` ticket 05:** since ticket 06
     * (ADR-0007) a Guest's [GuestProfileEntity.weeklyTarget]/[GuestProfileEntity.currentStreak]/
     * [GuestProfileEntity.bankedShields]/[GuestProfileEntity.notificationsEnabled]
     * are real, user-set state, not just unused defaults — but this method
     * still only copies body-stats onto the new Account and discards the
     * rest via [GuestProfileDao.clear], and the [recomputeStreak] call above
     * recomputes against the new Account's own (default) `weeklyTarget`,
     * not the Guest's. A Guest who set a custom target and banked a Shield
     * under it currently loses both on sign-up. Ticket 05 owns the actual
     * sign-up/merge contract for this widened Guest state ("claims... Guest
     * Weekly Target; recomputes Streak/Shields from the full Set history");
     * re-flagged here rather than silently fixed, since ticket 05 also
     * needs to decide Merge's "Account's existing target wins" case, which
     * a plain copy here would get wrong for that path.
     */
    suspend fun claimGuestData(accountId: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        database.withTransaction {
            sessionDao.claimUnowned(accountId)
            recomputeStreak(accountId, today, zone)
            guestProfileDao.get()?.let { guestProfile ->
                accountDao.getById(accountId)?.let { account ->
                    accountDao.update(
                        account.copy(
                            name = guestProfile.name,
                            age = guestProfile.age,
                            heightCm = guestProfile.heightCm,
                            weightKg = guestProfile.weightKg,
                            activityLevel = guestProfile.activityLevel,
                        ),
                    )
                }
                guestProfileDao.clear()
            }
        }
    }
}

private fun Instant.atZoneToLocalDate(zone: ZoneId): LocalDate = this.atZone(zone).toLocalDate()
