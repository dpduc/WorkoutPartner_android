# 01: Exercise-aware camera-tracking machinery

**What to build:** `TrackingStateMachine` stops using a fixed "any 3 of the 6
generic joints" rule and instead checks only the joints the *currently
tracked* Exercise (or Exercise Variant) actually needs — so, for example, a
Push-Up Set no longer flips to "Lost track of the person" just because the
Athlete's legs (irrelevant to a Push-Up) have left frame. This has no new UI
of its own: it's verified through the existing "Lost track of the person"
behavior in Session/Quick Count and through unit tests. See `../spec.md`
("The fixed 'whole body in frame' requirement is retired...") and
`docs/adr/0011-trackable-state-becomes-exercise-aware.md` for the reasoning.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent (code-complete; on-device verification outstanding — see Comments)

- [x] `TrackingStateMachine` takes the current step's `ExerciseProfile` and
      checks only that profile's three joints (`jointA`/`vertex`/`jointC`),
      resolved the same side-picking way `RepCounter`/`PoseFrameMapper`
      would — not a fixed generic-landmark count.
- [x] `TrackingStateMachine` supports updating the current profile after
      construction, without resetting its existing Lost/Trackable debounce
      state (consecutive-frame counters).
- [x] Existing debounce constants (frames-to-Lost, frames-to-Resume) are
      unchanged; all existing `TrackingStateMachineTest` cases still pass
      unmodified.
- [x] `CameraPoseTracker` and `VideoPoseTracker` each thread an initial
      `ExerciseProfile` into their own `TrackingStateMachine` instance.
- [x] The `PoseTracker` factory (`AppContainer.createPoseTracker()`) accepts
      an initial `ExerciseProfile`; Position Check, Session, and Quick Count
      each pass their own already-known Exercise/Variant at construction
      (Quick Count's own parameter; a Session's first Set; Position Check's
      first Set, the same way it already reads that for its first-Set
      announcement).
- [x] `SessionViewModel` pushes a profile update to its tracker whenever it
      observes a new `SessionPhase.Tracking.stepIndex`, so the requirement
      tracks the Routine's current Exercise as it moves between Sets. Quick
      Count and Position Check never need this (one Exercise for their whole
      run).
- [x] `TrackingStateMachineTest` gains cases: a frame missing only joints
      irrelevant to the current profile still reads `Trackable`; a frame
      missing a joint the current profile *does* need eventually reads
      `Lost`; updating the profile mid-instance changes which joints are
      checked from that point forward.
- [x] Typecheck and the full test suite pass.
- [ ] On-device (or scripted) verification: running a Push-Up Set and
      stepping legs fully out of frame no longer triggers "Lost track of the
      person." **Not done — no device/emulator available this session.**

## Comments

Implemented via `TrackingStateMachine.accept()` now gating on whether
`PoseLandmarkFrame.landmarks.keys` contains the current `ExerciseProfile`'s
`jointA`/`vertex`/`jointC` — deliberately the same all-or-nothing gate
`RepCounter.process()`/`Angle.between()` already applies internally, so
"Trackable" now means exactly "this frame can actually become a Rep," not an
independently-tuned approximation of it. Added `updateProfile(profile)` to
swap the profile without touching debounce state. `minimumTrackedLandmarks`/
`DEFAULT_MINIMUM_TRACKED_LANDMARKS` removed (superseded, not just unused).

Ripple: `TrackingStateMachine`'s constructor now *requires* an
`ExerciseProfile` (no generic-only mode), so `PoseTracker.createPoseTracker()`
and its three call sites (`BeforeYouStartScreen`, `SessionScreens`,
`QuickCountScreens`) all changed from `() -> PoseTracker` to
`(ExerciseProfile) -> PoseTracker` factories — `MainActivity` needed no
change since `container::createPoseTracker` adapts automatically. A new
`PoseTracker.updateExerciseProfile(profile)` method carries `SessionViewModel`'s
per-step update through.

One deviation from this ticket's literal wording, judged in-spirit rather than
against the letter: "all existing `TrackingStateMachineTest` cases still pass
unmodified" couldn't mean byte-for-byte unmodified once the constructor itself
requires a profile — the 6 pre-existing cases needed a mechanical
`initialProfile = ExerciseProfiles.forExercise(Exercise.SIT_UP)` constructor
arg added (chosen to match their existing SHOULDER/HIP/KNEE fixtures exactly),
with no change to their frame fixtures or assertions. `ClipReplayTest.kt` had
one stray no-arg `TrackingStateMachine()` call fixed the same way, against
whichever Exercise that test already replays.

Typechecked (`:core-pose-tracking:compileDebugKotlin`, `:app:compileDebugKotlin`)
and full suite green (`testDebugUnitTest`/`test` across all modules).
Implemented in its own worktree, fast-forward-merged into
`feature/camera-framing-indicator` at `664e11d` clean, no conflicts.

Not committed, confirmed gitignored/local-only, needed purely to unblock local
compilation in the sandbox this ran in: a placeholder `app/google-services.json`
(the real one's absence is an existing, documented gap — see `AppContainer.kt`)
and a `local.properties` pointing at a local Android SDK. Neither is part of
this ticket's diff.
