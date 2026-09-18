package com.workoutpartner.app.quickcount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workoutpartner.data.RosterRepository
import com.workoutpartner.data.TrackedProfileEntity

/**
 * The Roster (ticket 11, CONTEXT.md): create/list Tracked Profiles, tap one
 * to start a Quick Count run against them. [accountId] `null` is a Guest's
 * own Roster (`workout-partner-v3` ticket 06, ADR-0007) — works exactly the
 * same as an Account holder's.
 */
@Composable
fun RosterScreen(
    accountId: String?,
    rosterRepository: RosterRepository,
    onProfileSelected: (TrackedProfileEntity) -> Unit,
    onViewHistory: (TrackedProfileEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RosterViewModel = viewModel(
        factory = remember { viewModelFactory { initializer { RosterViewModel(accountId, rosterRepository) } } },
    )
    val roster by viewModel.roster.collectAsState()
    var newProfileName by remember { mutableStateOf("") }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newProfileName,
                    onValueChange = { newProfileName = it },
                    label = { Text("Name") },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { viewModel.createProfile(newProfileName); newProfileName = "" },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Add") }
            }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roster, key = { it.id }) { profile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(profile.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Button(onClick = { onViewHistory(profile) }, modifier = Modifier.padding(end = 8.dp)) { Text("History") }
                            Button(onClick = { onProfileSelected(profile) }) { Text("Quick Count") }
                        }
                    }
                }
            }
        }
    }
}
