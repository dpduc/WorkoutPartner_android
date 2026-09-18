package com.workoutpartner.data

/**
 * What [AuthRepository.signIn]/[AuthRepository.signInWithGoogle] found:
 * either nothing was waiting, or this device has Guest data
 * ([AccountRepository.hasUnclaimedGuestData]) pending a
 * [GuestDataResolution] via [AuthRepository.resolvePendingGuestData].
 */
sealed interface SignInResult {
    val accountId: String

    data class SignedIn(override val accountId: String) : SignInResult
    data class GuestDataPending(override val accountId: String, val summary: GuestDataSummary) : SignInResult
}

/** How to resolve [SignInResult.GuestDataPending] — see [AuthRepository.resolvePendingGuestData]. Cancel isn't a case here: the caller just calls [AuthRepository.signOut] directly. */
enum class GuestDataResolution { MERGE, DISCARD }
