# 12: Position Check

**What to build:** Extend `BeforeYouStartEngine` with a real Position Check phase: a live camera preview with a body outline that confirms the whole body is in frame at a workable distance before tracking starts, with spoken guidance. The Session now genuinely gates on this (previously it was skipped straight through).

**Blocked by:** 10.

**Status:** ready-for-agent

- [ ] Live camera preview with a body outline overlay once the Athlete has put the phone down.
- [ ] Clear indicators for "whole body in frame" and "distance OK".
- [ ] Position Check evaluation is a pure function of pose frames, inside the engine:
  - Body in frame: at least 28 of 33 landmarks with confidence ≥ 0.5.
  - Distance: skeleton height between 40% and 80% of frame height; below → "too far", above → "too close".
  - Stability: landmark displacement below a threshold for 2 seconds.
- [ ] Spoken guidance: "Step back a little" / "Come a bit closer" when distance fails; "Body detected" once in frame.
- [ ] All checks passing plus 2 seconds of stability auto-advances to Countdown, with no tap required.
- [ ] A "Start anyway" button appears once the phase has run for ~15 seconds, letting an imperfect setup proceed regardless.
- [ ] Lighting is explicitly not checked in this version.
- [ ] `BeforeYouStartEngineTest` covers: each Position Check failure mode reports correctly; pass + 2s stable auto-advances; "Start anyway" appears only after 15s.
