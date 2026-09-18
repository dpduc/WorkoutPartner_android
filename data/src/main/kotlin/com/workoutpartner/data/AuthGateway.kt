package com.workoutpartner.data

import kotlinx.coroutines.flow.Flow

/**
 * The raw Firebase Auth operations [AuthRepository] builds on —
 * [FirebaseAuthGateway] is the real implementation; tests drive
 * [AuthRepository] against a fake instead, the same split ticket 06 used
 * for [RemoteSyncGateway]/[SyncEngine].
 *
 * Email/password plus Google sign-in — no phone/SMS, and no Android UI
 * types on this interface. Adding another provider later means adding
 * another `signUpWith*`/`signInWith*` pair here, not redesigning it.
 */
interface AuthGateway {
    /** The signed-in Firebase UID, or null while a Guest — see [AuthState]. */
    val currentUserId: Flow<String?>

    /** Returns the new Firebase UID. Throws on failure (e.g. email already in use, weak password) — this is a thin wrapper, not an error-modeling layer; ticket 13's UI inspects the exception. */
    suspend fun signUpWithEmail(email: String, password: String): String

    /** Returns the existing Firebase UID. Throws on failure (e.g. wrong password, no such account). */
    suspend fun signInWithEmail(email: String, password: String): String

    /**
     * Returns the Firebase UID after authenticating with a Google ID token.
     * [LocalAuthGateway] (no Firebase configured) throws
     * [GoogleSignInUnavailableException] rather than faking a provider it
     * can't honestly offer.
     */
    suspend fun signInWithGoogle(idToken: String): String

    suspend fun signOut()
}

/** Thrown by [signInWithGoogle] when there's no real Google provider behind it — see [LocalAuthGateway]. */
class GoogleSignInUnavailableException :
    Exception("Google sign-in isn't available in local mode — sign in with email instead.")
