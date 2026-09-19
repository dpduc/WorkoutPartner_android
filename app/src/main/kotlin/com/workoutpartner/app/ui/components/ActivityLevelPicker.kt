package com.workoutpartner.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workoutpartner.data.ActivityLevel

/**
 * Four selectable [ActivityLevel] cards (`workout-partner-v3` ticket 07),
 * shared by [com.workoutpartner.app.onboarding.ProfileSetupScreen] (first
 * answer) and [com.workoutpartner.app.settings.SettingsScreen] (changing it
 * later) so the label/description text and layout can't drift between the
 * two. Replaces the 4-way segmented-button control both screens carried as
 * a placeholder since ticket 02 widened [ActivityLevel] to four tiers —
 * that control listed the tiers but had no room for the plain-language
 * description each card now shows.
 */
@Composable
fun ActivityLevelPicker(selected: ActivityLevel, onSelected: (ActivityLevel) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityLevel.entries.forEach { level ->
            val isSelected = level == selected
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelected(level) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isSelected, onClick = { onSelected(level) })
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(level.displayLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            level.description(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

private fun ActivityLevel.description(): String = when (this) {
    ActivityLevel.SEDENTARY -> "I rarely exercise"
    ActivityLevel.LIGHTLY_ACTIVE -> "A few times a week"
    ActivityLevel.ACTIVE -> "Frequently active"
    ActivityLevel.VERY_ACTIVE -> "Working out is my daily passion"
}
