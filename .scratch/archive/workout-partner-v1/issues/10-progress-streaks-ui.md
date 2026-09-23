Status: done

# 10 — Progress / Streaks UI

## Scope

Covers user stories 24, 25, 26, 32 (33 is the notification itself — ticket 12).

- Weekly Target setting (default 3 Active Days per `CONTEXT.md`), editable by the Account.
- Calendar heatmap of Active Days.
- Personal Bests per Exercise: top rep count and Form Score over time.
- Form Score trend line per Exercise over time.
- Surfaces current Streak and banked Streak Shield count from ticket 04's calculator, backed by Active Day history from ticket 06.

## Depends on

Ticket 04 (Streak Calculator), ticket 06 (repository layer, for Active Day/Set history).

## Out of scope

The daily reminder notification itself (ticket 12), Session-in-progress screens (ticket 09).

## Comments

Implemented in `app`: pure `ProgressStats` (Active Days, Personal Bests —
best reps and best Form Score tracked independently per Exercise, per
CONTEXT.md — and Form Score trend, all folded over the Account's full Set
history, not a snapshot) with its own unit tests, no Robolectric needed
(plain data transformation over `SetEntity`, no Room engine touched).
`ProgressViewModel` is the impure fetch-and-cache layer (same pure/impure
split as ticket 09's `SessionEngine`/`SessionViewModel`). `ProgressScreen`
adds a Weekly Target stepper, a Canvas-drawn Active Day heatmap (last 12
weeks), Personal Bests cards, and a simple Canvas line per Exercise's Form
Score trend — reachable from a new "Progress" button on the Routine
picker's top bar.

Account-holder only for the whole screen (a Guest sees a plain message
instead) — a practical simplification since ticket 06 only exposes Set
history per Account, not per-Guest, and Streak/Weekly Target have no
meaning for a Guest regardless (ticket 05/06's reasoning); documented as
such, including that stories 24/25 (Personal Bests/trend) are phrased
generically rather than "Account holder" like 26/32 are — an earlier draft's
doc comment overclaimed all of 24-32 were Account-only, corrected after
review.

4 tests passing (`./gradlew :app:test`, 17 total in the module):
`activeDays` collapsing same-day Sets, `personalBests` tracking best-reps
and best-form-score independently and only for attempted Exercises, and
`formScoreTrend` filtering + chronological ordering. Full project
build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one real staleness
bug found and fixed — `updateWeeklyTarget` changed the stored target but
never called `AccountRepository.recomputeStreak`, leaving the displayed
Streak/Shields computed against the *old* target until the next Set was
logged, even though `StreakCalculator.calculate` takes `weeklyTarget` as an
input per ticket 04/06's design; also corrected the doc-comment overclaim
above) and the repo's ADRs/CONTEXT.md/spec.md (Standards: no hard
violations; fixed the heatmap's "today" being read from the system clock
independently of the ViewModel's injected clock/zone — now threaded through
`UiState` so they can't disagree; added a named min/max (1-7) for the
Weekly Target stepper instead of an unbounded increment and a bare magic
`1`; extracted a shared `Exercise.displayName()` instead of duplicating the
same formatting twice; cleaned up inline fully-qualified Compose type
references left over from a first draft).

Ticket 12 (daily reminder) can now read the same Account/Streak data this
screen surfaces.
