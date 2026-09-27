# 04: Quick Count Run gets the same border

**What to build:** The same continuous-color border and dashed/gray
indicator built in ticket 02 appear live throughout a Quick Count run —
reused as-is, not reimplemented. Quick Count tracks a single Exercise for its
whole run, so the required-joints requirement stays fixed for the duration
(no per-step changes, unlike ticket 03's Session case). See `../spec.md`
user stories 4, 15, 23.

**Blocked by:** 01, 02

**Status:** ready-for-agent (code-complete; on-device verification outstanding — see Comments)

- [x] `QuickCountScreens.kt`'s `QuickCountRunScreen` renders ticket 02's
      shared border component, driven by the same shared distance-scoring
      pipeline and the tracker's `signals`.
- [x] `QuickCountViewModel` gains a `rawFrames` collection (it has none
      today), so the border has data to work with.
- [x] The border sits over the camera preview without obstructing the
      existing rep count/target UI.
- [x] No change to `QuickCountEngine`, rep counting, or Form Score
      computation.
- [x] Typecheck and the full test suite pass.
- [ ] On-device verification: a Quick Count run shows the live color border
      responding to distance, and the dashed/gray state when the run's
      Exercise's required joints aren't visible. **Not done — no
      device/emulator available this session.**

## Comments

Wired the same way `PositionCheckScreen` (ticket 02) does, adapted to Quick
Count's ViewModel-owned (not `remember`-owned) shape: `QuickCountViewModel`
gained a `private val smoother = FramingScoreSmoother()` and a `viewModelScope.launch`
collecting `poseTracker.rawFrames`, feeding each frame's `FramingScorer.evaluate(frame).closeness`
through the smoother into a new `closeness: StateFlow<Float>`. `trackable`
needed no new plumbing — `QuickCountPhase.Running.trackable` already comes
from `poseTracker.signals` via `QuickCountEngine.onPoseSignal` (ticket 01's
work). `QuickCountRunScreen` collects both as state and renders
`FramingBorder(trackable = current.trackable, closeness = closeness, modifier = Modifier.fillMaxSize())`
directly under the camera `AndroidView` and above the rep-count `Column` —
an outline-only `Stroke` draw, so it never obstructs the existing rep
count/target text.

Confirmed, not reimplemented: `QuickCountScreen`/`QuickCountRunScreen`'s
`poseTrackerFactory: (ExerciseProfile) -> PoseTracker` call sites already
pass `ExerciseProfiles.forExercise(exercise)` correctly from ticket 01's
work — no per-step profile-update logic needed here, since Quick Count
tracks one Exercise for its whole run.

No change to `QuickCountEngine`, `RepCounter`, or Form Score computation.
`SessionScreens.kt`/`SessionViewModel.kt` untouched (a parallel ticket's
scope). Typechecked (`:app:compileDebugKotlin`, `:core-pose-tracking:compileDebugKotlin`)
and full suite green (`test`/`testDebugUnitTest` across all modules).
Implemented in its own worktree, which — like tickets 01/02 — started stale
and needed a clean fast-forward onto `feature/camera-framing-indicator`
first.

Same not-committed, gitignored-confirmed local build workaround as tickets
01/02 (`local.properties` + a placeholder `app/google-services.json`) —
needed again since each fresh worktree starts without them; neither is part
of this ticket's diff.
