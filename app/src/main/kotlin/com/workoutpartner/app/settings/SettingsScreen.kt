package com.workoutpartner.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.app.speech.SpeechPrefs
import com.workoutpartner.app.ui.components.ActivityLevelPicker
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.ActivityLevel
import com.workoutpartner.data.AuthRepository

/**
 * Profile/settings entry point: who's signed in, the Athlete's editable
 * body-stats (`workout-partner-v2` ticket 01 — name/age/height/weight/
 * activity level, first collected at onboarding), the daily reminder
 * notification toggle (ticket 12's [com.workoutpartner.data.AccountEntity.notificationsEnabled]
 * or, for a Guest, [com.workoutpartner.data.GuestProfileEntity.notificationsEnabled]),
 * and the Weekly Target shortcut (the same control [com.workoutpartner.app.progress.ProgressScreen]
 * already exposes — Progress is where it lives, this just points there). All
 * of it works fully for a Guest too, since `workout-partner-v3` ticket 06
 * (ADR-0007) — [accountId] `null` just points [SettingsViewModel] at the
 * device's single Guest's state instead of an Account's. The one thing that
 * differs by [accountId]: an Account holder sees "Sign out"; a Guest sees a
 * "Back up your progress" entry (backup framing, not "unlock more
 * features" — every feature already works without one) that surfaces
 * Sign up/Sign in instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accountId: String?,
    accountRepository: AccountRepository,
    authRepository: AuthRepository,
    onSignedOut: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onViewProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { SettingsViewModel(accountId, accountRepository, authRepository) } } },
    )
    val state by viewModel.uiState.collectAsState()

    // Device-local, not Account data (`workout-partner-v3` ticket 13) — same for a Guest and an Account holder.
    val speechPrefs = remember { SpeechPrefs(context) }
    var spokenPromptsEnabled by remember { mutableStateOf(speechPrefs.spokenPromptsEnabled) }

    var name by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(ActivityLevel.LIGHTLY_ACTIVE) }

    // Seeds the editable fields from the Account's real row exactly once,
    // when it finishes loading (state.isLoaded flips false -> true) —
    // seeding from every recomposition would stomp on in-progress edits.
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded) {
            name = state.name ?: ""
            ageInput = state.age?.toString() ?: ""
            heightInput = state.heightCm?.toString() ?: ""
            weightInput = state.weightKg?.toString() ?: ""
            activityLevel = state.activityLevel ?: ActivityLevel.LIGHTLY_ACTIVE
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Profile", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { ageInput = it.filter(Char::isDigit) },
                        label = { Text("Age") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { heightInput = it.filter(Char::isDigit) },
                        label = { Text("Height (cm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { input -> weightInput = input.filter { it.isDigit() || it == '.' } },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text("Daily activity level", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    ActivityLevelPicker(
                        selected = activityLevel,
                        onSelected = { activityLevel = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val age = ageInput.toIntOrNull()
                    val heightCm = heightInput.toIntOrNull()
                    val weightKg = weightInput.toDoubleOrNull()
                    val isValid = name.isNotBlank() && (age != null && age > 0) && (heightCm != null && heightCm > 0) && (weightKg != null && weightKg > 0)
                    Button(
                        onClick = {
                            if (isValid && age != null && heightCm != null && weightKg != null) {
                                viewModel.updateProfile(name.trim(), age, heightCm, weightKg, activityLevel)
                            }
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Save profile") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Daily reminder")
                    Switch(checked = state.notificationsEnabled, onCheckedChange = viewModel::setNotificationsEnabled)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Spoken prompts")
                    Switch(
                        checked = spokenPromptsEnabled,
                        onCheckedChange = {
                            spokenPromptsEnabled = it
                            speechPrefs.spokenPromptsEnabled = it
                        },
                    )
                }
            }
            Button(onClick = onViewProgress, modifier = Modifier.fillMaxWidth()) { Text("Weekly Target & Progress") }
            if (accountId != null) {
                Button(onClick = { viewModel.signOut(onSignedOut) }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Back up your progress", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Everything here is saved on this device only. Sign up (or sign in) to keep it permanently and sync it across devices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onSignUp, modifier = Modifier.weight(1f)) { Text("Sign up") }
                            Button(onClick = onSignIn, modifier = Modifier.weight(1f)) { Text("Sign in") }
                        }
                    }
                }
            }
        }
    }
}
