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
 * the three atomic Guest-data transactions [AuthRepository] is built on
 * (`workout-partner-v3` ticket 05): [claimGuestData] (sign-up),
 * [mergeGuestData] (sign-in to an existing Account), and [discardGuestData].
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
    private val trackedProfileDao: TrackedProfileDao = database.trackedProfileDao(),
    private val tallyDao: TallyDao = database.tallyDao(),
    private val pendingSyncDao: PendingSyncDao = database.pendingSyncDao(),
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

    /**
     * Whether this device has any Guest data still waiting to be claimed —
     * what [AuthRepository] checks before triggering a claim on sign-up or
     * reporting a sign-in as pending (`workout-partner-v3` ticket 05).
     * Widened beyond unowned Sessions: an unowned Tracked Profile (a Guest's
     * Roster, ticket 06) or a [GuestProfileEntity] row carrying real state —
     * a customized Weekly Target, a banked Shield — count too, even with no
     * Session ever started (a Quick-Count-only Guest).
     */
    suspend fun hasUnclaimedGuestData(): Boolean {
        if (sessionDao.getUnowned().isNotEmpty()) return true
        if (trackedProfileDao.getForAccount(null).isNotEmpty()) return true
        return guestProfileDao.get()?.hasNonDefaultState() == true
    }

    /** The counts behind [hasUnclaimedGuestData] — what a Merge/Discard prompt (ticket 09) shows the Athlete before they choose. */
    suspend fun unclaimedGuestDataSummary(): GuestDataSummary {
        val unownedProfiles = trackedProfileDao.getForAccount(null)
        return GuestDataSummary(
            sessionCount = sessionDao.getUnowned().size,
            trackedProfileCount = unownedProfiles.size,
            tallyCount = unownedProfiles.sumOf { tallyDao.getForTrackedProfile(it.id).size },
        )
    }

    /**
     * Re-points every unowned (Guest) Session and Tracked Profile (and, via
     * the latter, its Tallies — ownership there is inherited through
     * [TrackedProfileEntity.accountId], not stored directly on
     * [TallyEntity]) at [accountId], copies the Guest's body-stats and
     * Weekly Target onto the new Account, refreshes its Streak from the
     * now-complete history, and clears the Guest record. A brand new
     * Account has nothing of its own to conflict with, so every field
     * copies over unconditionally — contrast [mergeGuestData], where an
     * *existing* Account's own values win.
     *
     * The Weekly Target copy happens before [recomputeStreak] runs, not
     * after: [recomputeStreak] computes Shields against whatever
     * `weeklyTarget` the Account row currently has, so recomputing first
     * would grade the Guest's history against the wrong target.
     *
     * All steps run in one transaction (`workout-partner-v3` ticket 05): a
     * crash partway through leaves this device's Guest data exactly as
     * unclaimed as it started, never half-moved.
     *
     * This is the primitive, not the sign-up flow itself — [AuthRepository.signUp]
     * is what calls this after creating the Account.
     */
    suspend fun claimGuestData(accountId: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        database.withTransaction {
            claimSessionsAndTrackedProfiles(accountId)
            applyGuestProfile(accountId) { account, guest ->
                account.copy(
                    name = guest.name,
                    age = guest.age,
                    heightCm = guest.heightCm,
                    weightKg = guest.weightKg,
                    activityLevel = guest.activityLevel,
                    weeklyTarget = guest.weeklyTarget,
                )
            }
            recomputeStreak(accountId, today, zone)
        }
    }

    /**
     * Folds pending Guest data onto an *existing* Account at sign-in
     * (`workout-partner-v3` ticket 05; the Merge/Discard prompt itself is
     * ticket 09's). Moves the same Sessions/Tracked Profiles
     * [claimGuestData] does, but the Account's own Weekly Target and
     * body-stats win — Guest body-stats fill in only the fields the
     * Account doesn't already have an answer for (`?:`, field by field),
     * and `weeklyTarget` is left untouched entirely, since (unlike a fresh
     * Account) there's always an existing value here that should win.
     * Streak/Shields are recomputed across the merged history either way.
     */
    suspend fun mergeGuestData(accountId: String, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
        database.withTransaction {
            claimSessionsAndTrackedProfiles(accountId)
            applyGuestProfile(accountId) { account, guest ->
                account.copy(
                    name = account.name ?: guest.name,
                    age = account.age ?: guest.age,
                    heightCm = account.heightCm ?: guest.heightCm,
                    weightKg = account.weightKg ?: guest.weightKg,
                    activityLevel = account.activityLevel ?: guest.activityLevel,
                )
            }
            recomputeStreak(accountId, today, zone)
        }
    }

    /**
     * Permanently deletes every unowned Guest row (`workout-partner-v3`
     * ticket 05; the confirmation UI is ticket 09's): Sessions and their
     * Sets (cascade), Tracked Profiles and their Tallies (cascade), the
     * Guest record, and any `pending_sync` entries queued for the
     * Sets/Tallies about to disappear — `pending_sync` has no FK to what it
     * queues, so cascading deletes alone would leave those orphaned. Set/
     * Tally ids are read *before* the cascade-deletes below remove their
     * parent rows, since there'd be nothing left to join against after.
     * One transaction: a crash partway through leaves this device's Guest
     * data exactly as unclaimed (and intact) as it started.
     */
    suspend fun discardGuestData() {
        database.withTransaction {
            val setIdsToDelete = sessionDao.getUnowned().flatMap { session -> setDao.getForSession(session.id).map { it.id } }
            val tallyIdsToDelete = trackedProfileDao.getForAccount(null)
                .flatMap { profile -> tallyDao.getForTrackedProfile(profile.id).map { it.id } }

            pendingSyncDao.deleteByEntityIds(setIdsToDelete + tallyIdsToDelete)
            sessionDao.deleteUnowned()
            trackedProfileDao.deleteUnowned()
            guestProfileDao.clear()
        }
    }

    private suspend fun claimSessionsAndTrackedProfiles(accountId: String) {
        sessionDao.claimUnowned(accountId)
        trackedProfileDao.claimUnowned(accountId)
    }

    /**
     * The shape [claimGuestData] and [mergeGuestData] share: if a
     * [GuestProfileEntity] row exists, fold it onto [accountId]'s
     * [AccountEntity] via [merge] and clear the Guest row — a no-op
     * (neither the Account nor the Guest record is touched) if either row
     * is missing. [merge] is the one thing the two callers disagree on:
     * claim copies every Guest field unconditionally, merge keeps the
     * Account's own values and only fills in what's missing.
     */
    private suspend fun applyGuestProfile(accountId: String, merge: (account: AccountEntity, guest: GuestProfileEntity) -> AccountEntity) {
        val guestProfile = guestProfileDao.get() ?: return
        accountDao.getById(accountId)?.let { account -> accountDao.update(merge(account, guestProfile)) }
        guestProfileDao.clear()
    }
}

private fun Instant.atZoneToLocalDate(zone: ZoneId): LocalDate = this.atZone(zone).toLocalDate()
