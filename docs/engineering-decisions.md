# Engineering Decisions & Technical Highlights

A recruiter/portfolio-facing overview of Workout Partner's tech stack, architecture,
and the engineering decisions behind it. See `CONTEXT.md` for the product's own
domain language, and `docs/adr/` for the full, unabridged decision records this
document summarizes.

## What it is

An Android app that uses **on-device pose estimation** to count exercise reps and
judge form in real time — for people working out alone, and for people counting
reps on someone else's behalf (Quick Count). No exercise is ever sent to a server
to be scored: the camera feed, the ML inference, and the rep-counting logic all run
on the phone.

## Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 2.4 | Sole language across a 5-module Gradle project |
| UI | Jetpack Compose (2026.09 BOM) | Declarative UI for all screens |
| Camera | CameraX 1.6 | Lifecycle-aware camera binding, front-camera preview + analysis |
| On-device ML | MediaPipe Tasks Vision (Pose Landmarker) | 33-point body pose estimation, `LIVE_STREAM` mode for camera, `VIDEO` mode for offline replay |
| Local persistence | Room 2.8 | On-device source of truth; offline-first (see below) |
| Background work | WorkManager | Deferred sync to the backend |
| Backend | Firebase Auth + Firestore | Shared backend for a separate companion Web dashboard project |
| Concurrency | Kotlin Coroutines + Flow | Structured concurrency throughout the pose-tracking and sync pipelines |
| Build | Gradle version catalogs (`libs.versions.toml`), KSP | Multi-module, versions pinned deliberately (see the file's own header comment) |
| Testing | JUnit, Robolectric (in-memory Room DB, no emulator needed) | Fast, device-independent test suite; classes that genuinely need a real device/camera/decoder are explicitly marked not-unit-tested rather than faked |

**Module structure**: `app` (UI/navigation), `core-pose-tracking` (camera + video
decode + MediaPipe wiring), `core-rep-counting` (pure rep-detection state
machines), `core-streaks` (streak/target logic), `data` (Room + Firestore). The
rep-counting and streak modules are deliberately pure Kotlin with no Android
framework dependency, so their logic is unit-tested without any device at all —
only the thin adapters around a camera, a decoder, or a database need the
device-dependent exceptions noted above.

## Architecture decisions (condensed from `docs/adr/`)

These were made explicitly, as written decision records, each with the
alternative considered and why it lost:

- **Firebase over Supabase/custom backend**: two independent client codebases
  (Android + Web) need one shared backend; Firebase's SDK family gives both
  built-in offline persistence out of the box, which the offline-first design
  below depends on. Trade-off: lock-in to Firebase's data model and pricing.
- **Offline-first, Room as source of truth**: pose tracking runs entirely
  on-device, so requiring a live connection just to *save* a result would make
  the app worse than the camera work it's built on. Completed Sets/Tallies
  write to Room immediately and sync to Firestore in the background.
- **Single-person pose tracking (v1)**: multi-person pose estimation
  (per-person detection, tracking, re-identification) is a materially heavier
  pipeline, not an incremental extension of single-person tracking — so Quick
  Count's UX (one Tracked Profile active at a time) follows the model's real
  constraint rather than fighting it.
- **All camera-based tracking stays in the Android app**; the Web project is a
  read-only companion dashboard. Porting pose tracking to a browser webcam flow
  is a distinct engineering effort with different camera APIs and performance
  characteristics — duplicating rep-counting logic across two codebases was
  rejected outright.
- **Guests get full feature parity, data stays local until sign-up migrates
  it** — rejected Firebase Anonymous Auth (would've required Firebase
  configured everywhere, including a local-only build variant that doesn't
  have it) in favor of a one-time local→cloud claim step.
- **Streak is weekly-target-based, not a daily streak** — a strict daily
  requirement punishes normal rest days; a Weekly Target + Streak Shield
  mechanic rewards a realistic weekly rhythm instead, with a same-week gap rule
  so it can't be gamed by cramming.
- **Security rules scoped by field, not just by collection** — Firestore rules
  let an owner write their own Sessions/Profile fields but keep Sets and
  Tallies create-only/immutable, and lock Streak fields to a Cloud Function
  once one exists, so the records that matter most for integrity (Sets,
  Streak) can't be edited by any client, ever.

## Case study: rebuilding the debug video-decode pipeline

The pose-tracking engine has a debug-only mode (`VideoPoseTracker`) that replays
a recorded clip through the exact same MediaPipe → rep-counting →
Form-Score pipeline the live camera uses, so tracking accuracy can be tested and
compared against a known ground truth without a person standing in front of a
camera. This became a real, multi-day debugging exercise:

**The problem.** The original implementation called
`MediaMetadataRetriever.getFrameAtTime(..., OPTION_CLOSEST)` once per sampled
timestamp. Frame-accurate seeking forces the decoder back to the last keyframe
and forward again on *every single call* — an 8x-real-time clip took over 10
minutes to analyze, and a meaningful fraction of those seeks returned no frame
at all, leaving gaps of hundreds of milliseconds in the sampled data. On a real
test clip (35 known reps), this undercounted to 5.

**The investigation.** Rather than guess, the actual captured landmark data was
replayed offline through the production rep-counting pipeline to confirm the
undercount was a *data-availability* problem (dropped frames), not a
counter-tuning problem — then research (not just prior knowledge) established
that neither `MediaMetadataRetriever` nor its modern Media3 successor
(`FrameExtractor`) fit a dense/sequential sampling pattern: both are
fundamentally seek-based, and even ExoPlayer's own `seekTo` still re-decodes
from the last keyframe on a small forward seek.

**The fix, and three real device-specific failures along the way.** The
correct fix is a genuine single-pass sequential decode: raw `MediaExtractor` +
`MediaCodec`, decoding the clip once, front to back. Building that against a
real device surfaced three distinct native crashes in a row, each ruling out
part of the design space:

1. Hardware decoder output → `ImageReader`'s CPU plane read: **crashed the
   whole process** reading `Image.getPlanes()` — hardware decoders are free to
   write compressed/tiled buffer layouts (Qualcomm's UBWC) that aren't safely
   readable as plain YUV planes on the CPU side.
2. Forcing a software decoder to sidestep that: a *different* crash negotiating
   the `ImageReader` surface's buffer usage flags.
3. Software decoder, no `Surface` at all (`MediaCodec.getOutputImage`, a
   ByteBuffer-mode readback): the software HEVC decoder component itself
   fatally errored on the real camera-recorded content, within ~1ms of
   starting — a known robustness gap in AOSP's reference software decoder
   relative to vendor hardware decoders tuned for their own camera's output.

The key diagnostic insight: failure #1 showed the **hardware decoder never
failed to decode** — only the CPU-side pixel readback did. The final
architecture goes back to the hardware decoder, but replaces `ImageReader`
with **`SurfaceTexture` + a small OpenGL ES pipeline** (`GlFrameReader`: EGL
context on a dedicated thread, a pass-through shader sampling the decoder's
external texture, an off-screen framebuffer, `glReadPixels`) — the same
technique a `TextureView` video preview uses internally, which handles a
vendor decoder's native buffer layout correctly because a GPU texture sampler,
unlike a CPU memory-map, doesn't need the buffer to be in a plain
uncompressed layout at all.

A second, subtler bug was found the same way afterward: the on-screen
skeleton overlay came out rotated 90°. Rather than re-derive the fix from
matrix algebra, a decoded frame was dumped to a PNG and inspected directly —
confirming the *image* was fine, isolating the bug to an unnecessary manual
rotation. Root cause: `SurfaceTexture.getTransformMatrix()` already bakes in
the container's rotation hint for `Surface`-targeted decode (unlike the
ByteBuffer-mode approach that came before it), so applying it a second time
by hand double-rotated every frame.

**Result**: decode time for a 37-second real test clip went from ~11 minutes
to real-time, with zero dropped-frame gaps (566 analyzed frames vs. 282
before, both at ~15 fps sampling), verified by re-running the exact scenario
on the physical test device before and after each fix.

## Known gaps / robustness roadmap

Identified but not yet built, worth being upfront about:

- **No `keepScreenOn` during an active tracking screen** — the display can
  time out mid-Session the same way it would on any other screen; a real
  workout app needs to request this explicitly, the OS doesn't grant it for
  free just because the camera is open.
- **No Foreground Service around camera use.** Android 10+ can reclaim the
  camera or kill the process if the app leaves the foreground while tracking —
  a real video-call-grade app declares a `foregroundServiceType="camera"`
  service with a persistent notification for exactly this reason.
- **No camera-lost retry/reconnect path.** `CameraPoseTracker` already catches
  startup failures and reports them through an `errors` stream instead of
  crashing, but there's no logic yet to recover if the camera is lost
  *mid-session* (another app claiming it, a transient driver error, thermal
  throttling).
- **No thermal-adaptive quality reduction** — a long tracking session at full
  resolution/frame rate has no fallback if the device starts thermal
  throttling.

These map to a natural next engineering effort, not filed as tickets yet.
