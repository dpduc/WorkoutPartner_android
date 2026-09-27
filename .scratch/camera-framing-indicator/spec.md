Status: ready-for-agent

# Live camera-framing indicator: continuous distance color + exercise-aware visibility

## Problem Statement

During Position Check, the Athlete gets a one-time pass/fail readout of whether
they're framed well enough for tracking to start — but it's static, binary
(green or white), and requires the Athlete's *whole* body in frame regardless
of which Exercise they're about to do. Once tracking actually starts (Session
Tracking, Quick Count Run), there's no distance feedback at all: the Athlete
gets no signal that they've drifted too close or too far until reps stop
landing, at which point all they see is a generic "Lost track of the person"
banner — after the fact, not as a guide to self-correct in the moment.

The "whole body in frame" requirement is also stricter than it needs to be.
`ExerciseProfile` already knows a Push-Up only needs the shoulder, elbow and
wrist visible — legs are irrelevant — but Position Check, and the tracking-lost
banner, both currently demand the same generic full-body visibility no matter
which Exercise is running.

## Solution

A single continuous-color border, drawn over the camera preview on all three
camera-active screens (Position Check, Session Tracking, Quick Count Run),
replaces Position Check's existing static body-silhouette outline. Its color
continuously reflects how close the Athlete's distance from the phone is to
ideal — green at the ideal distance, shading toward orange/red the further off
in either direction — updating live every pose frame, smoothed so the color
drifts rather than jumps. Distance never gates anything: reps count and a
Session/Quick Count run proceeds regardless of the border's color.

Separately, the border's *character* (not its color) reflects a boolean:
can we currently see the specific joints the Exercise (or Exercise Variant)
being tracked right now actually needs? When no, the border turns a fixed
dashed/gray state that overrides whatever the distance color would otherwise
say — distance can't be meaningfully judged for a joint that isn't tracked
at all, so this is a genuine third state, not color layered with a flag. This
same exercise-aware check replaces three things that are separate today:
Position Check's own whole-body readiness check, and the generic (not
exercise-aware) check behind Session/Quick Count's existing "Lost track of
the person" banner. All three now agree on one answer.

This spec explicitly documents, rather than fixes, a pre-existing limitation
this feature makes more visible: `RepCounter.process()` already silently
skips (under-counts, never miscounts) a frame whenever the current Exercise's
required joints aren't confidently tracked. Widening when the app *tells* the
Athlete they're framed well doesn't change when a Rep actually counts.

## User Stories

1. As an Athlete in Position Check, I want to see a border around the camera
   preview instead of a static body silhouette, so the same visual language
   carries into the actual workout.
2. As an Athlete in Position Check, I want the border to shift color
   continuously as I move closer or further from the ideal distance, so I get
   finer feedback than today's binary pass/fail.
3. As an Athlete in a Session's Tracking phase, I want the same live-color
   border, so I get distance feedback for the entire Set, not just before it
   starts.
4. As an Athlete running a Quick Count, I want the same live-color border, so
   Quick Count isn't a worse experience than a full Session.
5. As an Athlete, I want reps to keep counting and the Set/run to keep
   progressing no matter what color the border is, so imperfect framing never
   blocks or interrupts my workout.
6. As an Athlete, I want the existing "too far — come closer" / "too close —
   step back" text to keep appearing, so I know *which way* to move, not just
   that something's off.
7. As an Athlete, I want the border's color to change gradually rather than
   flicker frame to frame, so small, momentary tracking noise doesn't read as
   me constantly drifting.
8. As an Athlete doing a Push-Up Set, I want the framing check to only care
   about my shoulders, elbows and wrists, so I'm not flagged for legs the
   camera can't even see at that angle.
9. As an Athlete doing a Squat or Lunge Set, I want the framing check to only
   care about hips, knees and ankles.
10. As an Athlete doing a Sit-Up Set, I want the framing check to only care
    about shoulders, hips and knees.
11. As an Athlete, when the joints my current Exercise needs aren't visible
    at all, I want the border to visibly change shape/character (not just
    color), so a "can't tell" state is unmistakable from "framed, but off to
    one side."
12. As an Athlete, I want that "can't tell" state to override the distance
    color entirely while it's active, so I'm never shown a confident-looking
    green border when the app actually can't see enough of me.
13. As an Athlete starting a Routine whose first Set is, say, a Squat, I want
    Position Check to check for Squat's joints specifically, not a fixed
    whole-body rule.
14. As an Athlete partway through a multi-Exercise Routine, I want the live
    framing requirement to update automatically when I move from one Set's
    Exercise to the next (e.g. Squats into Push-Ups), so the requirement
    stays appropriate to what I'm actually doing right now.
15. As an Athlete running Quick Count for a single Exercise, I want the
    framing requirement to stay fixed to that one Exercise for the whole run.
16. As an Athlete who is colorblind, I want a non-color way to tell the
    difference between "too far/too close" (the text label) and "can't see
    what's needed" (the border's shape, not its hue), so I'm not solely
    dependent on distinguishing red from green.
