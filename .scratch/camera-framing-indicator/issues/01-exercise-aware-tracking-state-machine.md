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

**Status:** ready-for-agent

- [ ] `TrackingStateMachine` takes the current step's `ExerciseProfile` and
      checks only that profile's three joints (`jointA`/`vertex`/`jointC`),
      resolved the same side-picking way `RepCounter`/`PoseFrameMapper`
      would — not a fixed generic-landmark count.
- [ ] `TrackingStateMachine` supports updating the current profile after
      construction, without resetting its existing Lost/Trackable debounce
      state (consecutive-frame counters).
- [ ] Existing debounce constants (frames-to-Lost, frames-to-Resume) are
      unchanged; all existing `TrackingStateMachineTest` cases still pass
      unmodified.
- [ ] `CameraPoseTracker` and `VideoPoseTracker` each thread an initial
      `ExerciseProfile` into their own `TrackingStateMachine` instance.
- [ ] The `PoseTracker` factory (`AppContainer.createPoseTracker()`) accepts
      an initial `ExerciseProfile`; Position Check, Session, and Quick Count
      each pass their own already-known Exercise/Variant at construction
      (Quick Count's own parameter; a Session's first Set; Position Check's
      first Set, the same way it already reads that for its first-Set
      announcement).
- [ ] `SessionViewModel` pushes a profile update to its tracker whenever it
      observes a new `SessionPhase.Tracking.stepIndex`, so the requirement
      tracks the Routine's current Exercise as it moves between Sets. Quick
      Count and Position Check never need this (one Exercise for their whole
      run).
- [ ] `TrackingStateMachineTest` gains cases: a frame missing only joints
      irrelevant to the current profile still reads `Trackable`; a frame
      missing a joint the current profile *does* need eventually reads
      `Lost`; updating the profile mid-instance changes which joints are
      checked from that point forward.
- [ ] Typecheck and the full test suite pass.
- [ ] On-device (or scripted) verification: running a Push-Up Set and
      stepping legs fully out of frame no longer triggers "Lost track of the
      person."

## Comments
