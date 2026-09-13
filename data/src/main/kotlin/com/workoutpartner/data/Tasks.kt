package com.workoutpartner.data

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bridges a Google Play Services [Task] (Firebase's async result type, used
 * by both Firestore and Auth) into a suspend call — the one place this
 * conversion is written, shared by [FirestoreSyncGateway] and
 * [FirebaseAuthGateway] rather than each redefining it.
 */
internal suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
}
