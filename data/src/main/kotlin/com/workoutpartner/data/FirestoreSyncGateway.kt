package com.workoutpartner.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The real [RemoteSyncGateway]: writes to Firestore collections mirroring
 * the Room schema, per ADR-0001. Each push is a plain `.set(...)` keyed by
 * the entity's own client-generated id (never `.add()`, which would mint a
 * new random id) — a from-scratch write and a retry of an already-synced
 * item produce the identical document, so retries are safe, and two
 * devices' independently-created Sets/Tallies never collide on id (ticket
 * 05's UUID keys), which is what makes reconciliation additive.
 *
 * Not verified against a live Firebase project in this session: there is
 * still no `google-services.json` (ticket 01's scaffold flagged this;
 * ticket 07/14 are expected to provide it), so the `google-services` Gradle
 * plugin isn't applied to any module yet either. This class compiles and is
 * exercised in tests only through a fake [RemoteSyncGateway] (spec.md's
 * Testing Decisions) — the same "written for real, unverified end-to-end"
 * situation as ticket 03's CameraPoseTracker.
 */
class FirestoreSyncGateway(private val firestore: FirebaseFirestore) : RemoteSyncGateway {

    override suspend fun pushSet(set: SetEntity): Boolean = push(SETS_COLLECTION, set.id, set.toFirestoreMap())

    override suspend fun pushTally(tally: TallyEntity): Boolean = push(TALLIES_COLLECTION, tally.id, tally.toFirestoreMap())

    private suspend fun push(collection: String, documentId: String, data: Map<String, Any?>): Boolean =
        try {
            firestore.collection(collection).document(documentId).set(data).awaitResult()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network failure or a transient Firestore error: leave this
            // item queued for the next sync attempt rather than propagating
            // — see RemoteSyncGateway's doc comment on why this returns
            // false instead of throwing.
            false
        }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    companion object {
        const val SETS_COLLECTION = "sets"
        const val TALLIES_COLLECTION = "tallies"
    }
}
