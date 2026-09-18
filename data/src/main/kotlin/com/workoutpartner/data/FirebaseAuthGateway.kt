package com.workoutpartner.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The real [AuthGateway]: wraps [FirebaseAuth]'s email/password, Google
 * sign-in, and its current-user state.
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

    override suspend fun signInWithGoogle(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return requireNotNull(firebaseAuth.signInWithCredential(credential).awaitResult().user) {
            "Firebase reported success but returned no user"
        }.uid
    }

    override suspend fun signOut() = firebaseAuth.signOut()
}
