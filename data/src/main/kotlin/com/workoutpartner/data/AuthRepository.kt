package com.workoutpartner.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * The Auth module (spec.md's module list): sign-up/sign-in, [authState]
 * (Guest vs. signed-in Account, per [AuthState]), and — as of
 * `workout-partner-v3` ticket 05 — the single seam for every Guest-data
 * outcome on sign-up and sign-in. Covers user stories 1, 5, 6, 7, and
 * ticket 05's widened sign-in/sign-up contract.
 *
 * [clock] is where "today" comes from for the Streak recomputation
 * [AccountRepository.claimGuestData]/[AccountRepository.mergeGuestData]
 * need — this repository doesn't read the system clock inline, matching
 * [AccountRepository]'s own "caller injects today" convention; unlike an
 * app-layer ViewModel (which passes `today`/`zone` in explicitly, e.g.
 * [com.workoutpartner.app.progress.ProgressViewModel]), sign-up/sign-in
 * have no ViewModel of their own to hold one, so it's constructor-injected
 * here instead — the same shape [TallyRepository]'s own `clock` param uses.
 */
class AuthRepository(
    private val authGateway: AuthGateway,
    private val accountRepository: AccountRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    val authState: Flow<AuthState> = authGateway.currentUserId.map { uid ->
        if (uid == null) AuthState.Guest else AuthState.SignedIn(uid)
    }

    /**
     * Creates a new Firebase identity and its local [AccountEntity], then
     * claims every bit of this device's Guest data onto it
     * ([AccountRepository.claimGuestData]) if any exists. A brand new
     * Account never has anything of its own to conflict with, so sign-up
     * claims unconditionally rather than asking — contrast [signIn]/
     * [signInWithGoogle], which report pending data instead of touching it.
     */
    suspend fun signUp(email: String, password: String): String {
        val accountId = authGateway.signUpWithEmail(email, password)
        accountRepository.createAccount(accountId)
        if (accountRepository.hasUnclaimedGuestData()) {
            accountRepository.claimGuestData(accountId, today(), clock.zone)
        }
        return accountId
    }

    /**
     * Signs in an existing Account and reports whether this device's Guest
     * data (if any) is now pending resolution — never claims it itself.
     * Doesn't create or touch any local [AccountEntity] row otherwise — a
     * fresh device signing into a pre-existing Account may have never
     * cached one (no pull-sync path exists yet, see [SyncEngine]'s doc
     * comment), and fabricating a blank local row isn't this method's job.
     */
    suspend fun signIn(email: String, password: String): SignInResult = resultFor(authGateway.signInWithEmail(email, password))

    /** Signs in with a Google ID token. Behaves exactly like [signIn] with respect to local state and Guest data — see [resultFor]. */
    suspend fun signInWithGoogle(idToken: String): SignInResult = resultFor(authGateway.signInWithGoogle(idToken))

    private suspend fun resultFor(accountId: String): SignInResult =
        if (accountRepository.hasUnclaimedGuestData()) {
            SignInResult.GuestDataPending(accountId, accountRepository.unclaimedGuestDataSummary())
        } else {
            SignInResult.SignedIn(accountId)
        }

    /**
     * Resolves Guest data a [SignInResult.GuestDataPending] reported —
     * Merge folds it into [accountId]'s existing history
     * ([AccountRepository.mergeGuestData]), Discard permanently deletes it
     * ([AccountRepository.discardGuestData]). Sign-in has already completed
     * at the gateway by the time a caller has a [SignInResult.GuestDataPending]
     * to resolve, so there's no "cancel this sign-in" case here — backing
     * out of the prompt is just [signOut], leaving the Guest data as
     * pending as it was (still unclaimed, still intact) for next time.
     */
    suspend fun resolvePendingGuestData(accountId: String, resolution: GuestDataResolution) {
        when (resolution) {
            GuestDataResolution.MERGE -> accountRepository.mergeGuestData(accountId, today(), clock.zone)
            GuestDataResolution.DISCARD -> accountRepository.discardGuestData()
        }
    }

    suspend fun signOut() = authGateway.signOut()

    private fun today(): LocalDate = LocalDate.now(clock)
}
