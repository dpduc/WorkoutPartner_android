package com.workoutpartner.app.onboarding

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.workoutpartner.data.AuthRepository
import com.workoutpartner.data.GuestDataResolution
import com.workoutpartner.data.GuestDataSummary
import com.workoutpartner.data.SignInResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The Merge-or-Discard prompt (`workout-partner-v3` ticket 09, wiring ticket
 * 05's [AuthRepository.resolvePendingGuestData] into UI): shown by
 * `AuthScreens.kt`'s shared auth form whenever an email or Google sign-in
 * returns [SignInResult.GuestDataPending] — sign-in has already completed at
 * the gateway by then (see [AuthRepository.resolvePendingGuestData]'s doc
 * comment), so backing out of every step here signs the Athlete back out
 * via [onCancelled] rather than just dismissing a still-pending screen.
 *
 * A four-step flow, not a single dialog, because Merge and Discard need
 * different confirmation shapes: Discard is irreversible, so it asks
 * *before* deleting anything ([Step.ConfirmingDiscard]); Merge is not, so it
 * only needs to report what happened *after* ([Step.Merged]). A failed
 * Merge/Discard ([Step.Failed]) surfaces ticket 05's transactional
 * guarantee — the failure left Guest data intact and unclaimed — with a
 * retry, rather than silently swallowing the error or forcing a full
 * sign-in retry.
 */
@Composable
fun GuestDataPromptDialog(
    pending: SignInResult.GuestDataPending,
    authRepository: AuthRepository,
    onResolved: (accountId: String) -> Unit,
    onCancelled: () -> Unit,
) {
    var step by remember { mutableStateOf<Step>(Step.Choosing) }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val description = pending.summary.describe()

    fun resolve(resolution: GuestDataResolution) {
        isBusy = true
        scope.launch {
            try {
                authRepository.resolvePendingGuestData(pending.accountId, resolution)
                isBusy = false
                // Merge reports what happened afterward (Step.Merged); Discard
                // asked for confirmation already (Step.ConfirmingDiscard, before
                // this call), so it's done the moment the delete succeeds.
                if (resolution == GuestDataResolution.MERGE) {
                    step = Step.Merged
                } else {
                    onResolved(pending.accountId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isBusy = false
                step = Step.Failed(resolution, e.message ?: "Something went wrong.")
            }
        }
    }

    // Guards against the scrim tap / system Back gesture that drives
    // AlertDialog's onDismissRequest — unlike the buttons below, Compose
    // doesn't disable that trigger automatically, so without this a
    // dismiss-while-resolve()-is-in-flight could fire signOut() concurrently
    // with a still-running merge/discard: both onResolved and onCancelled
    // could then fire for the same prompt, or the merge/discard could
    // mutate Account data after the app already navigated away on Cancel.
    fun cancel() {
        if (isBusy) return
        isBusy = true
        scope.launch {
            authRepository.signOut()
            onCancelled()
        }
    }

    when (val current = step) {
        Step.Choosing -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("Merge your Guest data?") },
            text = { Text("This device has $description saved as a Guest. Merge it into this account, or discard it permanently.") },
            confirmButton = { TextButton(onClick = { resolve(GuestDataResolution.MERGE) }, enabled = !isBusy) { Text("Merge") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { step = Step.ConfirmingDiscard }, enabled = !isBusy) { Text("Discard") }
                    TextButton(onClick = ::cancel, enabled = !isBusy) { Text("Cancel") }
                }
            },
        )

        Step.ConfirmingDiscard -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("Discard Guest data?") },
            text = { Text("This will permanently delete $description from this device. This can't be undone.") },
            confirmButton = { TextButton(onClick = { resolve(GuestDataResolution.DISCARD) }, enabled = !isBusy) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { step = Step.Choosing }, enabled = !isBusy) { Text("Back") } },
        )

        Step.Merged -> AlertDialog(
            onDismissRequest = { onResolved(pending.accountId) },
            title = { Text("Added to your account") },
            text = { Text("Added $description to this account.") },
            confirmButton = { TextButton(onClick = { onResolved(pending.accountId) }) { Text("Done") } },
        )

        is Step.Failed -> AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text("Something went wrong") },
            text = { Text("${current.message} Your Guest data hasn't been touched — you can try again.") },
            confirmButton = { TextButton(onClick = { resolve(current.resolution) }, enabled = !isBusy) { Text("Try again") } },
            dismissButton = { TextButton(onClick = ::cancel, enabled = !isBusy) { Text("Cancel") } },
        )
    }
}

private sealed interface Step {
    data object Choosing : Step
    data object ConfirmingDiscard : Step
    data object Merged : Step
    data class Failed(val resolution: GuestDataResolution, val message: String) : Step
}

/** "12 Sessions, 3 Tracked Profiles, and 5 Tallies" — falls back to a generic phrase for the edge case ticket 05 flagged: a Guest pending solely due to non-default [GuestDataSummary]-invisible state (e.g. a customized Weekly Target) reports all-zero counts here. */
private fun GuestDataSummary.describe(): String {
    val parts = buildList {
        if (sessionCount > 0) add(pluralize(sessionCount, "Session"))
        if (trackedProfileCount > 0) add(pluralize(trackedProfileCount, "Tracked Profile"))
        if (tallyCount > 0) add(pluralize(tallyCount, "Tally", "Tallies"))
    }
    return when (parts.size) {
        0 -> "your Guest progress"
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + ", and " + parts.last()
    }
}

private fun pluralize(count: Int, singular: String, plural: String = "${singular}s") = "$count ${if (count == 1) singular else plural}"
