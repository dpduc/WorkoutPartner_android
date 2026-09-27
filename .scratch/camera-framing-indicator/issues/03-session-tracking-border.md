# 03: Session Tracking gets the same border

**What to build:** The same continuous-color border and dashed/gray
indicator built in ticket 02 appear live throughout a Session's Tracking
phase, not just before it starts — reused as-is, not reimplemented. As the
Athlete moves between Sets of different Exercises within a Routine, the
required-joints requirement relaxes or tightens automatically (e.g. a Squat
Set into a Push-Up Set stops requiring legs in frame). See `../spec.md`
user stories 3, 14, 23.

**Blocked by:** 01, 02

**Status:** ready-for-agent (code-complete; on-device verification outstanding — see Comments)

- [x] `SessionScreens.kt`'s `TrackingContent` renders ticket 02's shared
      border component, driven by the same shared distance-scoring pipeline
      and the tracker's `signals`.
- [x] `SessionViewModel`'s `rawFrames` collection becomes unconditional
      (today gated behind `showPoseOverlay`, debug builds only), so the
      border has data to work with in release builds.
- [x] The border's dashed/gray requirement visibly changes when the Routine
      moves from one Set's Exercise to the next, using ticket 01's per-step
      profile update.
- [x] The border sits over the camera preview without obstructing the
      existing rep count, target, or progress text.
- [x] No change to `SessionEngine`, rep counting, or Form Score computation.
- [x] Typecheck and the full test suite pass.
- [ ] On-device verification across a multi-Exercise Routine (e.g. a Squat
      Set followed by a Push-Up Set): the border's color responds live to
      distance throughout, and its required-joints behavior changes
      correctly between Sets. **Not done — no device/emulator available
      this session.**

## Comments

`SessionViewModel` gains a `FramingScoreSmoother` instance and a
`framingCloseness: StateFlow<Float>`, fed by making the existing
`poseTracker.rawFrames` collection unconditional (previously wrapped in
`if (showPoseOverlay)`): the single collector now updates `_poseFrame` only
when `showPoseOverlay` (unchanged debug-only behavior) but always feeds
`FramingScorer.evaluate(frame).closeness` through the smoother into
`framingCloseness`. `SessionScreen` collects it as state and passes it to
`TrackingContent`, which renders `FramingBorder(trackable = phase.trackable,
closeness = framingCloseness, modifier = Modifier.fillMaxSize())` — drawn
right after the pose overlay and before the rep-count `Column`, as an
unfilled `Stroke` outline, so it never covers the existing text.

Deliberately did not add a second `poseTracker.signals` collection to the
screen: `SessionPhase.Tracking.trackable` is already `TrackingStateMachine`'s
exercise-aware signal (ticket 01), plumbed through `SessionEngine.onPoseSignal`
from `poseTracker.signals` and already driving the "Lost track of you"
banner — reusing it for the border keeps both agreeing with the same source
instead of adding a second, independently-collected copy of the same signal.
Ticket 01's existing `SessionViewModel.publishPhase()` step-index-change ->
`poseTracker.updateExerciseProfile(...)` wiring was not touched; this ticket
only consumes its effect via `phase.trackable`.

No changes to `SessionEngine`, `RepCounter`, or Form Score. `QuickCountScreens.kt`/
`QuickCountViewModel.kt` (ticket 04, a parallel worktree) were not touched.

Typechecked (`:app:compileDebugKotlin`, `:core-pose-tracking:compileDebugKotlin`)
and full suite green (`test`/`testDebugUnitTest` across all modules — app,
core-pose-tracking, core-rep-counting, core-streaks, data).

Same not-committed, gitignored-confirmed local build workaround as tickets
01/02 (`local.properties` + a placeholder `app/google-services.json`) —
needed again since this worktree started without them; neither is part of
this ticket's diff. This worktree also started stale (at a
`camera-session-robustness`-era commit) and needed
`git merge --ff-only feature/camera-framing-indicator` before reading source,
same as prior tickets.
