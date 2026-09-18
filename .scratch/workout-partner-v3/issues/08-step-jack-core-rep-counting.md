# 08: Step Jack core-rep-counting

**What to build:** A Step Jack Exercise Variant, end-to-end through the rep-counting engine and persistence — its own rep-count threshold, its own Good Set / Form Score judgment, and its own Personal Best, distinct from Jumping Jack's. This ticket has no UI entry point for choosing Step Jack yet (that's ticket 11) — it's verified through the engine's tests and a direct call path (e.g. a debug/test harness invoking a Step Jack Set), plus real persistence so a recorded Step Jack Set actually produces a separate Personal Best.

**Blocked by:** 02 (schema — the `exerciseVariant` column Sets/Tallies persist to).

**Status:** ready-for-agent

- [ ] A new `ExerciseVariant` concept in `core-rep-counting`, with `STEP_JACK` as its only value, parented to `JUMPING_JACK`.
- [ ] `ExerciseProfiles` and `RepCounter` resolve a profile by Exercise plus optional Variant (extending the current Exercise-only key). Step Jack uses the same elbow–shoulder–hip joints and increasing direction as Jumping Jack, with `repThresholdDegrees = 75` and `formThresholdDegrees = 135`.
- [ ] `RoutineStep`/`CompletedSet` carry an optional Variant; a completed Step Jack Set persists its `exerciseVariant` via the ticket 02 column.
- [ ] `SessionEngine`'s Good Set judgement uses the Variant's own threshold when one is set.
- [ ] `ProgressStats.personalBests` keys Personal Bests by Exercise + Variant, so a Step Jack Set and a Jumping Jack Set produce separate records.
- [ ] `RepCounterTest` (using `PoseLandmarkFrameFixtures`): a Step Jack arm sweep to ~80° counts a rep that Jumping Jack would not at the same angle; Form Score uses the 135° threshold.
- [ ] `SessionEngineTest`: a Step Jack step judges Good Set against Step Jack's own thresholds, not Jumping Jack's.
- [ ] `ProgressStatsTest`: Step Jack and Jumping Jack Sets produce separate Personal Bests.