17. As an Athlete, if I happen to have my whole body in frame even when my
    current Exercise doesn't require it, I want that to still work fine — the
    per-Exercise requirement is a minimum, not a maximum.
18. As a developer, I want one shared, already-unit-tested mechanism deciding
    "can we see what's needed," not three independently-implemented versions
    that could silently drift apart.
19. As a developer maintaining the "Lost track of the person" debounce, I
    want its existing lost-after-N/resume-after-M frame behavior preserved
    unchanged, so this doesn't reintroduce banner flicker.
20. As a developer, I want the continuous distance-scoring logic to be a pure,
    directly unit-tested function, since it's now relied on by three screens
    instead of only Position Check.
21. As a developer, I want Position Check's old whole-body check deleted once
    the shared mechanism replaces it, not kept around as a second,
    now-redundant implementation.
22. As a Product owner, I want no change whatsoever to how a Rep is counted,
    Form Score is graded, or a Good Set is judged — this is a feedback-layer
    feature only.
23. As an Athlete, I want the border to sit over the main camera preview area
    without obstructing the rep count, target, or progress text already
    shown on screen.
24. As a QA reviewer, I want to be able to verify the distance-to-color and
    smoothing logic with plain unit tests, without needing a real camera or
    device.
25. As a future maintainer reading `TrackingStateMachine`, I want an ADR
    explaining why it now takes an `ExerciseProfile`, so this doesn't read as
    an unexplained scope creep of a previously camera-agnostic class.

## Implementation Decisions

- **One shared border component**, rendered inside `PositionCheckScreen`,
  `SessionScreens.kt`'s `TrackingContent`, and `QuickCountScreens.kt`'s
  `QuickCountRunScreen`, replacing `PositionCheckScreen`'s existing
  `BodyOutline` composable entirely.
- **Distance color** is driven by a continuous 0.0–1.0 closeness score,
  symmetric regardless of direction — too-close and too-far both shade toward
  the same red end of the gradient. The existing "too far — come closer" /
  "too close — step back" text label is kept as the non-color, direction-
  carrying cue, shown only while off the ideal distance (matching today's
  hide-when-passing behavior).
- **The continuous score is computed from the full `RawPoseFrame`** (all
  confident raw MediaPipe landmarks), generalizing `PositionCheckEvaluator`'s
  existing skeleton-height-fraction calculation — deliberately *not* scoped
  to only the current Exercise's joints, since distance-to-camera is a
  property of the physical setup, not of which Exercise is running. The
  existing `MIN_SKELETON_HEIGHT_FRACTION`/`MAX_SKELETON_HEIGHT_FRACTION`
  constants are reused as the score's range; the existing 3-bin
  `DistanceStatus` is derived from the same continuous score, for the text
  label.
- **Smoothing and animation**: the raw per-frame score is smoothed with an
  exponential moving average before being mapped to a color; Compose's
  `animateColorAsState` (a new pattern in this codebase — not used anywhere
  today) animates the transition between smoothed values. Recomputed every
  pose frame; no throttling — `CameraPoseTracker` already analyzes at full
  rate for rep-counting regardless.
- **`PositionCheckEvaluator`'s pure distance-scoring logic moves out of the
  `beforeyoustart` package** into a location shared by all three screens
  (exact package left to the implementer), since Session and Quick Count now
  depend on it too, not just Position Check.
- **The fixed "whole body in frame" requirement is retired.**
  `TrackingStateMachine` (`core-pose-tracking`) is extended to take the
  current step's `ExerciseProfile` and check only that profile's three joints
  (`jointA`/`vertex`/`jointC`, resolved the same side-picking way
  `RepCounter`/`PoseFrameMapper` would), replacing its current fixed "any 3 of
  the 6 generic joints" threshold. The existing debounce constants
  (frames-to-Lost, frames-to-resume) are unchanged.
- **`TrackingStateMachine`'s exercise-aware Trackable/Lost decision becomes
  the single source of truth** for three things that are three separate
  implementations today: (a) `SessionPhase.Tracking.trackable` /
  `QuickCountPhase.Running.trackable` (already exists, now exercise-aware
  instead of generic), (b) the new border's dashed/gray override state, and
  (c) Position Check's own readiness check — `PositionCheckEvaluator`'s
  independent `bodyInFrame`/`REQUIRED_CONFIDENT_LANDMARKS` computation is
  deleted, not kept alongside it (see ADR-0011).
