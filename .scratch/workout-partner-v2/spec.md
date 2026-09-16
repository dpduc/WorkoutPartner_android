# Workout Partner v2 — Profile, tuned Routines, Quick Count scoring

`workout-partner-v1`'s ticket list (01–15) closed out v1 scope (ticket 14's
comments). This directory covers the next round of feature work, started
alongside `workout-partner-v1/issues/15-local-auth-fallback.md` (which stays
filed under v1 since it was already speced there).

## Goals

1. Collect a body-stats profile (name, age, height, weight, a 3-tier daily
   activity level) from every user at first use — Guest or Account holder
   alike — so routines can be tuned to the person doing them.
2. Restructure the app's home screen into two top-level sections: pre-built
   Routines (tagged HIIT/Tabata/AMRAP, difficulty-tuned from the profile) and
   Quick Count.
3. Give Quick Count the same reps/duration/form-score reporting a Session's
   Sets already have, reversing the current "Quick Count Tallies never have a
   Form Score" decision recorded in `CONTEXT.md`.

## Non-goals

- A real timer/round-based interval engine for HIIT/Tabata/AMRAP (true work/
  rest intervals, round counting). Routines keep using the existing
  rep-target + rest-interval `SessionEngine` state machine; format is a
  label/tag only in this round. Filed separately as a `needs-triage`
  placeholder ticket (`04-interval-timer-engine.md`) so it isn't lost.
- Editing/authoring custom Routines — still seeded, bundled content only,
  per `workout-partner-v1`'s existing out-of-scope note.
- Applying the `com.google.gms.google-services` Gradle plugin or provisioning
  a production Firebase project — that's the user's own manual step per
  `docs/auth-roadmap.md` §3.1, unrelated to this feature set.

## Tickets

- `01-user-profile-onboarding.md` — Account/Guest profile fields, capture UI,
  Guest→Account migration extension.
- `02-routine-difficulty-and-format-tags.md` — two-section main menu, Routine
  format tags, BMI+age-band difficulty tuning.
- `03-quick-count-form-score-and-duration.md` — Tally gains `formScore` and
  `durationSeconds`, `CONTEXT.md`/ERD doc updates.
- `04-interval-timer-engine.md` — deferred, `needs-triage`.

## Depends on

`workout-partner-v1/issues/15-local-auth-fallback.md` — ticket 01's
Guest→Account profile claim needs a working `authRepository.signUp` path,
which is currently blocked until `LocalAuthGateway` lands (ticket 15).
