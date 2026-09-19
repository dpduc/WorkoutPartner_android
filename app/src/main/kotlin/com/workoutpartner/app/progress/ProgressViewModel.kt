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
 * The impure glue for the Progress/Streaks UI (ticket 10): reads the
 * Athlete's Weekly Target/Streak/Shields (ticket 04's Streak Calculator
 * output, cached on [com.workoutpartner.data.AccountEntity] or, for a Guest,
 * [com.workoutpartner.data.GuestProfileEntity] since `workout-partner-v3`
 * ticket 06/ADR-0007) and their full Set history, then hands both to the
 * pure [ProgressStats] aggregations for the heatmap/Personal Bests/trend.
 * Not unit-tested itself — a thin Room-backed fetch-and-cache layer;
 * [ProgressStats] carries the logic worth testing.
 *
 * [accountId] `null` reads/writes the device's single Guest's state instead
 * of an [com.workoutpartner.data.AccountEntity] row — see
 * [AccountRepository.recomputeStreak]/[AccountRepository.updateWeeklyTarget]
 * and [SetRepository.getSetsForAccount] for the same convention.
 */
class ProgressViewModel(
    private val accountId: String?,
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
        val personalBests: Map<TrackedExercise, PersonalBest> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var lastSets: List<SetEntity> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val id = accountId
            val (weeklyTarget, currentStreak, bankedShields) = if (id != null) {
                val account = accountRepository.getAccount(id) ?: return@launch
                Triple(account.weeklyTarget, account.currentStreak, account.bankedShields)
            } else {
                val guest = accountRepository.getGuestProfile()
                Triple(
                    guest?.weeklyTarget ?: StreakCalculator.DEFAULT_WEEKLY_TARGET,
                    guest?.currentStreak ?: 0,
                    guest?.bankedShields ?: 0,
                )
            }
            val sets = setRepository.getSetsForAccount(accountId)
            lastSets = sets
            _uiState.value = UiState(
                today = LocalDate.now(clock),
                weeklyTarget = weeklyTarget,
                currentStreak = currentStreak,
                bankedShields = bankedShields,
                activeDays = ProgressStats.activeDays(sets, clock.zone),
                personalBests = ProgressStats.personalBests(sets),
            )
        }
    }

    /** Story 25's Form Score trend line, for whichever Exercise the UI has selected — computed on demand from the same Set history [refresh] already fetched, not a separate Room query per Exercise. */
    fun formScoreTrend(exercise: Exercise): List<FormScorePoint> = ProgressStats.formScoreTrend(lastSets, exercise, clock.zone)

    /**
     * Story 26: "I want to set a Weekly Target..." — an Account's or, since
     * ticket 06, a Guest's. Also recomputes the cached Streak/Shields
     * against the *new* target (`AccountRepository.recomputeStreak`) rather
     * than leaving them stale until the next Set is logged —
     * `StreakCalculator.calculate` takes `weeklyTarget` as an input, so
     * changing it changes what the correct Streak/Shields are for the same
     * history, per ticket 04/06's design.
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