- **Tracker construction gains a profile.** `AppContainer.createPoseTracker()`
  takes an initial `ExerciseProfile`, threaded down into `CameraPoseTracker`'s
  (and `VideoPoseTracker`'s) `TrackingStateMachine`. All three call sites
  already know their Exercise/Variant upfront: Quick Count's own constructor
  parameter, a Session's first Set, and Position Check reading the first Set
  the same way `BeforeYouStartScreen` already does today for its first-Set
  announcement.
- **Mid-Session profile updates.** `SessionViewModel` pushes a profile update
  to its `PoseTracker` whenever it observes a new
  `SessionPhase.Tracking.stepIndex`, so the requirement relaxes/tightens as
  the Routine moves between Exercises. Quick Count and Position Check never
  need this — one Exercise for their whole run.
- **`rawFrames` collection becomes unconditional.** `SessionViewModel`
  currently only collects `poseTracker.rawFrames` when `showPoseOverlay`
  (debug builds only); this becomes unconditional so the continuous distance
  score has data in release builds. `QuickCountViewModel` gains a
  `rawFrames` collection it doesn't have today, for the same reason.
- **No change to `RepCounter`, `Angle.between()`, Form Score, or Good Set**
  — this feature is a feedback layer over already-existing tracking data.
- Already done this session, referenced but not repeated here: ADR-0011
  records the `TrackingStateMachine`-unification decision; `CONTEXT.md`'s
  "Position Check" entry already reflects the exercise-scoped definition.

## Testing Decisions

A good test here exercises external behavior — the `PoseTrackingSignal` a
sequence of frames produces, or the score/color/bin a `RawPoseFrame` produces
— never internal smoothing state or Compose recomposition details.

- **`TrackingStateMachine`**: extend the existing `TrackingStateMachineTest`
  (`core-pose-tracking`) with cases for the new exercise-aware behavior — a
  frame missing only joints irrelevant to the current profile still reads
  `Trackable`; a frame missing a joint the current profile *does* need
  eventually reads `Lost`; updating the profile mid-instance changes which
  joints are checked from that point forward. The existing debounce tests
  (occasional dropped frames, lost-after-N, resume-after-M, mostly-occluded
  frames) stay as-is — the debounce logic itself doesn't change.
- **The distance pipeline**: a new dedicated test file — this logic currently
  has no dedicated test file, only indirect coverage via
  `BeforeYouStartEngineTest` — covering the continuous score's
  monotonicity/boundaries against the existing skeleton-height-fraction
  constants, the smoothing function's behavior across a sequence of noisy
  inputs, and the score-to-color mapping's endpoints and midpoint. Prior art:
  `BeforeYouStartEngineTest`'s existing `frame(topY = ..., bottomY = ...,
  confidentCount = ...)` frame-builder is reusable here.
- **`BeforeYouStartEngineTest`'s existing `bodyInFrame`/
  `REQUIRED_CONFIDENT_LANDMARKS` tests are removed**, replaced by equivalent
  coverage in `TrackingStateMachineTest` for the exercise-aware path, since
  that's where the behavior now lives.
- **Compose UI is not unit-tested** — the border `Canvas`,
  `animateColorAsState`, collecting `rawFrames`/`signals` into state, and the
  dashed/gray rendering — matching this codebase's existing precedent for
  every camera-facing screen (`PositionCheckScreen`, `SessionScreens`,
  `QuickCountScreens`, `CameraPoseTracker`, `SessionViewModel`,
  `QuickCountViewModel` are all explicitly "not unit-tested" per their own
  doc comments). Verified on-device instead, the same way camera-session-
  robustness tickets 02/03 were.
- `SessionEngineTest`/`QuickCountEngineTest` are unaffected — rep/Form Score
  computation doesn't change, so no new engine-level test coverage is needed
  there.

## Out of Scope

- Any change to `RepCounter`, `Angle.between()`, Form Score, or when a Rep
  actually counts — including *not* fixing the "skips a frame when required
  joints aren't tracked" behavior this spec documents as a caveat.
- The `rep-counting-side-consistency` bug (tracked separately) — not touched,
  though `TrackingStateMachine`'s new exercise-aware side-resolution shares
  the same underlying concern.
- Any accessibility affordance beyond the redundant text label and the
  non-color dashed shape already decided — no colorblind-safe palette
  setting, no haptic feedback, no audio cue.
- Tuning `MIN_SKELETON_HEIGHT_FRACTION`/`MAX_SKELETON_HEIGHT_FRACTION` or any
  exercise-specific distance thresholds — existing placeholder constants are
  reused as-is.
- A settings toggle to disable the border.
- Any change to camera-session-robustness (tickets 01–03) — Foreground
  Service, keep-screen-on, and camera-loss/reconnect behavior are untouched.
- Any change to `TrackingStateMachine`'s debounce constants — existing
  defaults are reused.

## Further Notes

- ADR-0011 (`docs/adr/0011-trackable-state-becomes-exercise-aware.md`)
  already records the decision to unify the visibility check into
  `TrackingStateMachine` rather than adding a second, parallel signal.
- `CONTEXT.md`'s "Position Check" entry has already been updated to describe
  an exercise-scoped joints check rather than a fixed whole-body one.
- This work touches the same `CameraPoseTracker`/`TrackingStateMachine` area
  as the recently-shipped camera-session-robustness tickets (02/03) — that
  area's git history is already entangled (see that spec's own Comments), so
  review this diff carefully rather than assuming a clean separation.
- Caveat worth surfacing to Athletes directly (the reason this spec exists):
  the border and joints-visible indicator are best-effort visual feedback,
  not a guarantee of correct rep counting. `RepCounter` already silently
  under-counts when the current Exercise's specific required joints aren't
  tracked — precisely the marginal-framing situation this indicator is meant
  to help the Athlete correct, not a failure mode it eliminates.
