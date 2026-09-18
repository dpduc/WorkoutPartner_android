package com.workoutpartner.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.ActivityLevel
import com.workoutpartner.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The impure glue for the Settings screen: the Athlete's notification
 * preference ([com.workoutpartner.data.AccountEntity.notificationsEnabled]
 * or, for a Guest, [com.workoutpartner.data.GuestProfileEntity.notificationsEnabled],
 * ticket 12), its body-stats profile (`workout-partner-v2` ticket 01), and
 * signing out ([AuthRepository.signOut]). [accountId] `null` reads/writes
 * the device's single Guest's state instead of an Account's
 * (`workout-partner-v3` ticket 06, ADR-0007) — [signOut] is still never
 * called for a Guest (there's no session to sign out of), which
 * [com.workoutpartner.app.settings.SettingsScreen] enforces by only wiring
 * that button up for an Account holder.
 */
class SettingsViewModel(
    private val accountId: String?,
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val notificationsEnabled: Boolean = true,
        val name: String? = null,
        val age: Int? = null,
        val heightCm: Int? = null,
        val weightKg: Double? = null,
        val activityLevel: ActivityLevel? = null,
        /** True once the Account's real row has loaded — lets the screen seed its editable profile fields exactly once, instead of from this default (pre-load) state. */
        val isLoaded: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val id = accountId
            if (id != null) {
                val account = accountRepository.getAccount(id) ?: return@launch
                _uiState.value = UiState(
                    notificationsEnabled = account.notificationsEnabled,
                    name = account.name,
                    age = account.age,
                    heightCm = account.heightCm,
                    weightKg = account.weightKg,
                    activityLevel = account.activityLevel,
                    isLoaded = true,
                )
            } else {
                val guest = accountRepository.getGuestProfile()
                _uiState.value = UiState(
                    notificationsEnabled = guest?.notificationsEnabled ?: true,
                    name = guest?.name,
                    age = guest?.age,
                    heightCm = guest?.heightCm,
                    weightKg = guest?.weightKg,
                    activityLevel = guest?.activityLevel,
                    isLoaded = true,
                )
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationsEnabled = enabled)
        viewModelScope.launch {
            accountRepository.updateNotificationsEnabled(accountId, enabled)
        }
    }

    fun updateProfile(name: String, age: Int, heightCm: Int, weightKg: Double, activityLevel: ActivityLevel) {
        _uiState.value = _uiState.value.copy(
            name = name, age = age, heightCm = heightCm, weightKg = weightKg, activityLevel = activityLevel,
        )
        viewModelScope.launch {
            val id = accountId
            if (id != null) {
                accountRepository.updateProfile(id, name, age, heightCm, weightKg, activityLevel)
            } else {
                accountRepository.saveGuestProfile(name, age, heightCm, weightKg, activityLevel)
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onDone()
        }
    }
}
