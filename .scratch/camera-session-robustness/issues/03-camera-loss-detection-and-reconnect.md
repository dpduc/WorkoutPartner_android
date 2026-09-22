# 03: Camera-loss detection and bounded reconnect

**What to build:** `CameraPoseTracker` observes `CameraInfo.cameraState`
after a successful bind, and reacts to the camera being lost mid-session
instead of just silently going quiet (`analyzeFrame` stops being called,
with no signal to the rest of the app that anything is wrong). Recoverable
losses get a bounded, backed-off retry; unrecoverable ones surface through
`errors`. See `../spec.md` for the `CameraState`/`StateError` API this is
built on and why retries must be bounded.

**Blocked by:** None (independent of 01/02, though easiest to verify once 02
exists to confirm the Foreground Service doesn't itself mask or interact
with a camera-loss event).

**Status:** ready-for-agent

- [ ] After `bindToLifecycle` succeeds, `CameraPoseTracker` observes the
      bound `Camera`'s `CameraInfo.cameraState`.
- [ ] A `CameraState` carrying a recoverable `StateError` triggers a retry:
      `unbindAll()` then re-run the same bind path `start()` already uses,
      with a bounded number of attempts and backoff between them — not an
      unbounded retry loop (CameraX's own issue tracker has real examples of
      apps stuck in an open/error/reopen cycle from an unbounded retry; this
      must not do that).
  - [ ] The exact `StateError` codes to treat as recoverable vs. not should
        be checked against this repo's pinned CameraX version
        (`cameraX = "1.6.2"`) while implementing — the spec's research
        confirms the API shape, not a verbatim code list for this exact
        version.
- [ ] Once retries are exhausted (or the error is not recoverable to begin
      with), the failure is reported through the existing `errors: Flow<String>`
      — the same channel `start()` already uses for a startup bind failure —
      so the UI can show a message instead of the screen just going dark
      with no explanation.
- [ ] A successful reconnect does **not** reset `TrackingStateMachine`'s own
      Lost/Resume debounce state in a way that would double-count or
      mis-signal — a reconnected camera should resume producing frames the
      same way a person stepping back into frame would, not as if tracking
      restarted from zero. (If this constraint turns out to already hold for
      free because `TrackingStateMachine` only reacts to frame content and
      not to camera identity, say so in Comments rather than adding
      unnecessary handling.)
- [ ] A person genuinely stepping out of frame (normal, already handled by
      `TrackingStateMachine`) is not mistaken for a camera loss — this
      ticket only reacts to `CameraState`, never to the *content* of
      frames, so this should hold structurally; call it out explicitly in
      Comments once confirmed rather than leaving it implicit.
- [ ] Typecheck and the full test suite pass. If any of this logic can be
      pulled into a plain-Kotlin, camera-independent seam (e.g. "given this
      sequence of `CameraState` values, should a retry happen, and after how
      many failures should it give up") it should be unit-tested the same
      way `TrackingStateMachine` already is; the CameraX wiring itself stays
      device-verified only, same as the rest of this class.
- [ ] Verified on the real device: forcing a recoverable camera loss (e.g.
      another camera app briefly grabbing the camera mid-Session) triggers a
      reconnect without the user having to restart tracking manually, and an
      unrecoverable loss surfaces a visible error instead of a silently
      frozen screen.

## Comments
