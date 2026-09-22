Status: done

# VideoPoseTracker decode pipeline: hardware decode via SurfaceTexture + OpenGL

## Problem statement

Testing the newly-added real test clips (`testvideos/*.mp4`) on-device surfaced a
severe rep undercount on `jumping_jack.mp4` (5 counted vs. a user-supplied ground
truth of 35: 10 normal, 5 slow-with-pauses, 20 fast). Replaying the actual captured
landmark CSV through the real `RepCounter`/`TrackingStateMachine` pipeline (not
guesswork) showed this isn't a counter-tuning problem: `VideoPoseTracker`'s decode
step, `MediaMetadataRetriever.getFrameAtTime(..., OPTION_CLOSEST)`, produced 33
dropout bursts of 594–660ms each (8–10 consecutive missed 66ms samples in a row)
across the 37s clip, because frame-accurate seeking forces a decode-from-last-keyframe
on every single call — O(n×GOP) redundant work, measured at ~8x real time
(11+ minutes for a 37s clip).

Research confirmed neither `MediaMetadataRetriever` nor its official successor
(`androidx.media3.inspector.frame.FrameExtractor`, Media3 1.10+) fit a dense/
sequential sampling pattern — both are fundamentally seek-based, and ExoPlayer's own
`seekTo` still flushes and redecodes from the last keyframe even for small forward
seeks. The correct fix is a genuine single-pass sequential decode: read the video
once, front to back, handing every frame near a 66ms boundary to the pose
landmarker as it comes off the decoder — no re-seeking, no redundant GOP replay.

Two approaches were considered:
- **Option A**: raw `MediaExtractor` + `MediaCodec`, no new Gradle dependency.
- **Option B**: Media3 `ExoPlayer` + `VideoFrameProcessor` + `ImageReader`.

Option A was chosen (user sign-off) as the smaller, dependency-free change; `CameraPoseTracker`
(live camera capture) is untouched by any of this and stays out of scope throughout —
this only affects `VideoPoseTracker`'s debug/offline file-replay path.

## Diagnosis: three device-reproduced decode failures, in order

Building Option A against the real device (`R5CY91B7K6E`, Samsung `SM_S938B`) hit
three distinct native failures, each ruling out part of the design space:

| # | Decoder | Output path | Result |
|---|---|---|---|
| 1 | Hardware (`c2.qti.hevc.decoder`) | `Surface` → `ImageReader` plane read | `JNI DETECTED ERROR`: `nativeCreatePlanes`/`NewDirectByteBuffer` null pointer reading tiled/UBWC buffers — crashes the whole app process |
| 2 | Software (`c2.android.hevc.decoder`, forced via `MediaCodecList`/`isSoftwareOnly`) | `Surface` → `ImageReader` plane read | `Codec2Client: setOutputSurface -- failed to set consumer usage (6/BAD_INDEX)`, immediately followed by `C2SoftHevcDec: Fatal error in decoder` |
| 3 | Software (same) | No `Surface` — `MediaCodec.getOutputImage()` (ByteBuffer/`Image` mode) | `C2SoftHevcDec: Fatal error in decoder 0x43dd` within ~1ms of reaching `RUNNING` state, on the very first real input sample — no `Surface` involved at all this time |

Attempt 1 is the key data point: the **hardware** decoder successfully decoded
frames — its crash was in *reading* the output (`ImageReader`'s generic CPU
plane-mapping path can't handle this device's tiled/compressed buffer layout),
not in decoding. Attempts 2 and 3 show the **software** HEVC decoder
(`c2.android.hevc.decoder`/`C2SoftHevcDec`, AOSP's Codec2 reference implementation)
is not reliably capable of decoding this specific real camera-recorded HEVC content
at all, regardless of output mode — a known weak spot of that component relative to
vendor hardware decoders tuned for their own camera's encoder output.

## Decision

Go back to the **hardware** decoder (known to decode this content correctly) and
replace `ImageReader`'s CPU plane-read with **`SurfaceTexture` + OpenGL**: render
the decoder's output `Surface` to a GL external texture, sample it with a
pass-through shader into a normal RGBA framebuffer, then `glReadPixels` it out.
GPU texture sampling transparently handles whatever tiled/compressed layout the
vendor decoder produces — this is the same technique Grafika's reference examples
and Media3's own internal frame processor use for exactly this class of problem.

## Scope

- Ticket 01: replace `VideoPoseTracker`'s current software-decoder/`getOutputImage`
  pipeline with hardware decode + `SurfaceTexture`/OpenGL frame readback.

## Out of scope

- `CameraPoseTracker` (live camera) — a completely different code path, unaffected
  by any of this; not touched.
- Testing the remaining 4 new clips (`push_up.mp4`, `push_up_edge.mp4`,
  `side_squat.mp4`, `squat.mp4`) — follow-up once this ships; no ground truth
  gathered for them yet.
- Any `RepCounter`/`TrackingStateMachine` tuning — the diagnosis showed this is a
  data-availability problem (dropped/delayed frames), not a counting-logic problem.
