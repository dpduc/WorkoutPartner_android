Status: done

# 11 — Quick Count / Roster

## Scope

Covers user stories 34-41. Per `CONTEXT.md`'s Quick Count/Tally/Tracked Profile/Roster definitions and ADR-0003.

- Roster CRUD: create/list Tracked Profiles (name only, no login/Account of their own; not visible to the person represented).
- Quick Count run: pick a Tracked Profile + an Exercise, optional target count, run the camera.
- Reuses the Rep Counting Engine (ticket 02) but skips Form Score gating entirely — Quick Count Tallies are raw counts only (no Form Score field populated).
- Auto-stop when the optional target is reached; manual stop always available.
- Enforce single-person-in-frame per ADR-0003 — if the Pose Tracking Engine (ticket 03) surfaces multiple candidate people, Quick Count should refuse to count rather than guess which is the Tracked Profile.
- Saves each run as a Tally against the Tracked Profile (ticket 05/06 for persistence).
- Quick Count activity must NOT count toward the Account's own Streak/Active Days (story 41) — make sure ticket 04/06's Active Day derivation only looks at Sets, never Tallies.

## Depends on

Ticket 02 (Rep Counting Engine), ticket 03 (Pose Tracking Engine, for the single-person-in-frame signal), ticket 06 (repository layer, for Tally/Roster persistence).

## Out of scope

Any Tracked Profile self-visibility or "claim your history" flow — explicitly out of scope per spec.

## Comments

Implemented in `app`: pure `QuickCountEngine` (reuses `RepCounter` exactly
like `SessionEngine` does, but never reads `RepEvent.passedFormThreshold`
— raw counts only, per spec story 38) with its own tests (rep counting with
no form gating, auto-stop at target, unlimited count with no target,
manual stop, Lost/Trackable auto-resume). `QuickCountViewModel` is the
impure glue (camera stream, saves exactly one Tally once finished) — same
"impure shell" split as `SessionViewModel`. `RosterScreen` (create/list
Tracked Profiles), `QuickCountSetupScreen` (Exercise + optional target),
`QuickCountRunScreen`, and `TallyHistoryScreen` (story 39 — the Account
holder's own view of a Tracked Profile's history, not the Tracked Profile's
self-visibility, which stays correctly out of scope) are wired into
`MainActivity`'s navigation behind a new "Roster" button, hidden for a
Guest (`accountId == null`) since stories 34-41 are all Account-holder only.

Single-person-in-frame (ADR-0003) needed a real fix, not just documentation:
an earlier draft's `QuickCountEngine` doc comment claimed this was "enforced
by construction" via `core-pose-tracking` being "configured single-pose
only," but `CameraPoseTracker.createPoseLandmarker()` never actually called
`setNumPoses(1)` — the single-pose behavior was an unstated MediaPipe
default, with only `PoseLandmarkerResultMapping.toRawLandmarks()`'s
`firstOrNull()` as a real (if softer) guard. Both review passes caught
this. Fixed by adding the explicit `setNumPoses(1)` call (with an ADR-0003
comment) to `CameraPoseTracker`, and correcting the doc comment to describe
both mechanisms accurately instead of overstating one that didn't exist yet.

7 tests passing (`./gradlew :app:test`, 24 total in the module, all in
`QuickCountEngineTest`). Full project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: the single-person
gap above, plus story 39's "review someone's history later" being only
half-done — Tallies were saved but nothing read them back; added
`TallyHistoryScreen`) and the repo's ADRs/CONTEXT.md/spec.md (Standards: one
hard violation — the inaccurate ADR-0003 enforcement claim, fixed as
described above; noted but left for a follow-up ticket — `Exercise` display-
name formatting is now duplicated across `session`/`progress`/`quickcount`
packages, worth promoting to a shared helper rather than blocking this
ticket on it).

Known gaps carried forward, same as ticket 09: `CameraPoseTracker` still
needs the MediaPipe model asset and `google-services.json` before this runs
on a device — no emulator was available this session to verify beyond
compilation and the pure-engine tests.
