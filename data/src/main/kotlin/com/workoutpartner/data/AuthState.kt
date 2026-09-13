package com.workoutpartner.data

/**
 * Whether the app currently has a Firebase Auth identity at all (CONTEXT.md's
 * Account/Guest distinction). [Guest] is not "signed in anonymously" — per
 * ticket 07's scope ("no Firebase Auth identity involved while a Guest,
 * purely local"), this codebase never calls Firebase's anonymous-auth API;
 * [Guest] simply means [AuthGateway.currentUserId] is null.
 */
sealed interface AuthState {
    data object Guest : AuthState
    data class SignedIn(val accountId: String) : AuthState
}
