package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workoutpartner.data.ActivityLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Collects the body-stats every user answers once — name, age, height,
 * weight, and a daily [ActivityLevel] — right after choosing Guest or
 * completing Sign up (`workout-partner-v2` ticket 01). An existing Account
 * signing back in already has these from its own prior sign-up, so
 * `MainActivity` never routes `AppScreen.SignIn` through this screen.
 *
 * The segmented-button control below is a placeholder: `workout-partner-v3`
 * ticket 07 replaces it with four illustrated cards. This screen only needed
 * to keep compiling once ticket 02 widened [ActivityLevel] to four tiers.
 *
 * [onSubmit] is where the caller decides where the answers land: a Guest's
 * go to `AccountRepository.saveGuestProfile` (claimed onto a real Account
 * later, at sign-up, per [com.workoutpartner.data.AccountRepository.claimGuestData]);
 * a fresh Account's go straight to `AccountRepository.updateProfile`. Same
 * error-handling shape as [AuthForm] in `AuthScreens.kt`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    onSubmit: suspend (name: String, age: Int, heightCm: Int, weightKg: Double, activityLevel: ActivityLevel) -> Unit,
    onDone: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var weightInput by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf(ActivityLevel.LIGHTLY_ACTIVE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val age = ageInput.toIntOrNull()
    val heightCm = heightInput.toIntOrNull()
    val weightKg = weightInput.toDoubleOrNull()
    val isValid = name.isNotBlank() && (age != null && age > 0) && (heightCm != null && heightCm > 0) && (weightKg != null && weightKg > 0)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Tell us about you", style = MaterialTheme.typography.headlineSmall)
            Text(
                "This helps us tune workout difficulty to you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ageInput,
                onValueChange = { ageInput = it.filter(Char::isDigit) },
                label = { Text("Age") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = heightInput,
                onValueChange = { heightInput = it.filter(Char::isDigit) },
                label = { Text("Height (cm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = weightInput,
                onValueChange = { input -> weightInput = input.filter { it.isDigit() || it == '.' } },
                label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text("Daily activity level", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 20.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ActivityLevel.entries.forEachIndexed { index, level ->
                    SegmentedButton(
                        selected = activityLevel == level,
                        onClick = { activityLevel = level },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ActivityLevel.entries.size),
                    ) {
                        Text(level.displayLabel())
                    }
                }
            }
            errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Button(
                onClick = {
                    if (!isValid || age == null || heightCm == null || weightKg == null) return@Button
                    val validAge = age
                    val validHeight = heightCm
                    val validWeight = weightKg
                    errorMessage = null
                    isSubmitting = true
                    scope.launch {
                        try {
                            onSubmit(name.trim(), validAge, validHeight, validWeight, activityLevel)
                            onDone()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Something went wrong — please try again."
                        } finally {
                            isSubmitting = false
                        }
                    }
                },
                enabled = !isSubmitting && isValid,
                modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp)) else Text("Continue")
            }
        }
    }
}

private fun ActivityLevel.displayLabel(): String = when (this) {
    ActivityLevel.SEDENTARY -> "Sedentary"
    ActivityLevel.LIGHTLY_ACTIVE -> "Lightly Active"
    ActivityLevel.ACTIVE -> "Active"
    ActivityLevel.VERY_ACTIVE -> "Very Active"
}
