package com.workoutpartner.data

/**
 * What [AccountRepository.unclaimedGuestDataSummary] found waiting on this
 * device — surfaced to the Athlete before choosing Merge or Discard
 * (`workout-partner-v3` ticket 05; the prompt UI itself is ticket 09's).
 */
data class GuestDataSummary(
    val sessionCount: Int,
    val trackedProfileCount: Int,
    val tallyCount: Int,
)
