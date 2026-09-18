# 04: Distance-friendly Session screen

**What to build:** Rework the Session's Tracking phase layout so an Athlete standing ~2m from the phone can read it: a huge rep count, a large Exercise (or Exercise Variant) name, and a wide progress bar toward the rep target. Independent of the rest of the Before You Start work — no engine or audio changes here, just the tracking screen's layout.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [x] Rep count renders at ≈120sp.
- [x] Exercise (or Variant) name renders at ≈48sp.
- [x] A full-width progress bar shows progress toward the rep target.
- [x] Secondary info (e.g. rest countdown, Set number) renders at ≈28sp.
- [ ] Manually verified readable at arm's length and from across a room; no regression to existing Session functionality (pause, rest, Set completion still work).

## Comments

Implemented as spec'd — `TrackingContent` in `SessionScreens.kt` now shows Set position, Exercise name, rep count, a full-width `LinearProgressIndicator`, and the target rep count, at the sizes above. Rest countdown/Set-number in other phases (Countdown, Resting) were left untouched — this ticket's own scope line is "the Session's Tracking phase layout" specifically, not the other phases.

One thing changed from the first pass, caught in review: the target rep count was originally recomputed in the Composable via `RoutineDifficulty.adjustedTargetReps(...)`, duplicating a computation `SessionViewModel` already does when building the engine's steps — two independent call sites that could silently drift if the scaling formula ever changed. Fixed by adding `targetReps` directly onto `SessionPhase.Tracking` (`SessionEngine.kt`), populated from the engine's own already-adjusted `RoutineStep.targetReps`, so the UI reads it rather than recomputing it. Also switched the new text from bare `fontSize`/`fontWeight` to `MaterialTheme.typography.*.copy(fontSize = ...)`, matching how every other `Text()` in the file is styled.

**Not manually verified** — no device or emulator is available in this environment. Verified instead via `:app:compileDebugKotlin` and the full `:app:testDebugUnitTest` suite (SessionEngine/SessionViewModel logic is unchanged, only the Compose layout and the new `Tracking.targetReps` field). Flagging this gap explicitly rather than claiming the on-device check happened — a human should confirm readability at distance before this ships.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fix above came from that review.
