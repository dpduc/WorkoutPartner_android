# 12: Position Check

**What to build:** Extend `BeforeYouStartEngine` with a real Position Check phase: a live camera preview with a body outline that confirms the whole body is in frame at a workable distance before tracking starts, with spoken guidance. The Session now genuinely gates on this (previously it was skipped straight through).

**Blocked by:** 10.

**Status:** ready-for-human

- [x] Live camera preview with a body outline overlay once the Athlete has put the phone down.
- [x] Clear indicators for "whole body in frame" and "distance OK".
- [x] Position Check evaluation is a pure function of pose frames, inside the engine:
  - Body in frame: at least 28 of 33 landmarks with confidence ≥ 0.5.
  - Distance: skeleton height between 40% and 80% of frame height; below → "too far", above → "too close".
  - Stability: landmark displacement below a threshold for 2 seconds.
- [x] Spoken guidance: "Step back a little" / "Come a bit closer" when distance fails; "Body detected" once in frame.
- [x] All checks passing plus 2 seconds of stability auto-advances to Countdown, with no tap required.
- [x] A "Start anyway" button appears once the phase has run for ~15 seconds, letting an imperfect setup proceed regardless.
- [x] Lighting is explicitly not checked in this version.
- [x] `BeforeYouStartEngineTest` covers: each Position Check failure mode reports correctly; pass + 2s stable auto-advances; "Start anyway" appears only after 15s.

## Comments

Implemented as spec'd. `BeforeYouStartPhase.PositionCheck` now carries a `PositionCheckStatus` (body in frame, distance, seconds elapsed, Start-anyway availability). `BeforeYouStartEngine` is fed `onPoseFrame`/`onTick` and evaluates through a pure `PositionCheckEvaluator` (28 of 33 landmarks at confidence >= 0.5; skeleton height < 40% = too far, > 80% = too close; lighting not checked). It auto-advances to Countdown after 2 stable seconds, `startAnyway()` works only after 15 ticks, and spoken guidance is emitted as `PositionCue`s the caller drains with `takeCue()` ("Body detected" once, "Step back a little", "Come a bit closer", distance cues repeated every 5s while unresolved). `BeforeYouStartScreen` now routes Overview/Form Guides -> Position Check -> (Countdown, still a placeholder that proceeds into the Session until ticket 13), so the Session genuinely gates on it.

Supporting changes this needed: `core-pose-tracking` only exposed six debounced joints, so `PoseTracker` gained a `rawFrames` flow of `RawPoseFrame` (all 33 landmarks, undebounced); `RawPoseLandmark.confidence` is now shared with `PoseFrameMapper` instead of duplicated. `CameraPermissionGate` moved from `MainActivity` to `ui/components` since Position Check now needs the camera before the Session does. A minimal `PromptSpeaker` wraps `TextToSpeech` (no priorities/Settings toggle — ticket 13); the three spoken lines live in string resources.

Caught in review and fixed before commit: (Spec) stillness was compared frame-to-frame, so at ~30fps ordinary sway never tripped it, and a "stable second" counted if only its last frame passed — now compared against the previous window's last frame, and every frame in the second must pass; "Body detected" could repeat on landmark-count flicker — now once per Position Check; (Standards) a suppressed too-far cue was re-announced on flicker, cue/collector startup race (collectors now attach UNDISPATCHED before the camera starts), `PromptSpeaker` dropped lines before TTS init and touched a shut-down engine, duplicated confidence rule, an outline constant duplicating the check thresholds, unused MainActivity imports.

Judgement calls left as-is: `PositionCheckScreen` owns its tracker/ticker in the composition rather than a ViewModel (app navigation state isn't saved across rotation anyway, and an activity-scoped ViewModel wouldn't stop the camera on back — documented in its KDoc); the Position Check state lives in the engine rather than a separate class (the ticket says "inside the engine"); on-screen labels are inline strings like the rest of the app, only spoken lines are resources; all thresholds are placeholders to tune on real footage.

**Not verified on a device** (`adb devices` is empty): camera preview + outline, the real MediaPipe `rawFrames` path, TTS speech, and the tracker hand-off from Position Check to the Session's own tracker. Everything pure is covered by `BeforeYouStartEngineTest`; status is `ready-for-human` for that reason. The Session's `SessionScreen`/`SessionViewModel` use an un-keyed activity-scoped `viewModel(...)`, which may reuse a stale Session across runs — not touched here and not verified, worth a device check.
