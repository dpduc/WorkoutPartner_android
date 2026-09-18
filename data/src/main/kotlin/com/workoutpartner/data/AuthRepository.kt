package com.workoutpartner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The Auth module (spec.md's module list): sign-up/sign-in, [authState]
 * (Guest vs. signed-in Account, per [AuthState]), and detecting + triggering
 * the Guest -> Account migration on sign-up (ticket 07's scope). Covers user
 * stories 1, 5, 6, 7.
 *
 * [onGuestDataToMigrate] defaults to a no-op deliberately: this ticket's job
 * is the trigger — noticing a local Guest record exists and calling out —
 * not the migration transaction's own business rules (out of scope, per
 * this ticket's own text; ticket 08 owns those). Ticket 08 supplies the real
 * function, likely one that calls [AccountRepository.claimGuestData] (the
 * re-ownership primitive ticket 06 built) plus whatever else a full
 * transaction needs (e.g. deciding what happens if it fails partway —
 * `signUp` has already created the Firebase identity and local Account row
 * by the time this callback runs, so that's ticket 08's problem to design
 * for, not this default's).
 */
class AuthRepository(
    private val authGateway: AuthGateway,
    private val accountRepository: AccountRepository,
    private val onGuestDataToMigrate: suspend (accountId: String) -> Unit = {},
) {
    val authState: Flow<AuthState> = authGateway.currentUserId.map { uid ->
        if (uid == null) AuthState.Guest else AuthState.SignedIn(uid)
    }

    /**
     * Creates a new Firebase identity and its local [AccountEntity], then
     * calls [onGuestDataToMigrate] if this device has any Guest data
     * ([AccountRepository.hasUnclaimedGuestData]) waiting to be claimed.
     */
    suspend fun signUp(email: String, password: String): String {
        val accountId = authGateway.signUpWithEmail(email, password)
        accountRepository.createAccount(accountId)
        if (accountRepository.hasUnclaimedGuestData()) {
            onGuestDataToMigrate(accountId)
        }
        return accountId
    }

    /**
     * Signs in an existing Account and returns its id. Doesn't create or
     * touch any local [AccountEntity] row — a fresh device signing into a
     * pre-existing Account may have never cached one (ticket 06 has no
     * pull-sync path yet, see [SyncEngine]'s doc comment), and fabricating a
     * blank local row isn't something this ticket was asked to paper that
     * gap over with. "Synced history should become visible" (this ticket's
     * own text) stays genuinely unmet until that gap closes.
     */
    suspend fun signIn(email: String, password: String): String = authGateway.signInWithEmail(email, password)

    /**
     * Signs in with a Google ID token and returns the Account's id. Behaves
     * exactly like [signIn] with respect to local state and Guest data —
     * touches neither. Auto-claiming Guest data on sign-in (this method
     * used to do so unconditionally) is exactly the bug
     * `workout-partner-v3` ticket 01 exists to remove; the real pending/
     * merge/discard sign-in contract is ticket 05's job, not this one's.
     */
    suspend fun signInWithGoogle(idToken: String): String = authGateway.signInWithGoogle(idToken)

    suspend fun signOut() = authGateway.signOut()
}
