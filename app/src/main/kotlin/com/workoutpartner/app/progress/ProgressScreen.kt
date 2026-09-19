package com.workoutpartner.app.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.core.repcounting.Exercise
import com.workoutpartner.data.AccountRepository
import com.workoutpartner.data.SetRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** "SIT_UP" -> "sit up" — shared by every place this screen shows an Exercise's name. */
private fun Exercise.displayName(): String = name.lowercase().replace('_', ' ')

/** A [TrackedExercise]'s Exercise Variant name (`workout-partner-v3` ticket 08) if it has one, otherwise its Exercise's — CONTEXT.md: "scored and ranked separately from its parent Exercise." */
private fun TrackedExercise.displayName(): String = variant?.name?.lowercase()?.replace('_', ' ') ?: exercise.displayName()

/** How many weeks of Active Days the heatmap shows — not spec'd precisely ("a calendar heatmap"), a reasonable default. */
private const val HEATMAP_WEEKS = 12

/**
 * Weekly Target/Streak/Shields, a calendar heatmap of Active Days, Personal
 * Bests, and a Form Score trend per Exercise (ticket 10, spec.md user
 * stories 24-26, 32). Works fully for a Guest too, since `workout-partner-v3`
 * ticket 06 (ADR-0007) — [accountId] `null` just points [ProgressViewModel]
 * at the device's single Guest's state instead of an Account's.
 */
@Composable
fun ProgressScreen(
    accountId: String?,
    accountRepository: AccountRepository,
    setRepository: SetRepository,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProgressViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { ProgressViewModel(accountId, accountRepository, setRepository) } } },
    )
    val state by viewModel.uiState.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { StreakCard(state.currentStreak, state.bankedShields, state.weeklyTarget, onWeeklyTargetChange = viewModel::updateWeeklyTarget) }
            item { ActiveDayHeatmap(state.today, state.activeDays) }
            item { PersonalBestsSection(state.personalBests) }
            // formScoreTrend is still keyed by Exercise alone (ticket 08 only
            // asked personalBests to split by Variant) — distinct() so an
            // Exercise with both a standard and a Variant Personal Best
            // doesn't render the same combined trend section twice.
            items(state.personalBests.keys.map { it.exercise }.distinct()) { exercise ->
                FormScoreTrendSection(exercise, viewModel.formScoreTrend(exercise))
            }
        }
    }
}

@Composable
private fun StreakCard(currentStreak: Int, bankedShields: Int, weeklyTarget: Int, onWeeklyTargetChange: (Int) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Streak: $currentStreak weeks", style = MaterialTheme.typography.titleLarge)
            Text("Streak Shields banked: $bankedShields")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Weekly Target: $weeklyTarget Active Days")
                Button(
                    onClick = { onWeeklyTargetChange(weeklyTarget - 1) },
                    enabled = weeklyTarget > ProgressViewModel.MIN_WEEKLY_TARGET,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text("-") }
                Button(
                    onClick = { onWeeklyTargetChange(weeklyTarget + 1) },
                    enabled = weeklyTarget < ProgressViewModel.MAX_WEEKLY_TARGET,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("+") }
            }
        }
    }
}

@Composable
private fun ActiveDayHeatmap(today: LocalDate, activeDays: Set<LocalDate>, modifier: Modifier = Modifier) {
    // Oldest day first, HEATMAP_WEEKS*7 days ending today — "today" comes
    // from the ViewModel's injected clock/zone (the same one activeDays was
    // bucketed with), not read fresh from the system here, so the two never
    // disagree about which zone/day "today" means.
    val days = remember(today, activeDays) {
        val start = today.minusDays((HEATMAP_WEEKS * 7L) - 1)
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    }
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier) {
        Text("Active Days (last $HEATMAP_WEEKS weeks)", style = MaterialTheme.typography.titleMedium)
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp)) {
            val weeks = HEATMAP_WEEKS
            val cellWidth = size.width / weeks
            val cellHeight = size.height / 7f
            days.forEach { day ->
                val weekIndex = ChronoUnit.WEEKS.between(days.first(), day).toInt().coerceIn(0, weeks - 1)
                val dayOfWeekIndex = day.dayOfWeek.value - 1 // 0=Monday..6=Sunday
                drawRect(
                    color = if (day in activeDays) activeColor else inactiveColor,
                    topLeft = Offset(weekIndex * cellWidth, dayOfWeekIndex * cellHeight),
                    size = Size(cellWidth * 0.85f, cellHeight * 0.85f),
                )
            }
        }
    }
}

@Composable
private fun PersonalBestsSection(personalBests: Map<TrackedExercise, PersonalBest>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Personal Bests", style = MaterialTheme.typography.titleMedium)
        if (personalBests.isEmpty()) {
            Text("Complete a Set to start tracking Personal Bests.", modifier = Modifier.padding(top = 8.dp))
        }
        personalBests.forEach { (tracked, best) ->
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(tracked.displayName(), style = MaterialTheme.typography.bodyLarge)
                    Text("Best reps: ${best.bestReps} — Best Form Score: ${best.bestFormScore}")
                }
            }
        }
    }
}

@Composable
private fun FormScoreTrendSection(exercise: Exercise, points: List<FormScorePoint>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("${exercise.displayName()} Form Score trend", style = MaterialTheme.typography.titleMedium)
        if (points.size < 2) {
            Text("Complete a few more Sets to see a trend.", modifier = Modifier.padding(top = 8.dp))
            return
        }
        val lineColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp).padding(top = 8.dp)) {
            val stepX = size.width / (points.size - 1)
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = index * stepX
                val y = size.height * (1f - point.formScore / 100f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = lineColor, style = Stroke(width = 4f))
        }
    }
}
