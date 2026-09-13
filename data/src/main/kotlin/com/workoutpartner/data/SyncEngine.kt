package com.workoutpartner.data

/**
 * The "background sync queue pushing to Firestore when online" (ticket 06,
 * extended by ticket 14): replays [PendingSyncEntity] rows — queued by
 * [SetRepository]/[TallyRepository] at write time — against a
 * [RemoteSyncGateway], oldest first. Actually calling [syncPendingChanges]
 * on a schedule/connectivity change (WorkManager, a network callback) is
 * `app`-module wiring, not this class's concern; this is the pure, directly-
 * callable sync step spec.md's Testing Decisions ask to be tested against
 * "an in-memory Room database and a fake remote."
 *
 * ## Guest data has no Firestore presence until migration (ticket 14)
 *
 * A Set is only ever pushed once its owning Session has a real `accountId`
 * — resolved here via [sessionDao], not assumed. A Guest's Set (an unowned
 * Session) is left queued rather than pushed: "not yet owned" is a third
 * outcome alongside "pushed" and "network failed," not treated as either.
 * Once the Guest -> Account migration (ticket 08) re-points that Session,
 * the *same* already-queued [PendingSyncEntity] row resolves a real
 * `accountId` on the next sync attempt and pushes normally — no separate
 * "sync now that you've migrated" step is needed. A Tally's owning
 * `accountId` (via its [TrackedProfileEntity]) is always resolvable
 * immediately: [TrackedProfileEntity.accountId] is non-null by construction
 * (ticket 05 — Quick Count is Account-holder-only), so this "not yet owned"
 * case is Set-only.
 *
 * ## Reconciliation (spec.md user story 43 / ADR-0002)
 *
 * Scoped to Sets and Tallies only, per ticket 06's scope line ("Offline
 * write → local queue → sync-on-reconnect for **Sets and Tallies**") —
 * Account/Session/Roster documents themselves are Room-only; their
 * Firestore mirroring isn't built here (a real gap against ADR-0001's
 * "Firestore collections mirror the above," left for a later ticket rather
 * than rushed).
 *
 * For Sets and Tallies specifically, "prefer additive reconciliation" falls
 * out of ticket 05's schema for free on the *push* side rather than needing
 * extra merge logic: every Set/Tally has a client-generated UUID id, and
 * [FirestoreSyncGateway] writes by that id (never `.add()`). Two devices
 * logging offline before either has synced simply produce two different
 * Firestore documents once both come online — nothing is silently lost or
 * overwritten in Firestore, and nothing needs to be "merged" there, because
 * a Set/Tally is an immutable, append-only fact (this app never edits a
 * past Set's rep count) rather than mutable shared state.
 *
 * **What this ticket does *not* do — a real, not-yet-closed gap against
 * story 43's "reconcile sensibly," not just documented away**: there is no
 * *pull* path bringing a Set/Tally another device pushed back down into
 * *this* device's Room. So while Firestore ends up holding the true union
 * of both devices' history, neither device's own [AccountRepository]
 * (`recomputeStreak`/`getSetsForAccount`) sees more than what it wrote
 * itself — `currentStreak`/`bankedShields` are only ever accurate against
 * one device's local view, not the full merged history, until something
 * downloads the other device's rows too. Building that pull path also needs
 * Session (and Account) Firestore mirroring first — a bigger lift than
 * ticket 06's "Sets and Tallies" push-only scope, so it's left for a later
 * ticket rather than rushed or silently assumed solved here.
 *
 * **A genuine conflict case this ticket does *not* auto-resolve**, flagged
 * per ticket 06's own instruction to surface one rather than silently
 * picking a side: [AccountEntity]'s `weeklyTarget`/`notificationsEnabled`
 * are user preferences, not derived facts — if two offline devices change
 * one to different values before either syncs, there is no principled
 * "merge" the way `currentStreak`/`bankedShields` have one (recompute fresh
 * from the full Set history, once a device actually has all of it — see the
 * pull-path gap above). Account Firestore sync isn't implemented at all
 * yet, so the question is moot for now — but whichever ticket adds it needs
 * an explicit answer (e.g. surfacing the conflict to the user) instead of
 * silent last-write-wins, which would quietly discard one device's
 * deliberate choice.
 */
class SyncEngine(
    private val pendingSyncDao: PendingSyncDao,
    private val setDao: SetDao,
    private val tallyDao: TallyDao,
    private val sessionDao: SessionDao,
    private val trackedProfileDao: TrackedProfileDao,
    private val remote: RemoteSyncGateway,
) {
    /**
     * Attempts every queued item in enqueue order. A remote (network)
     * failure stops the run there — there's no reason to expect the next
     * item to succeed where this one didn't; the rest stay queued for the
     * next attempt. An item that's "not yet owned" (a Guest's Set) is
     * skipped without stopping the batch — later, independent items may
     * still be push-able. An item whose underlying Set/Tally has since
     * vanished locally (shouldn't normally happen — nothing in this module
     * deletes Sets/Tallies) is dropped from the queue rather than retried
     * forever.
     */
    suspend fun syncPendingChanges(): SyncResult {
        val pending = pendingSyncDao.getAllOrdered()
        var succeeded = 0

        for (item in pending) {
            when (resolveAndPush(item)) {
                PushOutcome.PUSHED -> {
                    pendingSyncDao.delete(item)
                    succeeded++
                }
                PushOutcome.NOT_YET_OWNED -> Unit // stays queued, keep going
                PushOutcome.REMOTE_FAILURE -> break
            }
        }

        return SyncResult(succeeded = succeeded, remaining = pendingSyncDao.count())
    }

    private suspend fun resolveAndPush(item: PendingSyncEntity): PushOutcome = when (item.entityKind) {
        SyncEntityKind.SET -> {
            val set = setDao.getById(item.entityId)
            if (set == null) {
                PushOutcome.PUSHED // vanished locally: treat as done, drop the stale entry
            } else {
                val accountId = sessionDao.getById(set.sessionId)?.accountId
                if (accountId == null) {
                    PushOutcome.NOT_YET_OWNED
                } else if (remote.pushSet(set, accountId)) {
                    PushOutcome.PUSHED
                } else {
                    PushOutcome.REMOTE_FAILURE
                }
            }
        }
        SyncEntityKind.TALLY -> {
            val tally = tallyDao.getById(item.entityId)
            if (tally == null) {
                PushOutcome.PUSHED
            } else {
                // TrackedProfileEntity.accountId is non-null by construction
                // (ticket 05) — a null lookup here means the profile itself
                // vanished, not "not yet owned"; treat the same as a
                // vanished Tally rather than looping on it forever.
                val accountId = trackedProfileDao.getById(tally.trackedProfileId)?.accountId
                if (accountId == null) {
                    PushOutcome.PUSHED
                } else if (remote.pushTally(tally, accountId)) {
                    PushOutcome.PUSHED
                } else {
                    PushOutcome.REMOTE_FAILURE
                }
            }
        }
    }

    private enum class PushOutcome { PUSHED, NOT_YET_OWNED, REMOTE_FAILURE }
}
