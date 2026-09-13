Status: ready-for-agent

# 01 — Project scaffold

## Scope

Stand up the Android project so later tickets have somewhere to land:

- Gradle multi-module setup (Kotlin, Compose). Module split should mirror the seams in `spec.md`:
  - `core-pose-tracking` (no Android framework dependency beyond CameraX/MediaPipe bindings)
  - `core-rep-counting` (pure Kotlin — Seam 1, no Android/camera dependency at all)
  - `core-streaks` (pure Kotlin — Seam 2, no persistence/clock dependency)
  - `data` (Room + Firestore sync — Seam 3)
  - `app` (Compose UI, DI wiring, WorkManager/AlarmManager)
- Min SDK ~26 per spec's "Further Notes" (flagged there as not yet validated against a real device matrix — carry that caveat forward, don't treat it as settled).
- Dependencies: CameraX, MediaPipe Tasks Vision (Pose Landmarker), Room, Firebase Auth + Firestore, Jetpack Compose, WorkManager.
- Baseline app shell that builds and launches to an empty screen — no feature logic yet.

## Depends on

Nothing — this is the foundation.

## Out of scope

Any feature logic. CI/lint config beyond what's needed to build.
