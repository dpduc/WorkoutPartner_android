package com.workoutpartner.data

import kotlinx.coroutines.flow.Flow

/**
 * The raw Firebase Auth operations [AuthRepository] builds on —
 * [FirebaseAuthGateway] is the real implementation; tests drive
 * [AuthRepository] against a fake instead, the same split ticket 06 used
 * for [RemoteSyncGateway]/[SyncEngine].
 *
 * Email/password only (ticket 07's scope: "implement email/password as the
 * baseline; treat 'or a provider' as an easy extension point, not a hard v1
 * requirement"). Adding a provider later means adding another
 * `signUpWith*`/`signInWith*` pair here, not redesigning this interface.
 */
interface AuthGateway {
    /** The signed-in Firebase UID, or null while a Guest — see [AuthState]. */
    val currentUserId: Flow<String?>

    /** Returns the new Firebase UID. Throws on failure (e.g. email already in use, weak password) — this is a thin wrapper, not an error-modeling layer; ticket 13's UI inspects the exception. */
    suspend fun signUpWithEmail(email: String, password: String): String

    /** Returns the existing Firebase UID. Throws on failure (e.g. wrong password, no such account). */
    suspend fun signInWithEmail(email: String, password: String): String

    suspend fun signOut()
}
