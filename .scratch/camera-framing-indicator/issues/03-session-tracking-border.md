# 03: Session Tracking gets the same border

**What to build:** The same continuous-color border and dashed/gray
indicator built in ticket 02 appear live throughout a Session's Tracking
phase, not just before it starts — reused as-is, not reimplemented. As the
Athlete moves between Sets of different Exercises within a Routine, the
required-joints requirement relaxes or tightens automatically (e.g. a Squat
Set into a Push-Up Set stops requiring legs in frame). See `../spec.md`
user stories 3, 14, 23.

**Blocked by:** 01, 02

**Status:** ready-for-agent (code-complete; border-rendering bug fixed and verified via the shared component, multi-Set live transition still outstanding — see Comments)

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
      correctly between Sets. **Partially covered, see Comments — the
      multi-Set live transition itself still needs a real device session.**

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

**On-device update**: this ticket's `FramingBorder` usage is identical to
Position Check's and Quick Count's, both of which were directly verified on
a Samsung SM-S938B and found to have a real rendering bug (the border was
invisible — see ticket 02's Comments for root cause and the fix,
`59d523e`). That fix applies here unchanged, since this ticket's code just
calls the same shared component with no Session-specific rendering logic —
high confidence it's fixed here too, but **not directly exercised**: the
debug-video replay mechanism used for the other two tickets plays one clip
per tracker instance, and `SessionViewModel` holds one tracker for an
entire multi-Set Session, so there's no easy way to feed genuinely different
footage per Set the way live use would provide. What *was* confirmed
on-device (ticket 01's Comments) is the underlying mechanism this ticket
depends on: `TrackingStateMachine` correctly reports not-Trackable when the
current profile's joints don't match what's actually visible, and
`SessionViewModel.publishPhase()`'s step-index-triggered
`updateExerciseProfile` call is unchanged, code-reviewed, unit-tested code.
The specific live experience of watching the border's requirement relax
mid-Session as a Routine moves from Squats into Push-Ups is the one piece
still resting on structural evidence rather than a direct observation —
next session with a device and a real body in frame, the linear check would
be: start a two-Set Routine (Squat then Push-Up), and confirm the border/
banner behavior visibly changes at the Squat-to-Push-Up handoff.
