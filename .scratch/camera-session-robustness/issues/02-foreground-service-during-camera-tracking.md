# 02: Foreground Service while camera tracking is active

**What to build:** A `foregroundServiceType="camera"` Foreground Service that
runs for as long as `CameraPoseTracker` has a camera bound, so Android 14+'s
background camera-use restrictions don't reclaim the camera or kill the
process if the app briefly leaves the foreground mid-Session (an incoming
call, a notification tap-away, a split-screen swap). See `../spec.md` for the
exact manifest/runtime shape this needs, cited against current Android docs.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] Manifest gains `android.permission.FOREGROUND_SERVICE` and
      `android.permission.FOREGROUND_SERVICE_CAMERA`, plus a `<service>`
      declaration with `android:foregroundServiceType="camera"`.
- [ ] A small service class starts (`ServiceCompat.startForeground(...,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)`) when
      `CameraPoseTracker.start()` successfully binds the camera, and stops
      when `CameraPoseTracker.stop()` is called — tie the service's lifetime
      to the tracker's, not to any one screen, so it survives Quick Count's
      Position Check → Run transition (two different tracker instances,
      ticket 14) without a visible gap.
- [ ] A persistent notification (channel + content) is shown while the
      service runs, following the same permission-request pattern this repo
      already has for `POST_NOTIFICATIONS` (ticket 12's daily reminder) —
      reuse that flow rather than inventing a second one. Copy: something
      like "Tracking your workout" / "Camera is active while you train" is
      fine as a starting point; not a blocking design decision.
- [ ] `VideoPoseTracker` does **not** start this service — it has no real
      camera to protect, and starting a camera-typed Foreground Service for
      it would misrepresent what it's doing.
- [ ] The service is properly stopped in every path that ends tracking:
      normal Set/Quick Count completion, the user manually stopping, and
      navigating away entirely (not just the happy path) — verify there's no
      way to strand the service running (and its notification showing) after
      tracking has actually ended.
- [ ] If `startForeground()` throws (missing permission granted at runtime,
      or any other `SecurityException`), it's caught and reported through
      `CameraPoseTracker`'s existing `errors` Flow rather than crashing —
      same defensive pattern `start()` already uses for camera-bind
      failures.
- [ ] Typecheck and the full test suite pass. Service lifecycle wiring is
      not meaningfully unit-testable without a real device/OS — verify
      on-device instead, same caveat as `CameraPoseTracker` itself.
- [ ] Verified on the real device: the notification appears when tracking
      starts and disappears when it stops; switching away from the app
      mid-Session (e.g. pressing Home, or receiving a call) and back does
      not lose tracking or crash the app.

## Comments
