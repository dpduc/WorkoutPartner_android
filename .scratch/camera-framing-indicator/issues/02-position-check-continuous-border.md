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

**Status:** ready-for-agent

- [ ] A new shared, pure module computes a continuous 0.0–1.0 distance-
      closeness score from a `RawPoseFrame`, generalizing
      `PositionCheckEvaluator`'s existing skeleton-height-fraction
      calculation (reusing the existing `MIN_SKELETON_HEIGHT_FRACTION`/
      `MAX_SKELETON_HEIGHT_FRACTION` constants as the score's range); the
      existing 3-bin `DistanceStatus` is derived from that same continuous
      score, for the existing text label.
- [ ] The raw per-frame score is smoothed (exponential moving average across
      recent frames) before being mapped to a color.
- [ ] A new shared, reusable border Composable renders a color driven by the
      smoothed score (animated via `animateColorAsState`, a new pattern in
      this codebase), overridden by a fixed dashed/gray state whenever
      ticket 01's exercise-aware `TrackingStateMachine`/`PoseTrackingSignal`
      reports not-`Trackable`.
- [ ] `PositionCheckScreen`'s existing `BodyOutline` composable is deleted
      and replaced by the new shared border.
- [ ] `PositionCheckEvaluator`'s `bodyInFrame`/`REQUIRED_CONFIDENT_LANDMARKS`
      computation is deleted outright (not kept alongside the new
      mechanism); Position Check's readiness now comes from collecting
      `poseTracker.signals` instead.
- [ ] The existing "too far — come closer" / "too close — step back" text
      label is retained, shown only while off the ideal distance (matching
      today's hide-when-passing behavior).
- [ ] The pure distance-scoring module moves out of the `beforeyoustart`
      package into a location shared by all three camera-active screens.
- [ ] `BeforeYouStartEngineTest`'s now-obsolete `bodyInFrame`/
      `REQUIRED_CONFIDENT_LANDMARKS` test cases are removed.
- [ ] A new dedicated test file covers: the continuous score's
      monotonicity/boundaries against the existing skeleton-height-fraction
      constants; the smoothing function's behavior across a sequence of
      noisy inputs; and the score-to-color mapping's endpoints and midpoint.
- [ ] Typecheck and the full test suite pass.
- [ ] On-device verification: Position Check shows the live color border
      responding to distance, and switches to the dashed/gray state when the
      upcoming Exercise's required joints aren't visible.

## Comments
