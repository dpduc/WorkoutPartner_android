package com.workoutpartner.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.ActivityLevel
import com.workoutpartner.data.AuthRepository

/**
 * Profile/settings entry point: who's signed in, the Account's editable
 * body-stats (`workout-partner-v2` ticket 01 — name/age/height/weight/
 * activity level, first collected at onboarding), the daily reminder
 * notification toggle (ticket 12's [com.workoutpartner.data.AccountEntity.notificationsEnabled]),
 * the Weekly Target shortcut (the same control [com.workoutpartner.app.progress.ProgressScreen]
 * already exposes — Progress is where it lives, this just points there), and
 * Sign out. A Guest sees a plain message and a Sign up prompt instead — same
 * "no Account, nothing Account-shaped to show" gating as [com.workoutpartner.app.progress.ProgressScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    accountId: String?,
    accountRepository: AccountRepository,
    authRepository: AuthRepository?,
    onSignedOut: () -> Unit,
    onSignUp: () -> Unit,
    onViewProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accountId == null || authRepository == null) {
        Surface(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You're using Guest mode — sign up for an Account to set a Weekly Target and daily reminders.")
                    Button(onClick = onSignUp, modifier = Modifier.padding(top = 16.dp)) { Text("Sign up") }
                }
            }
        }
        return
    }

    val viewModel: SettingsViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { SettingsViewModel(accountId, accountRepository, authRepository) } } },
    )
    val state by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(ActivityLevel.MEDIUM) }

    // Seeds the editable fields from the Account's real row exactly once,
    // when it finishes loading (state.isLoaded flips false -> true) —
    // seeding from every recomposition would stomp on in-progress edits.
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded) {
            name = state.name ?: ""
            ageInput = state.age?.toString() ?: ""
            heightInput = state.heightCm?.toString() ?: ""
            weightInput = state.weightKg?.toString() ?: ""
            activityLevel = state.activityLevel ?: ActivityLevel.MEDIUM
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
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ActivityLevel.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = activityLevel == level,
                                onClick = { activityLevel = level },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ActivityLevel.entries.size),
                            ) {
                                Text(level.name.lowercase().replaceFirstChar(Char::uppercase))
                            }
                        }
                    }
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
            Button(onClick = onViewProgress, modifier = Modifier.fillMaxWidth()) { Text("Weekly Target & Progress") }
            Button(onClick = { viewModel.signOut(onSignedOut) }, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
        }
    }
}
