package com.workoutpartner.app.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.core.streaks.StreakCalculator
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.SetEntity
import com.workoutpartner.data.SetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The impure glue for the Progress/Streaks UI (ticket 10): reads an
 * Account's Weekly Target/Streak/Shields (ticket 04's Streak Calculator
 * output, cached on [com.workoutpartner.data.AccountEntity] since ticket 06)
 * and its full Set history, then hands both to the pure [ProgressStats]
 * aggregations for the heatmap/Personal Bests/trend. Not unit-tested itself
 * — a thin Room-backed fetch-and-cache layer; [ProgressStats] carries the
 * logic worth testing.
 *
 * Account-holder only for this whole screen — a practical simplification,
 * not a literal reading of every story: stories 26 ("As an Account
 * holder, I want to set a Weekly Target...") and 32 are explicitly
 * Account-only, but 24/25 (Personal Bests, Form Score trend) are phrased
 * generically ("As a user..."). Gating the whole screen behind a non-null
 * `accountId` anyway is because ticket 06 exposes Set history only per
 * Account (`SetRepository.getSetsForAccount`) — there's no equivalent
 * Guest-scoped query this screen could fall back to — and because Streak/
 * Weekly Target genuinely have no meaning for a Guest (ticket 05/06's
 * reasoning) regardless. If a later ticket wants Guests to see their own
 * Personal Bests/trend, it needs a Guest-scoped Set query first, not just a
 * UI change here.
 */
class ProgressViewModel(
    private val accountId: String,
    private val accountRepository: AccountRepository,
    private val setRepository: SetRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    data class UiState(
        val today: LocalDate = LocalDate.now(),
        val weeklyTarget: Int = StreakCalculator.DEFAULT_WEEKLY_TARGET,
        val currentStreak: Int = 0,
        val bankedShields: Int = 0,
        val activeDays: Set<LocalDate> = emptySet(),
        val personalBests: Map<Exercise, PersonalBest> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var lastSets: List<SetEntity> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val account = accountRepository.getAccount(accountId) ?: return@launch
            val sets = setRepository.getSetsForAccount(accountId)
            lastSets = sets
            _uiState.value = UiState(
                today = LocalDate.now(clock),
                weeklyTarget = account.weeklyTarget,
                currentStreak = account.currentStreak,
                bankedShields = account.bankedShields,
                activeDays = ProgressStats.activeDays(sets, clock.zone),
                personalBests = ProgressStats.personalBests(sets),
            )
        }
    }

    /** Story 25's Form Score trend line, for whichever Exercise the UI has selected — computed on demand from the same Set history [refresh] already fetched, not a separate Room query per Exercise. */
    fun formScoreTrend(exercise: Exercise): List<FormScorePoint> = ProgressStats.formScoreTrend(lastSets, exercise, clock.zone)

    /**
     * Story 26: "I want to set a Weekly Target... editable by the Account."
     * Also recomputes the cached Streak/Shields against the *new* target
     * (`AccountRepository.recomputeStreak`) rather than leaving them stale
     * until the next Set is logged — `StreakCalculator.calculate` takes
     * `weeklyTarget` as an input, so changing it changes what the correct
     * Streak/Shields are for the same history, per ticket 04/06's design.
     */
    fun updateWeeklyTarget(newTarget: Int) {
        val clamped = newTarget.coerceIn(MIN_WEEKLY_TARGET, MAX_WEEKLY_TARGET)
        viewModelScope.launch {
            accountRepository.updateWeeklyTarget(accountId, clamped)
            accountRepository.recomputeStreak(accountId, LocalDate.now(clock), clock.zone)
            refresh()
        }
    }

    companion object {
        /** A Weekly Target of 0 would mean "no target," a different feature (and not what "editable" was asked for) — 1 is the practical floor. */
        const val MIN_WEEKLY_TARGET = 1

        /** A week only has 7 days — a Weekly Target above that could never be met. */
        const val MAX_WEEKLY_TARGET = 7
    }
}
