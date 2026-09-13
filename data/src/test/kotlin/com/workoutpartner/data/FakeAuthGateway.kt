package com.workoutpartner.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Fake [AuthGateway] for tests — no real Firebase project needed, same
 * minimal spirit as ticket 06's [FakeRemoteSyncGateway]: it tracks just
 * enough state (an email -> uid mapping, and who's currently "signed in")
 * for [AuthRepository]'s own logic to be exercised, without re-implementing
 * Firebase's actual validation rules (password checking, duplicate-email
 * rejection) that nothing in this ticket's tests needs.
 */
class FakeAuthGateway : AuthGateway {
    private val uidsByEmail = mutableMapOf<String, String>()
    override val currentUserId = MutableStateFlow<String?>(null)

    /** Test seeding only: registers an account under a caller-chosen [accountId] rather than a freshly-minted one — simulates "an Account that already exists (e.g. from another device)," without going through [signUpWithEmail]. */
    fun registerExistingAccount(accountId: String, email: String) {
        uidsByEmail[email] = accountId
    }

    override suspend fun signUpWithEmail(email: String, password: String): String {
        val uid = uidsByEmail.getOrPut(email) { UUID.randomUUID().toString() }
        currentUserId.value = uid
        return uid
    }

    override suspend fun signInWithEmail(email: String, password: String): String {
        val uid = uidsByEmail.getValue(email)
        currentUserId.value = uid
        return uid
    }

    override suspend fun signOut() {
        currentUserId.value = null
    }
}
