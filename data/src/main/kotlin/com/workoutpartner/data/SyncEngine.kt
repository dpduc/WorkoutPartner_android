package com.workoutpartner.data

/**
 * The "background sync queue pushing to Firestore when online" (ticket 06):
 * replays [PendingSyncEntity] rows — queued by [SetRepository]/
 * [TallyRepository] at write time — against a [RemoteSyncGateway], oldest
 * first. Actually calling [syncPendingChanges] on a schedule/connectivity
 * change (WorkManager, a network callback) is `app`-module wiring, not this
 * ticket's concern; this class is the pure, directly-callable sync step
 * spec.md's Testing Decisions ask to be tested against "an in-memory Room
 * database and a fake remote."
 *
 * ## Reconciliation (spec.md user story 43 / ADR-0002)
 *
 * Scoped to Sets and Tallies only, per this ticket's own scope line
 * ("Offline write → local queue → sync-on-reconnect for **Sets and
 * Tallies**") — Account/Session/Roster rows are Room-only in this ticket;
 * their Firestore mirroring isn't built here (a real gap against ADR-0001's
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
 * Session (and Account) Firestore mirroring first, since [SetEntity] itself
 * carries no `accountId` to query by — a bigger lift than this ticket's
 * "Sets and Tallies" push-only scope, so it's left for a later ticket
 * rather than rushed or silently assumed solved here.
 *
 * **A genuine conflict case this ticket does *not* auto-resolve**, flagged
 * per this ticket's own instruction to surface one rather than silently
 * picking a side: [AccountEntity]'s `weeklyTarget`/`notificationsEnabled`
 * are user preferences, not derived facts — if two offline devices change
 * one to different values before either syncs, there is no principled
 * "merge" the way `currentStreak`/`bankedShields` have one (recompute fresh
 * from the full Set history, once a device actually has all of it — see the
 * pull-path gap above). This ticket doesn't implement Account Firestore
 * sync at all yet, so the question is moot for now — but whichever ticket
 * adds it needs an explicit answer (e.g. surfacing the conflict to the
 * user) instead of silent last-write-wins, which would quietly discard one
 * device's deliberate choice.
 */
class SyncEngine(
    private val pendingSyncDao: PendingSyncDao,
    private val setDao: SetDao,
    private val tallyDao: TallyDao,
    private val remote: RemoteSyncGateway,
) {
    /**
     * Attempts every queued item in enqueue order. The first failure stops
     * the run — a failure here means "offline or the remote is
     * unreachable," and there's no reason to expect the next item to
     * succeed where this one didn't; the rest stay queued for the next
     * attempt. An item whose underlying Set/Tally has since vanished
     * locally (shouldn't normally happen — nothing in this module deletes
     * Sets/Tallies) is dropped from the queue rather than retried forever.
     */
    suspend fun syncPendingChanges(): SyncResult {
        val pending = pendingSyncDao.getAllOrdered()
        var succeeded = 0

        for (item in pending) {
            val pushed = when (item.entityKind) {
                SyncEntityKind.SET -> setDao.getById(item.entityId)?.let { remote.pushSet(it) } ?: true
                SyncEntityKind.TALLY -> tallyDao.getById(item.entityId)?.let { remote.pushTally(it) } ?: true
            }

            if (!pushed) break

            pendingSyncDao.delete(item)
            succeeded++
        }

        return SyncResult(succeeded = succeeded, remaining = pendingSyncDao.count())
    }
}
