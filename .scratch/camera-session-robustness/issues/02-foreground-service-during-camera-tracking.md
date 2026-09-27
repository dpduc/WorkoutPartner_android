# 02: Foreground Service while camera tracking is active

**What to build:** A `foregroundServiceType="camera"` Foreground Service that
runs for as long as `CameraPoseTracker` has a camera bound, so Android 14+'s
background camera-use restrictions don't reclaim the camera or kill the
process if the app briefly leaves the foreground mid-Session (an incoming
call, a notification tap-away, a split-screen swap). See `../spec.md` for the
exact manifest/runtime shape this needs, cited against current Android docs.

**Blocked by:** None.

**Status:** done

- [x] Manifest gains `android.permission.FOREGROUND_SERVICE` and
      `android.permission.FOREGROUND_SERVICE_CAMERA`, plus a `<service>`
      declaration with `android:foregroundServiceType="camera"`.
- [x] A small service class starts (`ServiceCompat.startForeground(...,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)`) when
      `CameraPoseTracker.start()` successfully binds the camera, and stops
      when `CameraPoseTracker.stop()` is called — tie the service's lifetime
      to the tracker's, not to any one screen, so it survives Quick Count's
      Position Check → Run transition (two different tracker instances,
      ticket 14) without a visible gap.
- [x] A persistent notification (channel + content) is shown while the
      service runs, following the same permission-request pattern this repo
      already has for `POST_NOTIFICATIONS` (ticket 12's daily reminder) —
      reuse that flow rather than inventing a second one. Copy: something
      like "Tracking your workout" / "Camera is active while you train" is
      fine as a starting point; not a blocking design decision.
- [x] `VideoPoseTracker` does **not** start this service — it has no real
      camera to protect, and starting a camera-typed Foreground Service for
      it would misrepresent what it's doing.
- [x] The service is properly stopped in every path that ends tracking:
      normal Set/Quick Count completion, the user manually stopping, and
      navigating away entirely (not just the happy path) — verify there's no
      way to strand the service running (and its notification showing) after
      tracking has actually ended.
- [x] If `startForeground()` throws (missing permission granted at runtime,
      or any other `SecurityException`), it's caught and reported through
      `CameraPoseTracker`'s existing `errors` Flow rather than crashing —
      same defensive pattern `start()` already uses for camera-bind
      failures.
- [x] Typecheck and the full test suite pass. Service lifecycle wiring is
      not meaningfully unit-testable without a real device/OS — verify
      on-device instead, same caveat as `CameraPoseTracker` itself.
- [x] Verified on the real device: the notification appears when tracking
      starts and disappears when it stops; switching away from the app
      mid-Session (e.g. pressing Home, or receiving a call) and back does
      not lose tracking or crash the app.

## Comments

Implemented via `CameraTrackingService` (the actual `Service`) and
`CameraTrackingSession` (a reference-counted singleton owning start/stop
decisions, with a 2s grace-period delayed stop so Quick Count's Position
Check → Run handoff doesn't flicker the notification off and back on),
wired into `CameraPoseTracker` through the `onCameraSessionStarted`/
`onCameraSessionStopped` constructor callbacks and into `AppContainer`
(the composition root — `core-pose-tracking` never depends on these
app-module classes directly).

**Code-reviewed together with ticket 03** (`/code-review`, Standards +
Spec sub-agents, fixed point `HEAD` = `762a5157`) — done as one combined
pass because ticket 03 was implemented on top of this ticket's
already-uncommitted `CameraPoseTracker.kt` changes, so the two diffs were
entangled in the working tree regardless. One real bug the Spec axis
caught here: `CameraTrackingSession.startFailureListener` was a single
global field reassigned on *every* `trackerStarted` call, not just the one
that actually triggers `startForegroundService()` — a tracker joining an
already-running session (the Position Check → Run handoff this ticket's
own checklist calls out) could silently steal the failure callback from
whichever tracker's `startForeground()` call is actually still pending.
Fixed: the assignment now only happens on the `activeCount == 1`
transition, the one call that can actually fail. See ticket 03's Comments
for the fuller review writeup (both tickets' findings were reported
together).

Standards axis also flagged (as judgement-call smells, not fixed here):
the two callback params on `CameraPoseTracker`'s constructor reading as a
Data Clump / their no-op defaults as Speculative Generality (only ever
wired one way, from `AppContainer`), and `CameraPoseTracker` itself
picking up a third reason to change (camera bind, session-lifecycle
signaling, now also ticket 03's reconnect policy) as a Divergent Change
candidate for a future split. Deliberately left for the combined
all-three-tickets review this spec's own note already asks for, rather
than reshaping this ticket's design in isolation.

Still outstanding: on-device verification (last checkbox) — deferred by
the user ("let's skip it for now, next time").

**Committed anyway**, on the owner's explicit call when asked (no `adb`
device reachable at commit time) — chose "commit it as-is now" over
waiting to reconnect one. Committed together with ticket 03 in one commit,
not two: their `CameraPoseTracker.kt` changes are interleaved line-by-line
inside the same `bindCamera`/`stop` methods (ticket 02's session-callback
firing sits next to ticket 03's retry/observer cleanup in both), reviewed
together as one diff already — splitting it now would mean undoing that
review's own fix (the `startFailureListener` race spanned both tickets'
code) rather than a clean cut.

**On-device verification (Samsung SM-S938B, physical device over USB)**,
the session after the commit above:

- Notification appears the moment a Quick Count run's camera binds
  (`dumpsys notification` showed a live `channel=camera_tracking` record,
  and the shade itself showed "Tracking your workout" / "Camera is active
  while you train." — exactly this ticket's suggested copy), and
  `dumpsys activity services` confirmed `isForeground=true` with that same
  notification attached to `CameraTrackingService`.
- Backgrounding mid-Session (Home) and returning: confirmed via logcat that
  CameraX's own `bindToLifecycle` closes the camera on `onStop()`
  (`CombinedCameraState(state=CLOSED, error=null)`) and reopens it cleanly
  on `onStart()` (`state=OPEN, error=null`) with no crash and no manual
  restart — the rep counter's value survived the round trip unchanged. The
  Foreground Service itself never stopped across this (still
  `isForeground=true` throughout), so Android's background-camera
  restriction genuinely never applied here regardless of CameraX's own
  lifecycle-driven pause.
- **Found and fixed a real bug this checklist item's own "verify there's
  no way to strand the service" line asks for**: tapping Quick Count's
  "Stop" then "Done" left the service running indefinitely —
  `dumpsys activity services` still showed `isForeground=true` with the
  notification attached, even back on the Roster screen, minutes later.
  Root cause: `QuickCountViewModel` only ever called `poseTracker.stop()`
  from `onCleared()`, and this app has no back-stack-scoped
  `ViewModelStoreOwner` (`MainActivity`'s screen switch is a plain
  `mutableStateOf<AppScreen>`, not Navigation Compose) — so a `viewModel()`
  call is Activity-scoped and `onCleared()` doesn't fire just from
  navigating away. `SessionScreens.kt` already gets this right for the
  full-Routine path (`DisposableEffect(viewModel) { onDispose { viewModel.release() } }`);
  `QuickCountScreens.kt`'s `QuickCountRunScreen` had no equivalent. Fixed
  by adding a `release()` method to `QuickCountViewModel` (mirroring
  `SessionViewModel`'s) and the same `DisposableEffect` to
  `QuickCountRunScreen`. Re-verified after the fix: `dumpsys activity
  services` shows `(nothing)` immediately after "Done", both right after a
  manual "Stop" and after letting a run finish on its own.
- Typecheck and the full test suite re-run clean after the fix.

**A separate, pre-existing bug was found while investigating the above,
unrelated to this ticket**: running Quick Count a second time in the same
app session (without restarting the app) reused the same, now-stale
`QuickCountViewModel` instance instead of creating a fresh one — no new
camera session ever started, and the screen just redisplayed the previous
run's already-`Finished` state. Same root cause (Activity-scoped
`viewModel()` with no back-stack scoping) as the stranding bug above, but a
different symptom (staleness, not stranding), and a Quick Count feature bug
rather than a camera-session-robustness one — out of this ticket's own
scope, but fixed alongside it anyway since the codebase already had the
exact right pattern on file: `SessionScreens.kt`'s `SessionScreen` already
avoids this same trap with a `key = remember { UUID.randomUUID().toString() }`
per Session (its own comment names this exact failure mode).
`QuickCountRunScreen` never got the same treatment; added the identical key
there. Verified on-device: two Quick Count runs back to back in the same
app session (no restart between them) — the second run now genuinely
starts fresh (a real new Position Check, a real new Foreground Service
instance), instead of instantly showing the first run's stale Tally.
