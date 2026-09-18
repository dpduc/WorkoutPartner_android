# 03: Before You Start engine + Workout Overview

**What to build:** Picking a Routine now lands on a Workout Overview screen before the first Set, backed by a new pure-Kotlin `BeforeYouStartEngine` (no Android/camera/clock dependency, same pattern as `SessionEngine`) with phases Overview → Form Guides → Position Check → Countdown → Ready. This ticket delivers only the Overview phase and the engine's phase skeleton; Form Guides (ticket 10), Position Check (ticket 12) and Countdown (ticket 13) fill in the later phases. Until those land, "Start" on the Overview proceeds straight into the Session.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `BeforeYouStartEngine` models the full phase sequence (Overview → Form Guides → Position Check → Countdown → Ready) even though only Overview is wired to real UI yet; later tickets fill in the rest without re-architecting the engine.
- [ ] Overview shows: Routine name, estimated duration, difficulty tier, and each Exercise with its difficulty-adjusted rep target (reusing `RoutineDifficulty`).
- [ ] Overview shows safety notes: 2m × 2m clear space, good lighting, fitted clothing.
- [ ] A "Review form" button is always visible on the Overview (wired up for real in ticket 10; may be a disabled/hidden no-op until then).
- [ ] Workout Overview's estimated duration is derived from the difficulty-adjusted steps (assumed seconds-per-rep plus rest intervals).
- [ ] `BeforeYouStartEngineTest` (modelled on `SessionEngineTest`) covers phase sequencing driven by fixture ticks, independent of any UI.
- [ ] Quick Count does not show this Overview (unaffected by this ticket — ticket 14 wires Quick Count into the engine's later phases only).
