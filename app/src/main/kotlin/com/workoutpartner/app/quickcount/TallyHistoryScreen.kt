package com.workoutpartner.app.quickcount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.workoutpartner.data.TallyEntity
import com.workoutpartner.data.TallyRepository
import com.workoutpartner.data.TrackedProfileEntity

/**
 * A Tracked Profile's Tally history (spec.md story 39: "each Quick Count run
 * saved as a Tally against the Tracked Profile, so that I can review
 * someone's history later") — the Account holder's own view of it, not the
 * Tracked Profile's own (explicitly out of this ticket's scope: "Any
 * Tracked Profile self-visibility... flow").
 */
@Composable
fun TallyHistoryScreen(profile: TrackedProfileEntity, tallyRepository: TallyRepository, modifier: Modifier = Modifier) {
    var tallies by remember { mutableStateOf<List<TallyEntity>>(emptyList()) }
    LaunchedEffect(profile.id) { tallies = tallyRepository.getTalliesForTrackedProfile(profile.id) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("${profile.displayName}'s history", style = MaterialTheme.typography.titleLarge)
            if (tallies.isEmpty()) {
                Text("No Quick Count runs yet.", modifier = Modifier.padding(top = 16.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tallies, key = { it.id }) { tally ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(tally.exercise.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodyLarge)
                            Text(if (tally.target != null) "${tally.repsAchieved} / ${tally.target} reps" else "${tally.repsAchieved} reps")
                        }
                    }
                }
            }
        }
    }
}
