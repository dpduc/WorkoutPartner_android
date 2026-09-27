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

**Status:** done

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
- [x] On-device (or scripted) verification: running a Push-Up Set and
      stepping legs fully out of frame no longer triggers "Lost track of the
      person."

## Comments

**On-device verification (Samsung SM-S938B, physical device over USB):**
run via the app's debug-video replay path (`testvideos/push_up_edge.mp4`
pushed in as `debug_video.mp4`, `VideoPoseTracker` swapping in for the real
camera) rather than a live body, so results only cover orientation-agnostic
claims — see the caveat below. Two runs, both through Quick Count:

- **Push Up selected, push-up clip played end to end**: `VideoPoseTracker`
  logged `finished: 505 frames analyzed, pose detected in 505` — zero frames
  ever read as untracked — and the run completed normally (2 reps, Form
  Score 50, Tally saved) with the "Lost track of the person" banner never
  appearing once. Confirms the positive case this checklist item asks for.
- **Squat selected against the same push-up-framed clip (deliberate
  mismatch)**: immediately read as not-`Trackable` — banner text switched to
  "Step into frame so we can see you clearly." and the live border (ticket
  02) switched to its dashed state — confirming the negative case too: a
  profile whose required joints genuinely aren't confidently visible does
  still correctly report Lost/not-Trackable, so this isn't a change that
  made the check permissive in general, only exercise-appropriate.

**Caveat on this methodology** (see `video-clip-testing-mirror-caveat`
memory / raised mid-session): `VideoPoseTracker` doesn't apply the
un-mirroring `CameraPoseTracker` does for a live front camera feed, so
landmark left/right identity is flipped relative to a real session. Both
checks above only depend on generic joint-*type* presence/confidence, not
left/right identity, so this doesn't undermine either result — but it's
also why this doesn't stand in for verifying anything side-dependent (a
Lunge's front leg, the still-open `rep-counting-side-consistency` bug).

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
