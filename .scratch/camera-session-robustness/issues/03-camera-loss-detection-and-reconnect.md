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

**Status:** ready-for-agent (code-complete; on-device verification outstanding, see Comments)

- [x] After `bindToLifecycle` succeeds, `CameraPoseTracker` observes the
      bound `Camera`'s `CameraInfo.cameraState`.
- [x] A `CameraState` carrying a recoverable `StateError` triggers a retry:
      `unbindAll()` then re-run the same bind path `start()` already uses,
      with a bounded number of attempts and backoff between them — not an
      unbounded retry loop (CameraX's own issue tracker has real examples of
      apps stuck in an open/error/reopen cycle from an unbounded retry; this
      must not do that).
  - [x] The exact `StateError` codes to treat as recoverable vs. not should
        be checked against this repo's pinned CameraX version
        (`cameraX = "1.6.2"`) while implementing — the spec's research
        confirms the API shape, not a verbatim code list for this exact
        version.
- [x] Once retries are exhausted (or the error is not recoverable to begin
      with), the failure is reported through the existing `errors: Flow<String>`
      — the same channel `start()` already uses for a startup bind failure —
      so the UI can show a message instead of the screen just going dark
      with no explanation.
- [x] A successful reconnect does **not** reset `TrackingStateMachine`'s own
      Lost/Resume debounce state in a way that would double-count or
      mis-signal — a reconnected camera should resume producing frames the
      same way a person stepping back into frame would, not as if tracking
      restarted from zero. (If this constraint turns out to already hold for
      free because `TrackingStateMachine` only reacts to frame content and
      not to camera identity, say so in Comments rather than adding
      unnecessary handling.)
- [x] A person genuinely stepping out of frame (normal, already handled by
      `TrackingStateMachine`) is not mistaken for a camera loss — this
      ticket only reacts to `CameraState`, never to the *content* of
      frames, so this should hold structurally; call it out explicitly in
      Comments once confirmed rather than leaving it implicit.
- [x] Typecheck and the full test suite pass. If any of this logic can be
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

