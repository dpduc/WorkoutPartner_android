Status: done

# 04 — Streak Calculator (Seam 2)

## Scope

Pure function, no persistence or clock dependency inside the function itself (inject "today"). Covers user stories 26-31.

- Input: an Account's Active Day history, its Weekly Target (default 3, per `CONTEXT.md`), and an injected "today."
- Output: current Streak count, banked Streak Shield count, and whether the gap-safeguard has fired.
- Rules (see `CONTEXT.md` Streak/Weekly Target/Streak Shield definitions and ADR-0005):
  - Meeting the Weekly Target banks a Streak Shield (banked Shields are capped — pick and document a cap; not specified numerically in the spec).
  - A banked Shield automatically covers one missed week instead of breaking the Streak.
  - Missing the Weekly Target with no Shield banked resets the Streak.
  - The Streak breaks immediately on 3 consecutive days with zero Active Days, regardless of weekly count (the gap-safeguard) — this check is independent of the weekly tally.
  - Multiple Sets/Sessions in one day still count as exactly one Active Day (per `CONTEXT.md`).

## Testing

Per spec's Testing Decisions, unit tests over constructed Active Day history fixtures and an injected "today," covering: target met, target missed with a Shield available, target missed with no Shield, and the gap-safeguard firing mid-week.

## Depends on

Nothing (pure function, independent of tickets 01-03).

## Out of scope

Persistence of Active Days (ticket 06), calendar heatmap / notification UI (tickets 10, 12).

## Comments

Implemented in `core-streaks`: `StreakCalculator.calculate(activeDays, today,
weeklyTarget, shieldCap)` walks forward day by day from the account's first
Active Day through `today`, tracking the gap-safeguard (any 3 consecutive
zero-Active-Day days breaks the Streak and wipes banked Shields immediately,
independent of the weekly tally) and the weekly tally (each Monday-Sunday
week meeting `weeklyTarget` extends the Streak and banks a Shield, capped at
`shieldCap`; a miss is covered by a banked Shield or resets the Streak).
`StreakStatus` bundles the three required outputs (current Streak, banked
Shields, whether the gap-safeguard fired). The in-progress week counts as
met the moment its Active Day count reaches the target, rather than waiting
for the week to end, per spec.md's "Duolingo-style weekly Streak" framing.

Design decisions the spec left unspecified, made here and documented in
`StreakCalculator.kt`'s doc comment rather than left implicit (same spirit
as ticket 02's placeholder angle thresholds): weeks are Monday-Sunday (ISO);
the Shield cap is 4 (roughly one bankable miss a month); a gap-safeguard
break wipes banked Shields along with the Streak count (reasoning: "the
Streak breaks" reads as a full reset, not just the displayed number).

11 tests passing (`./gradlew :core-streaks:test`) covering all four required
scenarios (target met, missed-with-Shield, missed-no-Shield, gap-safeguard
mid-week) plus the Shield cap, early in-week crediting, a pending
(not-yet-failed) in-progress week, and `gapSafeguardFired` clearing once a
later week rebuilds the Streak. Full project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one real bug found
and fixed — the day-by-day walk started at the Monday of the account's
first Active Day's week rather than at that day itself, so an account whose
first-ever Active Day fell mid-week could rack up enough phantom
zero-Active-Day days before it had any history to wrongly trip the
gap-safeguard and void an otherwise-met first week; fixed by starting the
walk exactly at the first Active Day, with a regression test added) and the
repo's ADRs/CONTEXT.md/spec.md (Standards: no hard violations; collapsed a
duplicated `gapFired = false` across two branches into one shared line).

Tickets 06 (repository layer), 10 (progress/streaks UI), and 12 (daily
reminder) depend on this and are now unblocked on the streak-math piece.
