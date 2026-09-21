# 08: Step Jack core-rep-counting

**What to build:** A Step Jack Exercise Variant, end-to-end through the rep-counting engine and persistence — its own rep-count threshold, its own Good Set / Form Score judgment, and its own Personal Best, distinct from Jumping Jack's. This ticket has no UI entry point for choosing Step Jack yet (that's ticket 11) — it's verified through the engine's tests and a direct call path (e.g. a debug/test harness invoking a Step Jack Set), plus real persistence so a recorded Step Jack Set actually produces a separate Personal Best.

**Blocked by:** 02 (schema — the `exerciseVariant` column Sets/Tallies persist to).

**Status:** done

- [x] A new `ExerciseVariant` concept in `core-rep-counting`, with `STEP_JACK` as its only value, parented to `JUMPING_JACK`.
- [x] `ExerciseProfiles` and `RepCounter` resolve a profile by Exercise plus optional Variant (extending the current Exercise-only key). Step Jack uses the same elbow–shoulder–hip joints and increasing direction as Jumping Jack, with `repThresholdDegrees = 75` and `formThresholdDegrees = 135`.
- [x] `RoutineStep`/`CompletedSet` carry an optional Variant; a completed Step Jack Set persists its `exerciseVariant` via the ticket 02 column.
- [x] `SessionEngine`'s Good Set judgement uses the Variant's own threshold when one is set.
- [x] `ProgressStats.personalBests` keys Personal Bests by Exercise + Variant, so a Step Jack Set and a Jumping Jack Set produce separate records.
- [x] `RepCounterTest` (using `PoseLandmarkFrameFixtures`): a Step Jack arm sweep to ~80° counts a rep that Jumping Jack would not at the same angle; Form Score uses the 135° threshold.
- [x] `SessionEngineTest`: a Step Jack step judges Good Set against Step Jack's own thresholds, not Jumping Jack's.
- [x] `ProgressStatsTest`: Step Jack and Jumping Jack Sets produce separate Personal Bests.

## Comments

Implemented as spec'd, end to end: `ExerciseVariant` (core-rep-counting) → `ExerciseProfiles`/`RepCounter` resolve by `(Exercise, ExerciseVariant?)` → `RoutineStep`/`CompletedSet` carry the Variant through `SessionEngine` → `SessionViewModel` persists it via `SetRepository.recordSet`'s new `exerciseVariant` param → `ProgressStats.personalBests` keys by it.

`SetEntity.exerciseVariant`/`TallyEntity.exerciseVariant` were narrowed from ticket 02's raw-String placeholder to the real `ExerciseVariant` type (a new pair of Room `TypeConverter`s, no migration needed — both serialize to the same nullable TEXT column, exactly as ticket 02's doc comment anticipated).

The Step Jack-specific `RepCounterTest` coverage lives in a new `RepCounterVariantTest.kt` rather than in `RepCounterTest.kt` itself: that class is `@RunWith(Parameterized::class)` over `Exercise.entries`, and Step Jack isn't a member of that enum (it's an `ExerciseVariant` resolved by an explicit second argument) — folding it into the same parameterization would need restructuring that test's own contract, not just adding a case. Flagged in review (Spec axis) as a location deviation from the literal ticket wording, not a coverage gap — the exact scenarios asked for (an ~80° sweep, the 135° form threshold) are covered, using the same `PoseLandmarkFrameFixtures` helpers.

Caught in review (Standards axis) and fixed before commit: `ProgressStats.personalBests`'s new key was originally a bare `Pair<Exercise, ExerciseVariant?>`, threaded through `ProgressViewModel`/`ProgressScreen` as-is — a real Primitive Obsession/Data Clump (an unnamed `key.displayName()` reading `.first`/`.second`). Replaced with a small named `TrackedExercise(exercise, variant)` type in `ProgressStats.kt`, used consistently by the ViewModel/screen/tests instead.

No UI entry point for choosing Step Jack was added, per this ticket's own scope line — every real Routine step still resolves `variant = null` until ticket 11 wires one in.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fix above came from that review.


> **Update 2026-09-21:** the 135° Step Jack form threshold above was lowered to **110°** (and Jumping Jack's from 150° to 125°) after tuning against real footage; the tests now mention those values. See `ExerciseProfiles.kt`.
