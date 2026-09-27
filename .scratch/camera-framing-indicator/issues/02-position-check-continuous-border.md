# 02: Position Check gets the continuous-color border

**What to build:** Position Check's static, binary (green/white) body-
silhouette outline is replaced by a live, continuous-color border: green at
the ideal distance, shading toward orange/red the further off in either
direction, updating smoothly frame to frame. The border also visibly changes
character (dashed/gray, overriding whatever color it would otherwise show)
whenever the joints the upcoming Exercise needs aren't visible, using ticket
01's exercise-aware tracking. This ticket builds the shared pieces (the pure
distance-scoring pipeline and the border component itself) that tickets 03
and 04 then reuse as-is. See `../spec.md`'s Solution and Implementation
Decisions sections.

**Blocked by:** 01

**Status:** done

- [x] A new shared, pure module computes a continuous 0.0–1.0 distance-
      closeness score from a `RawPoseFrame`, generalizing
      `PositionCheckEvaluator`'s existing skeleton-height-fraction
      calculation (reusing the existing `MIN_SKELETON_HEIGHT_FRACTION`/
      `MAX_SKELETON_HEIGHT_FRACTION` constants as the score's range); the
      existing 3-bin `DistanceStatus` is derived from that same continuous
      score, for the existing text label.
- [x] The raw per-frame score is smoothed (exponential moving average across
      recent frames) before being mapped to a color.
- [x] A new shared, reusable border Composable renders a color driven by the
      smoothed score (animated via `animateColorAsState`, a new pattern in
      this codebase), overridden by a fixed dashed/gray state whenever
      ticket 01's exercise-aware `TrackingStateMachine`/`PoseTrackingSignal`
      reports not-`Trackable`.
- [x] `PositionCheckScreen`'s existing `BodyOutline` composable is deleted
      and replaced by the new shared border.
- [x] `PositionCheckEvaluator`'s `bodyInFrame`/`REQUIRED_CONFIDENT_LANDMARKS`
      computation is deleted outright (not kept alongside the new
      mechanism); Position Check's readiness now comes from collecting
      `poseTracker.signals` instead.
- [x] The existing "too far — come closer" / "too close — step back" text
      label is retained, shown only while off the ideal distance (matching
      today's hide-when-passing behavior).
- [x] The pure distance-scoring module moves out of the `beforeyoustart`
      package into a location shared by all three camera-active screens.
- [x] `BeforeYouStartEngineTest`'s now-obsolete `bodyInFrame`/
      `REQUIRED_CONFIDENT_LANDMARKS` test cases are removed.
- [x] A new dedicated test file covers: the continuous score's
      monotonicity/boundaries against the existing skeleton-height-fraction
      constants; the smoothing function's behavior across a sequence of
      noisy inputs; and the score-to-color mapping's endpoints and midpoint.
- [x] Typecheck and the full test suite pass.
- [x] On-device verification: Position Check shows the live color border
      responding to distance, and switches to the dashed/gray state when the
      upcoming Exercise's required joints aren't visible.

## Comments

**On-device verification (Samsung SM-S938B) found and fixed a real bug this
exact checklist item exists to catch**: the border was completely invisible
on the real device, in both Position Check and Quick Count Run. Confirmed
this wasn't a testing artifact via a throwaway diagnostic (a solid magenta
fill in the same `Canvas`, temporarily) — the fill painted edge to edge
correctly, but a `Stroke`-style `drawRect()` using the exact same rect
geometry produced nothing visible against either a black or magenta
background. Root cause: `drawRect()` was called with the rect's path sitting
exactly on the canvas's own boundary (`topLeft = (0,0)`, full `size`), so a
centered `Stroke` had half its 12px width sitting right at the literal
physical screen edge — swallowed by this device's own edge compositing,
independent of anything the app draws there. Fixed by insetting the rect by
a full stroke width on each side, keeping the entire stroke inside the
canvas regardless of any edge effect (`FramingBorder.kt`, follow-up commit
`59d523e` on top of this ticket's own `55eb07f`).

Re-verified after the fix, via the debug-video replay path
(`testvideos/push_up_edge.mp4` as `debug_video.mp4`): the border now clearly
renders in its solid, continuously-colored state, and — selecting an
Exercise whose required joints don't match the loaded clip — clearly renders
its dashed/gray override instead, replacing the passing-checks banner text
with "Step into frame so we can see you clearly." Both states now
unambiguous on real hardware. See ticket 01's Comments for the caveat that
this used video-clip replay, not a live camera, and why that doesn't
undermine this specific (orientation-agnostic) result.

Shared pieces, for tickets 03/04 to import as-is (not reimplement):

- **`app.framing`** (new package, moved out of `beforeyoustart`): `FramingScorer`
  (`DistanceStatus` + `FramingDistance(closeness, status)` + `evaluate(frame: RawPoseFrame)`,
  reusing `MIN_SKELETON_HEIGHT_FRACTION`/`MAX_SKELETON_HEIGHT_FRACTION` as the
  score's range, symmetric both directions), `FramingScoreSmoother` (stateful
  EMA, `smoothingFactor = 0.2f` default), `FramingColor` (`forCloseness(Float): Color`,
  linear `lerp` between `IDEAL`/`FAR_OFF`, plus `UNTRACKABLE`).
- **`app.ui.components.FramingBorder`**: `@Composable fun FramingBorder(trackable: Boolean, closeness: Float, modifier: Modifier = Modifier)` —
  animates the color transition itself (`animateColorAsState`) and draws
  dashed-gray instead whenever `trackable == false`. Callers own smoothing
  (feed it an already-`FramingScoreSmoother`-smoothed `closeness`); this
  composable doesn't smooth internally.

Wired into `PositionCheckScreen` by collecting `poseTracker.signals` (new —
previously only `rawFrames`) for `trackable`, alongside `rawFrames` now also
feeding a `FramingScoreSmoother`. `BeforeYouStartEngine`'s `PositionCheckStatus`
drops `bodyInFrame` entirely (now `distance`/`secondsElapsed` only) — readiness
is no longer computed inside the engine at all, only at the screen level from
`signals`. The `BODY_DETECTED`/`STEP_BACK`/`MOVE_CLOSER` cue logic was re-checked
by hand against every existing `BeforeYouStartEngineTest` fixture: none relied
on the old `bodyInFrame` gate in a way that changes their expected outcome.

`PositionCheckEvaluator` now holds only the stillness check (`isStill`) — the
distance/bodyInFrame logic that used to live there is gone, moved to
`FramingScorer`, not duplicated.

Typechecked and full suite green. Implemented in its own worktree (which,
like ticket 01's, started stale and needed a clean fast-forward onto the
feature branch first), merged into `feature/camera-framing-indicator` at
`55eb07f` with no conflicts.

Same not-committed, gitignored-confirmed local build workaround as ticket 01
(`local.properties` + a placeholder `app/google-services.json`) — needed
again since each fresh worktree starts without them; neither is part of this
ticket's diff.
