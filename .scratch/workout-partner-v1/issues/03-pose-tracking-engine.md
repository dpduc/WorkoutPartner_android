Status: ready-for-agent

# 03 — Pose Tracking Engine

## Scope

Wraps MediaPipe Pose Landmarker over CameraX (front camera). Covers user stories 8, 12, 13.

- Emits a stream of pose-landmark frames for the Rep Counting Engine (ticket 02) to consume. No rep-counting logic lives in this module — per spec's module boundary.
- Detects loss of tracking (subject steps out of frame or is occluded): pause emitting a "trackable" state, surface a signal the UI can turn into the warning banner (see ticket 09) — per spec's "Lost tracking mid-Set" interaction, no partial Rep should be counted for the gap.
- Auto-resumes on landmark re-detection without requiring the Set to restart.

## Depends on

Ticket 01 (project scaffold, for the CameraX/MediaPipe dependencies).

## Out of scope

Rep-counting logic (ticket 02), the warning banner UI itself and countdown (ticket 09), multi-person tracking (explicitly out of scope per ADR-0003 for the whole app).
