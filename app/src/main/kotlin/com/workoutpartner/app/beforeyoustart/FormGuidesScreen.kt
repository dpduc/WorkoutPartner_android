package com.workoutpartner.app.beforeyoustart

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.workoutpartner.app.progress.TrackedExercise
import kotlinx.coroutines.launch

/**
 * Swipeable Form Guide pager (`workout-partner-v3` ticket 10): a picture,
 * starting position, correct-form cues, common mistakes, and what the
 * tracker checks, per [TrackedExercise] in [guides] — with a "2 of 4"-style
 * position indicator.
 *
 * Used for both cases the ticket describes, told apart only by what the
 * caller passes: the automatic before-Session gate ([guides] being only
 * the Routine's *unseen* guides, per [BeforeYouStartEngine]) and "Review
 * form" on the Overview ([guides] being every guide in the Routine
 * regardless of seen-state). This screen doesn't know which case it's in.
 *
 * Marks each guide seen the moment its page is actually displayed, not
 * only once the Athlete reaches the end — swiping partway through and
 * backing out still "counts" for the pages actually viewed, matching the
 * ticket's per-guide "won't be forced... again" wording rather than an
 * all-or-nothing completion.
 */
@Composable
fun FormGuidesScreen(
    guides: List<TrackedExercise>,
    formGuidePrefs: FormGuidePrefs,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { guides.size })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        formGuidePrefs.markSeen(guides[pagerState.currentPage])
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "${pagerState.currentPage + 1} of ${guides.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                FormGuidePage(FormGuides.resourcesFor(guides[page]).resolve(), modifier = Modifier.fillMaxSize())
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    enabled = pagerState.currentPage > 0,
                ) { Text("Back") }

                if (pagerState.currentPage == guides.lastIndex) {
                    Button(onClick = onDone) { Text("Continue") }
                } else {
                    Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun FormGuidePage(content: FormGuideContent, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Image(
            painter = painterResource(content.imageRes),
            contentDescription = content.name,
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        Text(content.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))

        Text("Starting position", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(content.startingPosition, style = MaterialTheme.typography.bodyMedium)

        Text("Cues", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        content.cues.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }

        Text("Common mistakes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        content.mistakes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }

        Text("What the tracker checks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
        Text(content.trackerCheck, style = MaterialTheme.typography.bodyMedium)
    }
}