**Exact `StateError` codes for CameraX 1.6.2**, read directly off the
decompiled `camera-core-1.6.2-api.jar` in this repo's own Gradle cache
(`androidx.camera.core.CameraState$StateError.getType()`'s bytecode), not
inferred from docs:

- `ERROR_MAX_CAMERAS_IN_USE` (1), `ERROR_CAMERA_IN_USE` (2),
  `ERROR_OTHER_RECOVERABLE_ERROR` (3) → `RECOVERABLE`.
- `ERROR_STREAM_CONFIG` (4), `ERROR_CAMERA_DISABLED` (5),
  `ERROR_CAMERA_FATAL_ERROR` (6), `ERROR_DO_NOT_DISTURB_MODE_ENABLED` (7),
  `ERROR_CAMERA_REMOVED` (8) → `CRITICAL`.

Better than that: `CameraState.StateError` in this exact version already
exposes a `getType(): CameraState.ErrorType` convenience method that does
this classification itself (`RECOVERABLE`/`CRITICAL`), so
`CameraPoseTracker` never has to hardcode the code list above at all — it
just checks `error.type`. The list is recorded here only as the evidence for
*why* that property means what this ticket needs it to mean on this
specific pinned version, in case a future CameraX bump changes it.

**Retry/backoff logic pulled into a plain-Kotlin seam**, per this ticket's
own suggestion: `CameraRetryPolicy` (new file, `core-pose-tracking/src/main/
kotlin/.../CameraRetryPolicy.kt`) holds zero CameraX types — it only answers
"was this recoverable-error report one too many," the same shape as
`TrackingStateMachine`. Unit-tested in `CameraRetryPolicyTest.kt` (JUnit,
same style as `TrackingStateMachineTest`). Default: 3 attempts, 1s/2s/4s
backoff. `CameraPoseTracker` wires it in: a recoverable error asks the
policy for a delay and reconnects via the same `bindCamera` path `start()`
uses; `null` (budget exhausted) or a non-recoverable error both go through
one `reportPermanentCameraLoss` — emits through `errors`, then calls its own
`stop()` so a dead camera doesn't leave `onCameraSessionStopped` (ticket
02's Foreground Service hook) never firing.

**Both structural claims hold for free, confirmed by reading the code, not
just asserted:**

- `TrackingStateMachine.accept(frame: PoseLandmarkFrame)` takes no camera
  identity or session token of any kind — its entire state is
  `consecutiveTracked`/`consecutiveUntracked`/`state`, driven purely by
  `frame.landmarks.size`. Ticket 03's reconnect path never calls
  `trackingStateMachine` at all (only `handleCameraState`/`bindCamera` do,
  and neither touches it), so a reconnect literally cannot perturb its
  debounce state — there's no seam through which it could.
- Ticket 03's `handleCameraState` is only ever invoked by the
  `CameraInfo.cameraState` `LiveData` observer registered in `bindCamera`;
  `analyzeFrame` (which is what feeds `trackingStateMachine`) is a
  completely separate callback path off `ImageAnalysis.Analyzer`, wired
  once per bind and never touched by ticket 03's code. A person stepping
  out of frame changes what `analyzeFrame` sees, never what
  `cameraState` reports (the camera itself is still `Type.OPEN` the whole
  time) — so the two are structurally incapable of being confused with
  each other, not just unlikely to be in practice.

**Outstanding: on-device verification** (the ticket's last checkbox). Not
done this session — forcing a real recoverable camera loss (e.g. another
camera app briefly grabbing the camera mid-Session) needs a device in hand,
and camera-session-robustness ticket 02's own on-device verification is
already deferred ("let's skip it for now, next time"). Code is typechecked
(`:core-pose-tracking:compileDebugKotlin :app:compileDebugKotlin`) and the
full test suite is green, but this ticket isn't marked `done` yet pending
that device pass — consistent with how ticket 01 was only marked `done`
after both its review and its device verification actually ran.

**Code-reviewed together with ticket 02** (`/code-review`, Standards + Spec
sub-agents in parallel, fixed point `HEAD` = `762a5157`) — one combined
pass rather than two, since ticket 03 was built on top of ticket 02's
already-uncommitted `CameraPoseTracker.kt` changes and the diffs were
entangled in the working tree regardless. Findings and outcome:

- **Fixed — stale `CameraState` observer never removed across retries.**
  The Spec axis caught that `bindCamera`'s own doc comment was wrong: "a
  fresh `Camera` per bind means a fresh `CameraState` LiveData" is true,
  but says nothing about the *previous* bind's LiveData, which doesn't stop
  existing just because a new one was created — it can still fire its own
  CLOSING/CLOSED transition after `unbindAll()`, so the old observer stayed
  registered and observers stacked across retries (bounded by
  `CameraRetryPolicy.DEFAULT_MAX_ATTEMPTS`, but still not what "re-run the
  same bind path" was supposed to mean). Fixed by keeping a single stable
  `Observer<CameraState>` instance plus a reference to the currently
  observed `LiveData`, explicitly `removeObserver`'d before attaching to
  the new bind's LiveData (and in `stop()`).
- **Fixed, but a ticket 02 bug, not a ticket 03 one** —
  `CameraTrackingSession.startFailureListener`'s mis-routing race; see
  ticket 02's own Comments for the description and fix. Recorded here too
  since both tickets were reviewed as one diff.
- **Not fixed, deferred** — the Standards axis's Data Clump / Speculative
  Generality / Divergent Change findings on `CameraPoseTracker`'s shape
  (see ticket 02's Comments) apply to this ticket's own additions too
  (`retryPolicy`, `pendingRetry`, `handleCameraState` are exactly the kind
  of "third reason to change" the Divergent Change finding means).
  Deliberately left for the combined all-three-tickets review this spec's
  own note already asks for.
- Both axes independently confirmed no scope creep and no missing
  checklist items (beyond the explicitly-deferred device-verification
  boxes on both tickets).

After the fixes above: re-typechecked
(`:core-pose-tracking:compileDebugKotlin :app:compileDebugKotlin`, clean)
and re-ran the full test suite (green).

**Committed alongside ticket 02**, on the owner's explicit call, with no
device available to actually run the last checkbox — see ticket 02's
Comments for why `CameraPoseTracker.kt`'s diff couldn't be split between
the two tickets. `Status` stays `ready-for-agent (code-complete...)`, not
`done`, until the on-device pass above actually happens.
