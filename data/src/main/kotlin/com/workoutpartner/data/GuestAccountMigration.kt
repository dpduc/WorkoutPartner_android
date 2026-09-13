package com.workoutpartner.data

import java.time.Clock
import java.time.LocalDate

/**
 * The Guest -> Account migration transaction (ADR-0004; ticket 08's scope,
 * covering spec stories 2-4). This is the concrete function meant to be
 * wired into [AuthRepository]'s `onGuestDataToMigrate` hook (ticket 07) in
 * production — e.g. `AuthRepository(gateway, accountRepository,
 * onGuestDataToMigrate = GuestAccountMigration(accountRepository)::invoke)`.
 *
 * Everything a migration transaction needs turned out to already exist,
 * once ticket 06's [AccountRepository.claimGuestData] was in place:
 *  - **"one migration transaction, not a background job"** (ADR-0004 — the
 *    user should never see a migration "in progress" window): satisfied by
 *    `claimGuestData` running inside `database.withTransaction` and by
 *    [AuthRepository.signUp] awaiting this call directly rather than
 *    launching it — by the time `signUp` returns, the migration has already
 *    either fully completed or fully failed, nothing is left half-done.
 *  - **"uploads and re-owns"** (ADR-0004), for **Sets**: "re-owns" is
 *    `claimGuestData` re-pointing the Guest's Sessions at the new Account
 *    and refreshing its cached Streak from the now-complete history;
 *    "uploads" already happens for free, since [SetRepository.recordSet]
 *    enqueues every Set for Firestore sync regardless of ownership at write
 *    time (ticket 06) — a Guest's Sets were never withheld from the sync
 *    queue while unowned.
 *
 * **This migration does not touch Tallies at all — not an oversight, a
 * consequence of ticket 05's schema.** [TrackedProfileEntity.accountId] is
 * non-null (Quick Count/Roster is Account-holder-only per spec.md stories
 * 34-41), so there is no "unowned Guest Tally" state for this transaction
 * to claim — a Guest structurally cannot have created one to begin with,
 * under the schema as it stands today. This is the same tension ticket 05
 * flagged (CONTEXT.md/user story 2 describe a Guest having Tallies;
 * TrackedProfileEntity's non-null `accountId` says otherwise) — re-flagged
 * here, not silently resolved: if a later ticket makes
 * [TrackedProfileEntity.accountId] nullable to actually allow this, this
 * migration will need a `trackedProfileDao`-based claim step alongside
 * [SessionDao.claimUnowned], mirroring how Sessions are handled.
 *
 * So this class is a thin, named wrapper — not because the transaction
 * turned out to need nothing, but because ticket 06 already built the
 * transaction itself; what was missing was a concrete, production-ready
 * function of the right shape (`suspend (accountId: String) -> Unit`) to
 * hand to ticket 07's hook, instead of that hook defaulting to a no-op.
 *
 * If [claimGuestData][AccountRepository.claimGuestData] throws, this device's
 * Guest data simply stays unclaimed (`AccountRepository.hasUnclaimedGuestData`
 * remains true) rather than ending up partially migrated — Room's
 * transaction guarantees that. The Firebase identity and local Account row
 * `signUp` already created before calling this are not rolled back;
 * surfacing that and offering a retry is UI territory (ticket 13), not this
 * transaction's, per this ticket's own scope (out of scope: "the 'create an
 * Account?' prompt UI").
 */
class GuestAccountMigration(
    private val accountRepository: AccountRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend operator fun invoke(accountId: String) {
        accountRepository.claimGuestData(accountId, today = LocalDate.now(clock), zone = clock.zone)
    }
}
