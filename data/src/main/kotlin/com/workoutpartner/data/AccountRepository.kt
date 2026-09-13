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

    suspend fun updateWeeklyTarget(accountId: String, weeklyTarget: Int) {
        val account = accountDao.getById(accountId) ?: return
        accountDao.update(account.copy(weeklyTarget = weeklyTarget))
    }

    suspend fun updateNotificationsEnabled(accountId: String, enabled: Boolean) {
        val account = accountDao.getById(accountId) ?: return
        accountDao.update(account.copy(notificationsEnabled = enabled))
    }

    /**
     * Recomputes [AccountEntity.currentStreak]/[AccountEntity.bankedShields]
     * from the account's full Set history via `core-streaks.StreakCalculator`
     * — the "keep them in sync... whenever a new Active Day lands" this
     * schema's own doc comment (ticket 05) calls for. [zone] resolves each
     * Set's [Instant] timestamp to the calendar day it counts as an Active
     * Day on; [today] is the caller's injected "today," same as
     * [StreakCalculator] itself requires (this repository doesn't read the
     * system clock on its own).
     */
    suspend fun recomputeStreak(accountId: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        val account = accountDao.getById(accountId) ?: return
        val activeDays = setDao.getForAccount(accountId).map { it.timestamp.atZoneToLocalDate(zone) }.toSet()
        val status = StreakCalculator.calculate(activeDays = activeDays, today = today, weeklyTarget = account.weeklyTarget)
        accountDao.update(account.copy(currentStreak = status.currentStreak, bankedShields = status.bankedShields))
    }

    /**
     * Re-points every unowned (Guest) Session at [accountId], then refreshes
     * that account's Streak from its now-complete history — the Guest
     * period counts in full, since it's the same Session/Set rows, just
     * newly owned (see [AccountEntity]'s doc comment). Both steps run in one
     * transaction: a crash between them should never leave a re-owned
     * account with stale Streak columns.
     *
     * This is the primitive, not the migration itself — ticket 08 also
     * needs to touch Firestore/Auth, which is beyond this repository.
     */
    suspend fun claimGuestData(accountId: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        database.withTransaction {
            sessionDao.claimUnowned(accountId)
            recomputeStreak(accountId, today, zone)
        }
    }
}

private fun Instant.atZoneToLocalDate(zone: ZoneId): LocalDate = this.atZone(zone).toLocalDate()
