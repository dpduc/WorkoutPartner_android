Status: ready-for-agent

# 03 — Pose Tracking Engine

## Scope

Wraps MediaPipe Pose Landmarker over CameraX (front camera). Covers user stories 8, 12, 13.

- Emits a stream of pose-landmark frames for the Rep Counting Engine (ticket 02) to consume. No rep-counting logic lives in this module — per spec's module boundary.
- Detects loss of tracking (subject steps out of frame or is occluded): pause emitting a "trackable" state, surface a signal the UI can turn into the warning banner (see ticket 09) — per spec's "Lost tracking mid-Set" interaction, no partial Rep should be counted for the gap.
- Auto-resumes on landmark re-detection without requiring the Set to restart.

## Depends on

Ticket 01 (project scaffold, for the CameraX/MediaPipe dependencies). Also,
as implemented: ticket 02 (rep counting engine), for its
Landmark/PoseLandmarkFrame/Point3D types — per `Landmark.kt`'s own doc
comment, mapping MediaPipe's per-side output onto those generic joints is
this ticket's job, which means constructing ticket 02's actual types rather
than duplicating them. Not listed here originally; added after the fact once
implementation made the dependency direction concrete (core-pose-tracking ->
core-rep-counting, non-circular — core-rep-counting depends on nothing).

## Out of scope

Rep-counting logic (ticket 02), the warning banner UI itself and countdown (ticket 09), multi-person tracking (explicitly out of scope per ADR-0003 for the whole app).

## Comments

Implemented in `core-pose-tracking`: `PoseFrameMapper` maps MediaPipe's
left/right landmarks onto core-rep-counting's generic
Landmark/PoseLandmarkFrame (picking whichever side clears a visibility/
presence threshold with higher confidence — naturally favors a Lunge's front
leg since it reads more visible than the back one, per `Landmark.kt`'s
example); `TrackingStateMachine` debounces per-frame detection into
Trackable/Lost (needs several consecutive frames with too few tracked
landmarks — not just a fully empty detection, so a mostly-occluded subject
also surfaces Lost — before flipping, and a couple of good frames before
resuming, so single dropped/lucky frames don't flap the signal); no partial
Rep can be counted for the gap because no frame reaches the Rep Counting
Engine while Lost. `CameraPoseTracker` is the actual CameraX (front camera,
mirrored/rotated to upright) + MediaPipe Pose Landmarker (LIVE_STREAM mode)
wiring behind the `PoseTracker` interface ticket 09/11 consume.

11 tests passing (`./gradlew :core-pose-tracking:test`), all against the pure
mapping/debounce logic — no MediaPipe runtime or CameraX involved, same
"first tests set the pattern" spirit as ticket 02. Full project build
(`./gradlew clean assembleDebug testDebugUnitTest`) is green.

Reviewed via `/code-review` against this ticket (Spec: two gaps found and
addressed — the "Depends on" section above was stale once ticket 02 became
an actual build dependency, now corrected; and Lost originally only fired on
a fully empty detection, not partial occlusion, now fixed via the
minimum-tracked-landmarks threshold) and the repo's ADRs/CONTEXT.md/spec.md
(Standards: no hard violations; addressed the "Repeated Switches" smell in
`TrackingStateMachine.accept` by collapsing two `when(state)` blocks into
one).

Known gap, not fixed by this change: the MediaPipe `pose_landmarker_lite.task`
model file isn't bundled under `core-pose-tracking/src/main/assets/` yet —
it's a binary to provision (from MediaPipe's model zoo), not code, so it's
left as an outstanding step rather than faked, the same way ticket 01 left
`google-services.json` for later. `CameraPoseTracker` (the CameraX/MediaPipe
glue itself) compiles and the full build is green, but wasn't exercised
against a real camera/device — no emulator/device was available in this
session, same caveat ticket 01 raised.

Tickets 09 (session flow UI) and 11 (quick count) depend on this and remain
blocked on ticket 06 (repository layer) for full Session/Tally persistence,
but are now unblocked on the pose-tracking piece.
