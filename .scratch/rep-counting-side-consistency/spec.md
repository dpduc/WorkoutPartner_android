Status: ready-for-agent

# Rep counting: pick one consistent body side per angle, not per joint

## Problem statement

Four real clips (pushed through the debug video-replay pipeline —
`AppContainer`'s `debug_video.mp4` swap — and counted through the actual
app UI, Quick Count) were checked against hand-counted ground truth:

| Clip | Exercise | App counted | True count |
|---|---|---|---|
| `squat.mp4` (face-on) | Squat | 1 | 10 |
| `side_squat.mp4` (side view) | Squat | 6 | 10 |
| `push_up.mp4` (2 reps, walk out, walk in, 6 reps) | Push-up | 6 | 8 |
| `push_up_edge.mp4` | Push-up | 2 | 10 |

Every one of these undercounts, several badly. This is the same shape of
bug `workout-partner-v3`'s jumping-jack clip hit before (5 counted vs. 35
true), and `ClipReplayTest`'s own doc comment already names occlusion and
jitter as a known-noisy area — but the specific mechanism below hasn't been
identified or fixed before.

## Root cause (confirmed against real captured landmark data, not guessed)

Every `ExerciseProfile` (`ExerciseProfiles.kt`) defines its tracked angle as
three `Landmark`s — e.g. Push-up is `SHOULDER`-`ELBOW`-`WRIST`, Squat is
`HIP`-`KNEE`-`ANKLE`. `Landmark` is deliberately side-agnostic (`Landmark.kt`'s
own doc comment: mapping the real per-side MediaPipe landmarks onto these is
`core-pose-tracking`'s job, "e.g. picking whichever side/leg is more
visible" — **one** side, as a unit).

`PoseFrameMapper.pickSide()` does not do that. It resolves **each joint
independently** — SHOULDER's left-vs-right pick has no relationship to
ELBOW's or WRIST's. When the camera's roughly face-on (both sides read
similar confidence), tiny frame-to-frame noise flips which side "wins" each
joint's independent vote — so a single frame's "elbow angle" can end up
computed from the **left shoulder, right elbow, and left wrist**: a
geometrically meaningless cross-body measurement, not the person's actual
arm. `Angle.between` has no way to know this happened; it just computes
whatever three points land in the frame's `landmarks` map.

Direct measurement, from the real landmark CSVs now checked into
`core-pose-tracking/src/test/resources/clips/` (captured via the same debug
pipeline used for the app-level counts above, one row per analyzed frame):

| Clip | Frames | Frames where a single consistent side (all 3 joints) was available | Frames where the mapper picked a mixed/inconsistent side anyway |
|---|---|---|---|
| `squat.landmarks.csv` (face-on) | 356 | 356 (100%) | **327 (91.9%)** |
| `pushup_edge.landmarks.csv` | 505 | 505 (100%) | **256 (50.7%)** |
| `side_squat.landmarks.csv` (side view) | 358 | 347 (96.9%) | 11 (3.2%) |

The "consistent side available" column is the key result: a coherent,
fully-visible side existed almost every single frame in all three clips —
this isn't fundamentally an occlusion/visibility problem. It's that
`pickSide()` doesn't look for that coherent side; it picks per joint and
lets the pieces land wherever they land. The near-100% mix rate on the
face-on clip directly explains why face-on framing (squat.mp4, 1/10) is so
much worse than the side view (side_squat.mp4, 6/10): symmetric visibility
between left/right is exactly the condition that makes independent
per-joint voting flip constantly.

The 3.1% of `side_squat.landmarks.csv`'s frames that had **no** consistent
side available (11 frames) are the real occlusion case — every joint of
every side dips below `PoseFrameMapper.DEFAULT_VISIBILITY_THRESHOLD` at
once. `Angle.between` returns `null` for these today and `RepCounter.process`
skips the frame outright (`RepCounter.kt`'s own doc comment already
describes this as deliberate: "frames with a missing joint... are skipped
rather than treated as a phase change"). That skip is defensible in
isolation, but it's undifferentiated: a frame with *no* usable data and a
frame with a perfectly good but *mismatched* side get the same "just use
whatever's in the map" treatment today, when only the first case actually
has nothing to work with.

## Scope

- `PoseFrameMapper` (or a new seam it delegates to) picks **one physical
  side for the whole angle** — e.g. score each side by its worst (or mean)
  confidence across the profile's three joints, pick the higher-scoring
  side as a unit, and only fall back to per-joint mixing (or drop the
  frame) once neither side has all three joints confidently tracked. This
  needs to know which three `Landmark`s a frame's caller cares about, which
  it doesn't today (it currently maps generically for `PoseTracker`'s
  entire consumer set, before any `RepCounter`/`ExerciseProfile` is in the
  picture) — the exact seam (push the profile's joint triple down into the
  mapper, or resolve side-consistency one layer up, e.g. in `RepCounter`
  itself against `RawPoseFrame`) is an implementation decision for whoever
  picks this up, informed by which is less invasive to `PoseTracker`'s
  existing `signals`/`rawFrames` contract.
- Regression coverage via the existing `ClipReplayTest` pattern
  (`core-pose-tracking/src/test/kotlin/.../ClipReplayTest.kt` +
  `src/test/resources/clips/*.csv`) — the three new CSVs captured for this
  investigation (`squat.landmarks.csv`, `side_squat.landmarks.csv`,
  `pushup_edge.landmarks.csv`) are already checked in for this purpose, each
  with real hand-counted ground truth (10, 10, 10 respectively) to assert
  against once the fix lands.

## Out of scope

- The genuine no-consistent-side-available case (occlusion of every joint
  on every side at once) — already handled by the existing skip-the-frame
  behavior, which this ticket doesn't need to change. `side_squat`'s 11
  such frames are noted above as real evidence this case exists, not as
  something to fix here.
- `push_up.mp4`'s walk-out/walk-in gap (2 reps, then a real Lost/Resume
  cycle, then 6 more) — that clip's 6-vs-8 undercount may be partly this
  same side-consistency bug and partly something about the Lost/Resume
  transition itself; no landmark CSV was preserved for that specific run
  (overwritten before this investigation started), so it's not diagnosed
  here. Worth a fresh capture-and-check once the side-consistency fix is in,
  to see how much of the gap it closes.
- Two Position Check UX issues surfaced by the owner while live-testing,
  unrelated to rep-counting accuracy itself — noted here so they aren't
  lost, not designed or scoped:
  - "Start anyway" (`PositionCheckScreen.kt`) only appears after
    `BeforeYouStartEngine.START_ANYWAY_AFTER_SECONDS`, by which point the
    Athlete is deliberately ~2m back to pass the distance check — reaching
    the phone to tap it defeats the point of having stepped back.
  - The Position Check's top banner ("Put your phone down, then step back
    until your whole body fits the outline.") is a static `Text`, always
    shown regardless of current pass/fail state (`PositionCheckScreen.kt`
    line ~136) — it never reflects that the checks below it have actually
    passed, which reads as the check being stuck even when it isn't.
  - Both are real findings from this session but need their own spec — not
    folded into this one, which is scoped to rep-counting accuracy.

## Comments
