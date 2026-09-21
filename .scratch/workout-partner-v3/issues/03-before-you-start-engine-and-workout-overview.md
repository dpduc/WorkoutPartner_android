# 03: Before You Start engine + Workout Overview

**What to build:** Picking a Routine now lands on a Workout Overview screen before the first Set, backed by a new pure-Kotlin `BeforeYouStartEngine` (no Android/camera/clock dependency, same pattern as `SessionEngine`) with phases Overview → Form Guides → Position Check → Countdown → Ready. This ticket delivers only the Overview phase and the engine's phase skeleton; Form Guides (ticket 10), Position Check (ticket 12) and Countdown (ticket 13) fill in the later phases. Until those land, "Start" on the Overview proceeds straight into the Session.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `BeforeYouStartEngine` models the full phase sequence (Overview → Form Guides → Position Check → Countdown → Ready) even though only Overview is wired to real UI yet; later tickets fill in the rest without re-architecting the engine.
- [x] Overview shows: Routine name, estimated duration, difficulty tier, and each Exercise with its difficulty-adjusted rep target (reusing `RoutineDifficulty`).
- [x] Overview shows safety notes: 2m × 2m clear space, good lighting, fitted clothing.
- [x] A "Review form" button is always visible on the Overview (wired up for real in ticket 10; may be a disabled/hidden no-op until then).
- [x] Workout Overview's estimated duration is derived from the difficulty-adjusted steps (assumed seconds-per-rep plus rest intervals).
- [x] `BeforeYouStartEngineTest` (modelled on `SessionEngineTest`) covers phase sequencing driven by fixture ticks, independent of any UI.
- [x] Quick Count does not show this Overview (unaffected by this ticket — ticket 14 wires Quick Count into the engine's later phases only).

## Comments

Implemented as spec'd. New `AppScreen.WorkoutOverview(routine)` sits between `RoutinePicker` and `Session` in `MainActivity`'s navigation; `RoutinePickerScreen.onRoutineSelected` now routes there instead of straight to `Session`.

One thing changed from the first pass, caught in review: the Overview screen originally held a `BeforeYouStartEngine` instance and called `skipToReady()` on "Start," but nothing ever read `engine.phase` — the screen's behavior was identical whether or not the engine was there, which is exactly the "inserted for a future ticket's benefit, not this one's" smell. Removed that wiring; "Start" now calls `onStart()` directly. `BeforeYouStartEngine` itself stays, fully tested via `BeforeYouStartEngineTest`, ready for ticket 10 to actually drive once Form Guides has content to show — the engine existing and being correct doesn't require a screen to hold an unused instance of it in the meantime.

Also renamed `WorkoutDurationEstimator` → `RoutineDurationEstimator` (review flagged "Workout" as one of CONTEXT.md's explicit avoid-terms — it's estimating a Routine's duration), and extracted a small `humanizeEnumName` helper to deduplicate the display-casing logic that had been repeated for the difficulty tier and each Exercise label.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fixes above came from that review.
