Status: ready-for-agent

# Camera session robustness: keep-screen-on, Foreground Service, camera reconnect

## Problem statement

Every camera-active screen (`BeforeYouStartScreen`'s Position Check,
`SessionScreens`' Set tracking, `QuickCountScreens`' Quick Count run) opens
`CameraPoseTracker` and keeps it running for minutes at a time, with the phone
typically propped up and not touched. Right now:

- Nothing keeps the screen from timing out mid-Session — the OS applies the
  device's normal screen-off timeout exactly as it would on any other screen,
  camera open or not.
- Nothing declares a Foreground Service around camera use, so if the app
  briefly leaves the foreground while tracking (an incoming call, a
  notification tap-away, a split-screen swap), Android's background execution
  limits can reclaim the camera or kill the process outright — this is not a
  hypothetical: `CameraPoseTracker.start()` already has to catch and report
  camera *startup* failures (`errors` Flow) rather than crash, but nothing
  detects or recovers from the camera being lost *after* a successful start.
- `CameraPoseTracker` has no camera-state monitoring at all once bound —
  `analyzeFrame` just silently stops being called if the camera drops, with
  no distinction between "person walked out of frame" (normal,
  `TrackingStateMachine`'s job) and "the camera itself is gone."

Video-call-class apps handle all three of these as a matter of course; this
app doesn't yet. See `docs/engineering-decisions.md`'s "Known gaps" section,
which first named this gap.

## Research (what real apps actually do, verified against current Android/CameraX docs)

- **Keep screen on**: the modern, Compose-native mechanism is
  `Modifier.keepScreenOn` (`androidx.compose.ui:ui`, stable since 1.9.0 —
  this repo's pinned Compose BOM, `2026.09.00`, is well past that). Applied
  to a composable, it prevents the screen from sleeping for as long as that
  composable is part of the composition — no manual flag bookkeeping needed,
  and it stops applying automatically once the composable leaves
  composition (e.g., navigating away), which is exactly the "only while this
  screen is active" behavior wanted here. (Android's own guidance: the
  screen-on effect is inherently tied to being in the foreground — the
  system already allows the screen to sleep the moment the app itself goes
  to the background, so no separate app-background handling is needed on
  top of this.) Source:
  [Android: Keep the screen on](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).
- **Foreground Service for camera**: required, not optional, from Android 14
  (API 34) onward for an app to keep using the camera once it's no longer
  the foreground-focused app. Needs, together:
  - Manifest: `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
    and `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />`,
    plus a `<service android:foregroundServiceType="camera">` declaration.
  - The `CAMERA` runtime permission must already be granted (it already is,
    by the time any of these screens opens a tracker).
  - Starting it: `ServiceCompat.startForeground(this, notificationId,
    notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)` (androidx-core
    1.12+, already >= this repo's pinned `coreKtx` 1.19.0), which is the
    version-safe wrapper over the platform API and requires a
    `Notification` — this repo already has a working
    `POST_NOTIFICATIONS`-permission flow (ticket 12's daily reminder) to
    follow the same pattern from.
  - Missing the type permission or declaration throws `SecurityException` at
    `startForeground()` — this fails loudly, not silently, if misconfigured.
  Source:
  [Android 14: foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required).
- **Camera-loss detection and reconnect**: CameraX exposes
  `CameraInfo.getCameraState()` as a `LiveData<CameraState>` — `CameraState.Type`
  includes `OPEN`/`OPENING`/`CLOSING`/`CLOSED`/`PENDING_OPEN`, and on failure a
  `CameraState.StateError` carries an error code distinguishing recoverable
  conditions (e.g. another app briefly claimed the camera) from fatal ones
  (e.g. the camera device itself failed). The documented recovery shape is:
  observe this state, and on a recoverable error, `unbindAll()` then retry
  `bindToLifecycle()` — the same call `start()` already makes, just retried
  rather than one-shot. Retries must be **bounded with backoff, not
  infinite** — CameraX's own issue tracker documents real apps getting stuck
  in an open/error/reopen loop when this isn't bounded. After retries are
  exhausted, surface a permanent failure through the existing `errors` Flow
  instead of silently going dark. Sources:
  [`CameraState` API reference](https://developer.android.com/reference/androidx/camera/core/CameraState),
  [`CameraState.StateError` API reference](https://developer.android.com/reference/androidx/camera/core/CameraState.StateError).
  The exact `StateError` code constants should be re-checked against this
  repo's pinned CameraX version (`cameraX = "1.6.2"`) while implementing —
  the research above confirms the API shape and pattern, not a verbatim list
  of every code for that exact version.

## Scope

Three independently implementable tickets, in the order they're numbered
(no hard dependency, but 03 is easiest to verify once 02 already has a
Foreground Service to log/notify through):

- **01**: `Modifier.keepScreenOn` on every camera-active screen.
- **02**: Foreground Service (`foregroundServiceType="camera"`) while any
  `PoseTracker` is running, tied to `CameraPoseTracker.start()`/`stop()` (not
  `VideoPoseTracker`, which has no real camera to protect).
- **03**: `CameraPoseTracker` observes `CameraInfo.cameraState`, retries a
  recoverable camera loss with bounded backoff, and reports a permanent loss
  through `errors` instead of silently going dark.

## Out of scope

- Thermal-adaptive quality reduction (a separate, later gap noted in
  `docs/engineering-decisions.md`, not part of this spec).
- `VideoPoseTracker` (the debug video-replay path) — it has no real camera to
  lose, no foreground-service concern, and already applies its own
  `keepScreenOn`-equivalent reasoning not at all relevant (it drives its own
  pacing via `delay`, independent of the screen).
- Any change to `TrackingStateMachine`'s existing Lost/Resume debounce for a
  person stepping out of frame — that is normal, expected tracking behavior,
  not a camera failure, and is not touched by ticket 03.
