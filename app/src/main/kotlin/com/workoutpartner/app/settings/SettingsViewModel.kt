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
 * The impure glue for the Settings screen: the Account's notification
 * preference ([com.workoutpartner.data.AccountEntity.notificationsEnabled],
 * ticket 12), its body-stats profile (`workout-partner-v2` ticket 01), and
 * signing out ([AuthRepository.signOut]). Account-holder only, same gating
 * as [com.workoutpartner.app.progress.ProgressViewModel] — a Guest has
 * neither a notification preference nor a session to sign out of, so
 * [com.workoutpartner.app.settings.SettingsScreen] never constructs this
 * for a Guest.
 */
class SettingsViewModel(
    private val accountId: String,
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
            val account = accountRepository.getAccount(accountId) ?: return@launch
            _uiState.value = UiState(
                notificationsEnabled = account.notificationsEnabled,
                name = account.name,
                age = account.age,
                heightCm = account.heightCm,
                weightKg = account.weightKg,
                activityLevel = account.activityLevel,
                isLoaded = true,
            )
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
            accountRepository.updateProfile(accountId, name, age, heightCm, weightKg, activityLevel)
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onDone()
        }
    }
}
