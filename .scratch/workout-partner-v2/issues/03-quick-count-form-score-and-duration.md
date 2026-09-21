Status: done

# 03 — Quick Count: average form score + duration

## Scope

`CONTEXT.md` currently states "Quick Count Tallies never have a Form Score —
they're raw counts," and the code matches: `QuickCountEngine` deliberately
never reads `RepEvent.passedFormThreshold` or calls `FormScore.compute`.
`TallyEntity` has no `formScore` field and no duration/start-time field —
only a single save-time `timestamp`. This ticket reverses that decision (
confirmed with the user): Quick Count should report total reps, total time,
and average form score, matching what a Session's Set already reports.

- `TallyEntity` (`data/src/main/kotlin/com/workoutpartner/data/TallyEntity.kt`):
  add `formScore: Int` and `durationSeconds: Int` columns. Land in the same
  `MIGRATION_2_3`/version-3 bump as ticket 01 if sequenced together, or its
  own follow-up migration otherwise — decide at implementation time based on
  which ticket actually lands first.
- `QuickCountEngine`: stop skipping form-threshold checks — record each
  rep's `passedFormThreshold` the same way `SessionEngine` already does, and
  track a start `Instant`.
- `QuickCountViewModel`: on save, compute duration (elapsed since start) and
  average form score via the existing `FormScore.compute(repEvents)` pure
  function (already used by `SessionEngine`, just not currently invoked
  here), and persist both on the `Tally`.
- `QuickCountScreens.kt`: update the "Tally saved" summary and
  `TallyHistory` screen to show reps, duration (mm:ss), and average form
  score.
- `CONTEXT.md`: update the **Form Score** and **Tally**/**Quick Count**
  glossary entries — remove the "never have a Form Score" line, describe the
  new fields accurately.
- `docs/auth-roadmap.md`'s ERD table and `firestore.rules`/Firestore schema
  (ticket 14): extend to include the new `tallies` columns (and ticket 01's
  new `accounts` columns, if not already covered by that ticket) — this is
  the "Firebase ERD" documentation the user asked to be guided to; it
  already exists from ticket 14 and just needs extending here, not building
  from scratch.

## Testing

- `QuickCountEngineTest`/`FormScoreTest` (existing suites): extend to assert
  a Quick Count run records `passedFormThreshold` per rep and yields a
  correct average.
- Manual: run a Quick Count, confirm the saved Tally shows reps, duration,
  and average form score on both the save screen and Tally History.

## Depends on

None structurally, but naturally sequenced after
`01-user-profile-onboarding.md` if both migrations are combined into one
`MIGRATION_2_3`.

## Out of scope

- Any change to Session/Set form scoring — already correct today.
- Routine/difficulty changes — `02-routine-difficulty-and-format-tags.md`.

## Comments

Implemented as spec'd, combined into ticket 01's `MIGRATION_2_3` since both
landed in the same session (`tallies` gains nullable `formScore`/
`durationSeconds`).

`QuickCountEngine` still never *gates* on form (every Rep counts toward the
total regardless) — only reads `RepEvent.passedFormThreshold` to compute an
average via the same `FormScore.compute` a Session's Sets already use,
exposed as a new `formScore` property and carried on
`QuickCountPhase.Finished`. Duration is the `QuickCountViewModel`'s job, not
the engine's — the engine stays pure/clock-free, so `durationSeconds` is a
separate `StateFlow<Int?>` set alongside the Tally save rather than living
on `QuickCountPhase`. `TallyRepository.recordTally` gained optional
`formScore`/`durationSeconds` params (default `null`, not required — a
Tally recorded before this ticket, or by some future caller that genuinely
doesn't have them, stays honestly incomplete rather than fabricated).

UI: the "Tally saved" screen and `TallyHistoryScreen` both show reps,
Form Score, and time (mm:ss) — `TallyHistoryScreen` omits the line entirely
for historical Tallies where both are null, rather than showing a
misleading "0".

Docs: `CONTEXT.md`'s Form Score/Tally/Quick Count entries updated to drop
the "never have a Form Score" claim; `firestore.rules`' `tallies` match
block and `TallyEntity.toFirestoreMap()` both extended with the two new
nullable fields; `docs/auth-roadmap.md`'s ERD table extended to cover all
of `workout-partner-v2`'s schema changes (this ticket's and tickets 01/02's).

Tests: `QuickCountEngineTest` gained two `formScore` cases and updated
`Finished` equality checks; `TallyRepositoryTest` gained two cases
(supplied vs. defaulted-null). Full `data`+`app` unit test suites pass.

Not done in this session: manual on-device verification of a live Quick
Count run's saved Tally/history display.
