package com.workoutpartner.app.quickcount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.data.RosterRepository
import com.workoutpartner.data.TrackedProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Roster CRUD (ticket 11, spec.md user story 34): create/list Tracked
 * Profiles. Thin Room-backed glue, not unit-tested — `RosterRepository`
 * (ticket 06) already is. [accountId] `null` operates on the device's
 * single Guest's Roster instead of an Account's (`workout-partner-v3`
 * ticket 06, ADR-0007).
 */
class RosterViewModel(
    private val accountId: String?,
    private val rosterRepository: RosterRepository,
) : ViewModel() {
    private val _roster = MutableStateFlow<List<TrackedProfileEntity>>(emptyList())
    val roster: StateFlow<List<TrackedProfileEntity>> = _roster.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _roster.value = rosterRepository.getRoster(accountId) }
    }

    fun createProfile(displayName: String) {
        if (displayName.isBlank()) return
        viewModelScope.launch {
            rosterRepository.createTrackedProfile(accountId, displayName.trim())
            refresh()
        }
    }
}
