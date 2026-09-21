Status: done

# 02 — Rep Counting Engine (Seam 1)

## Scope

Pure Kotlin, no Android framework or camera dependency (`core-rep-counting` module). Covers user stories 9, 11, 16 from `spec.md`.

- One state machine per Exercise: Squat, Push-up, Sit-up, Lunge, Jumping Jack.
- Each state machine consumes a stream of pose-landmark frames and emits Rep events.
- Each Exercise defines its own range-of-motion/angle threshold; a Rep is checked against it to determine whether it counts toward the Set's Form Score (per `CONTEXT.md`'s Form Score definition — Quick Count runs skip this gating, see ticket 11).
- Exact per-Exercise angle thresholds aren't specified in the spec — pick reasonable defaults per Exercise from standard form cues (e.g. knee angle at bottom of a Squat) and record them in code comments; this is expected to be tuned later against real device data, not a blocker.

## Testing

Per spec's Testing Decisions: unit tests driven by fixture landmark-frame sequences per Exercise —

- a full valid rep cycle
- a partial/no-rep cycle
- a below-threshold-form cycle

asserting on Rep count and resulting Form Score. No CameraX, MediaPipe runtime, or UI involved. These are the first tests in the repo — they set the pattern later work follows.

## Depends on

Nothing (deliberately independent of ticket 01's Android modules beyond the module existing).

## Out of scope

Camera/MediaPipe integration (ticket 03), beep/UI feedback (ticket 09), Quick Count's no-Form-Score variant (ticket 11 — reuses this engine).

## Comments

Implemented in commit `d7542cf` (feat: rep counting engine (ticket 02, Seam
1)). 21 tests passing (`./gradlew :core-rep-counting:test`); full project
build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one gap — FormScore
wasn't exercised end-to-end via the fixtures — closed) and the repo's ADRs/
CONTEXT.md/spec.md (Standards: no hard violations; four judgement-call
smells addressed).

Angle thresholds per Exercise are placeholder defaults (see
`ExerciseProfiles.kt` comments) — expected to be tuned later against real
device data, per this ticket's own note. Landmark/PoseLandmarkFrame are this
module's own minimal types, not MediaPipe's — ticket 03 maps onto them.

Ticket 09 (session flow UI) and 11 (quick count) depend on this and are now
unblocked on this piece (both also need tickets 03 and 06).
