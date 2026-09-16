Status: ready-for-agent

# 02 — Two-section main menu, Routine format tags, BMI/age difficulty tuning

## Scope

Today `RoutinePicker` (defined inline in `MainActivity.kt`'s
`WorkoutPartnerApp`) doubles as the app's home screen, with Progress/
Roster-or-SignUp/Settings tucked behind a `MainMenu` overflow dropdown.
Routines are a flat, hardcoded 3-item list (`BundledRoutines.kt`) with no
format or difficulty concept anywhere — `RoutineEntity` is just `id`/`name`,
and `SessionEngine`'s rep-target + rest-interval state machine has no
timer/round-based execution mode.

- New `AppScreen.MainMenu` as the true home screen: two tiles, "Workouts"
  (-> existing `RoutinePicker`) and "Quick Count" (-> existing Roster/
  Quick-Count-setup flow, internals unchanged, just promoted to a top-level
  tile instead of living behind the overflow menu -> Roster path). The
  existing overflow dropdown (Progress/Settings/Sign up) moves to
  `MainMenu`'s top bar.
- New `RoutineFormat` enum (`HIIT`/`TABATA`/`AMRAP`/`STANDARD`) as a column
  on `RoutineEntity` — **label only**, per this round's scope: no new timer/
  round execution model. `SessionEngine`'s existing rep-target + rest state
  machine is unchanged; a real interval engine is deferred
  (`04-interval-timer-engine.md`, `needs-triage`). Extend
  `BundledRoutines.kt` with format-tagged variants covering all three
  formats (retag the existing 3 and/or add more).
- New pure function `RoutineDifficulty.compute(account): DifficultyTier` —
  new file, e.g. `app/src/main/kotlin/com/workoutpartner/app/routines/RoutineDifficulty.kt`.
  BMI from `heightCm`/`weightKg` (ticket 01) -> category, combined with an
  age band, -> `EASY`/`STANDARD`/`CHALLENGING` tier -> a rep/rest multiplier.
  Applied at session-start time when `SessionViewModel` converts
  `RoutineStepEntity` rows into engine `RoutineStep`s — **not** by mutating
  the seeded `RoutineEntity`/`RoutineStepEntity` rows, which stay "seeded
  content, not user-editable" per `RoutineEntity`'s existing doc comment.
- `RoutinePicker` UI: show each routine's format tag and the account's
  computed difficulty tier as badges.

## Testing

- `RoutineDifficultyTest`: BMI category + age band -> tier mapping, table of
  representative inputs (underweight/normal/overweight x young/mid/older).
- Manual: confirm the Main Menu's two tiles route correctly, a tagged
  Routine shows its format chip, and the same Routine's rep targets visibly
  scale for two different test Accounts (e.g. a younger normal-BMI profile
  vs. an older higher-BMI profile).

## Depends on

`01-user-profile-onboarding.md` — needs `AccountEntity.heightCm`/`weightKg`/
`age` to exist.

## Out of scope

- Real HIIT/Tabata/AMRAP timer/round execution — `04-interval-timer-engine.md`.
- Quick Count changes — `03-quick-count-form-score-and-duration.md`.

## Comments

Implemented as spec'd. `RoutineFormat` (new enum, `data` module) landed as
`RoutineEntity.format` via its own `MIGRATION_3_4` (version 3 -> 4, kept
separate from ticket 01/03's `MIGRATION_2_3` rather than folded in, so each
ticket's schema change stays independently traceable) — existing rows and
the column's own default both read `STANDARD`. `BundledRoutines.kt`'s three
routines are tagged HIIT / TABATA / AMRAP respectively, covering all three
formats without adding new bundled routines.

`RoutineDifficulty` (new file, `app/.../routines/`) computes a
`DifficultyTier` from a `BodyStats` (age/heightCm/weightKg — deliberately
not `AccountEntity` directly, since a Guest has the same three fields on
`GuestProfileEntity` instead, per ticket 01's "collect for everyone"
decision) via BMI category + age band. Falls back to `STANDARD` whenever
any field is missing (no fabricated tuning from partial data). Applied by
scaling `targetReps`/`restIntervalSeconds` when `SessionViewModel` builds
its engine steps — the seeded `RoutineEntity`/`RoutineStepEntity` rows
themselves are never mutated.

`AppScreen.MainMenu` is the new home screen (two tiles: Workouts ->
`RoutinePicker`, Quick Count -> `Roster` or `SignUp` depending on
Guest-vs-Account, same gating the old overflow-menu branch had).
`RoutinePicker` is now a sub-screen with its own Back button; the overflow
menu (renamed `OverflowMenu`, Progress/Settings only) moved to
`MainMenu`'s top bar. Every "return to home" back-target in
`MainActivity.kt` (Settings, Progress, Roster, Sign-out, post-onboarding)
now points at `AppScreen.MainMenu`; Session's own "Done" still returns to
`RoutinePicker` (picking another workout is the more natural next action
right after finishing one) — a deliberate, narrow exception, not an
oversight.

`MainActivity` now loads `BodyStats` (from the signed-in Account or, for a
Guest, `GuestProfileEntity`) into `RoutineDifficulty.compute`, re-fetched on
`accountId` changes; `ProfileSetup`'s `onSubmit` also updates it directly
(the `accountId`-keyed reload doesn't re-fire for a Guest, since `accountId`
stays null before and after that screen).

Tests: `MigrationTest` gained a `MIGRATION_3_4` case; new
`RoutineDifficultyTest` (9 cases: fallback behavior, BMI+age -> tier
mapping, rep/rest scaling, floor-at-1, zero-rest-stays-zero). Full
`data`+`app` unit test suites pass.

Not done in this session: manual on-device verification of the new Main
Menu/Workouts navigation and visible difficulty scaling.
