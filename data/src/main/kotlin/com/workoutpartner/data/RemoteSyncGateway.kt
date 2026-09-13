package com.workoutpartner.data

/**
 * The remote half of "offline write → local queue → sync-on-reconnect"
 * (ticket 06) — [FirestoreSyncGateway] is the real, Firestore-backed
 * implementation; tests drive [SyncEngine] against a fake instead (spec.md's
 * Testing Decisions: "tests against an in-memory Room database and a fake
 * remote"), so this seam can be exercised without a live Firebase project.
 *
 * Each push writes to a Firestore document keyed by the entity's own
 * client-generated id ([SetEntity.id]/[TallyEntity.id]) rather than
 * appending — see [SyncEngine]'s doc comment for why this is what makes
 * two-device reconciliation additive by construction.
 *
 * Returns `false` (not a thrown exception) for "try again later": both "no
 * connectivity" and "a transient remote error" look the same to
 * [SyncEngine] — it just leaves the item queued either way. An
 * implementation should only let an exception escape for a genuinely
 * unrecoverable local bug (e.g. a serialization error), not for ordinary
 * network failure.
 */
interface RemoteSyncGateway {
    suspend fun pushSet(set: SetEntity): Boolean

    suspend fun pushTally(tally: TallyEntity): Boolean
}
