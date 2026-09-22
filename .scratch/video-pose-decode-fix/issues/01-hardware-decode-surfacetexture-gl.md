# 01: Hardware decode via SurfaceTexture + OpenGL

**What to build:** Replace `VideoPoseTracker`'s current `MediaExtractor` +
software `MediaCodec` + `getOutputImage()` pipeline with hardware decode,
reading frames out via `SurfaceTexture` + OpenGL (external-texture sample +
`glReadPixels`) instead of `ImageReader`'s CPU plane API. See `../spec.md` for
the full diagnosis of why both `ImageReader` (with a hardware decoder) and the
software decoder (in any output mode) fail on real device content.

**Blocked by:** None — diagnosis and decision are complete; this is ready to
implement directly.

**Status:** done

- [x] `VideoPoseTracker` selects a **hardware** decoder again (drop the
      `createSoftwareDecoder`/`isSoftwareOnly` codec selection added while
      diagnosing; `MediaCodec.createDecoderByType(mime)` or equivalent).
- [x] Decoder output goes to a `Surface` backed by a `SurfaceTexture`, not
      `ImageReader`. A GL context/thread renders each available frame: bind the
      `SurfaceTexture`'s `GL_TEXTURE_EXTERNAL_OES` texture, sample it via a
      pass-through shader into an FBO/normal RGBA texture, `glReadPixels` it
      back. (Shipped as a `ByteBuffer`/`Bitmap.copyPixelsFromBuffer` readback,
      not the `IntArray`/`Bitmap.setPixels` path this line originally
      described — that line described an intermediate, never-committed
      version of the pipeline; functionally equivalent, caught in review.)
- [x] ~~`MediaFormat.KEY_ROTATION` handling (manual `Matrix().postRotate(...)`
      after readback) is preserved~~ — **turned out wrong; see Comments.**
      Shipped behavior applies *no* manual rotation: a hardware decoder
      rendering to a `Surface`/`SurfaceTexture` already bakes the container's
      rotation hint into `SurfaceTexture.getTransformMatrix()`, which the
      shader already samples through. Applying it a second time (what this
      line asked for) rotated every frame an extra 90° — found by dumping a
      decoded frame to a PNG and looking at it, on the real device.
- [x] The synchronous decode loop's existing behavior is preserved unchanged:
      `dequeueInputBuffer`/`queueInputBuffer`/`dequeueOutputBuffer` sequencing,
      `nextSampleUs` frame-interval sampling (`FRAME_INTERVAL_MS`), wall-clock
      pacing (`delay(waitMs)`) after each analyzed frame, and
      `currentCoroutineContext().ensureActive()` cancellation checks.
- [x] GL resources (context, textures, FBO, `SurfaceTexture`, `Surface`) are
      released deterministically in the same `finally` block that already
      releases the codec/extractor/landmarker, with no leaks across repeated
      `play()`/`stop()` cycles.
- [x] `PoseTracker`'s interface contract (`signals`, `rawFrames`, `errors`,
      `mirrorsPreview`, `start`, `stop`) is unchanged; `CameraPoseTracker`
      (live camera) is not touched by this change at all.
- [x] Verified on the real device (`R5CY91B7K6E`) against `jumping_jack.mp4`:
      decode completes without crashing, at a measured speed meaningfully
      faster than the original `MediaMetadataRetriever` baseline (~11 minutes
      for this 37s clip), and the resulting rep count is materially closer to
      the 35-rep ground truth than the pre-fix count of 5. This class has no
      unit test coverage (needs a real device runtime), so on-device
      verification is the acceptance bar, not a test run.
- [x] Typecheck and the full test suite pass (no regressions expected outside
      this file; nothing here should be unit-testable given the above).

## Comments

Shipped as planned (hardware decoder + `SurfaceTexture` + OpenGL readback,
`GlFrameReader`), with two things found only by testing on the real device
that the ticket text didn't anticipate:

1. **The `KEY_ROTATION` checklist line was wrong.** Manually re-applying the
   container's rotation hint after readback (what the ticket asked for, and
   what the prior ByteBuffer-mode version genuinely needed) double-rotates
   the frame when decoding to a `Surface`: `SurfaceTexture.getTransformMatrix()`
   already includes it, and the shader already samples through that matrix.
   Found by dumping a decoded frame to a PNG mid-implementation and looking at
   it — the image was upright but the skeleton overlay in the app was
   sideways, confirming it as a display/rotation bug rather than a tracking
   bug. Fixed by removing the manual `postRotate` entirely, keeping only the
   vertical flip `glReadPixels`'s bottom-up row order always needs.
2. **Full-resolution decode/readback is scope creep, flagged in review, kept
   as-is.** The prior `MediaMetadataRetriever`-based version downscaled via
   `getScaledFrameAtTime(..., MAX_FRAME_WIDTH, ...)`; that was already dropped
   earlier in the `MediaExtractor`/`MediaCodec` rewrite (before this ticket),
   and neither `spec.md` nor this ticket ever re-authorized or reconsidered
   it. Left full-resolution: verified fast enough on-device (real-time for the
   37s test clip, full 1920x1080), and re-adding a resize step risks
   reopening the exact class of crash this ticket just finished fixing.

On-device result for `jumping_jack.mp4`: 566 frames analyzed (vs. 282 before),
no dropout gaps, decode finishes in ~real time (vs. ~11 minutes before), no
crash. Quick Count's rep count went from 5 to 10 against a 35-rep ground
truth — closer, but the remaining gap is attributed to `RepCounter`/
`TrackingStateMachine` tuning against dense, correctly-oriented data, which
this ticket's spec explicitly keeps out of scope; a follow-up ticket would be
needed to close that gap.

Reviewed via `/code-review` (Standards + Spec axes) before commit. Standards:
two judgement calls taken — `GlFrameReader` (a self-contained, general-purpose
EGL/GLES utility, flagged as Divergent Change sharing a file with
`VideoPoseTracker`'s own orchestration logic) moved to its own file; its
`width`/`height` constructor params (flagged as a Data Clump) bundled into a
single `android.util.Size`. Two judgement calls left as-is: raw `Long`
millisecond/microsecond timestamps (Primitive Obsession) — matches how time is
handled elsewhere in this codebase (e.g. `FRAME_INTERVAL_MS`), not worth a
wrapper type in isolation; and `compileShader`/`buildProgram`'s duplicated
status-check-then-throw shape — two call sites, and the reviewer itself
flagged it as idiomatic GL boilerplate, not worth extracting. Spec axis
findings are captured in the checklist annotations and points 1–2 above.
