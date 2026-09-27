# 02: Foreground Service while camera tracking is active

**What to build:** A `foregroundServiceType="camera"` Foreground Service that
runs for as long as `CameraPoseTracker` has a camera bound, so Android 14+'s
background camera-use restrictions don't reclaim the camera or kill the
process if the app briefly leaves the foreground mid-Session (an incoming
call, a notification tap-away, a split-screen swap). See `../spec.md` for the
exact manifest/runtime shape this needs, cited against current Android docs.

**Blocked by:** None.

**Status:** ready-for-agent (code-complete; on-device verification outstanding, see Comments)

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
- [ ] Verified on the real device: the notification appears when tracking
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
code) rather than a clean cut. `Status` stays as above, not `done`, until
the on-device checkbox is actually ticked.
