package com.workoutpartner.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The real [AuthGateway]: a thin wrapper over [FirebaseAuth]'s email/
 * password sign-up/sign-in and its current-user state.
 *
 * Not verified against a live Firebase project in this session — same
 * caveat as [FirestoreSyncGateway]: there is still no `google-services.json`
 * (ticket 01's scaffold flagged this), so the `google-services` Gradle
 * plugin isn't applied to any module yet. Exercised in tests only through a
 * fake [AuthGateway].
 */
class FirebaseAuthGateway(private val firebaseAuth: FirebaseAuth) : AuthGateway {

    override val currentUserId: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.uid) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signUpWithEmail(email: String, password: String): String =
        requireNotNull(firebaseAuth.createUserWithEmailAndPassword(email, password).awaitResult().user) {
            "Firebase reported success but returned no user"
        }.uid

    override suspend fun signInWithEmail(email: String, password: String): String =
        requireNotNull(firebaseAuth.signInWithEmailAndPassword(email, password).awaitResult().user) {
            "Firebase reported success but returned no user"
        }.uid

    override suspend fun signOut() = firebaseAuth.signOut()
}
