# 03: Session Tracking gets the same border

**What to build:** The same continuous-color border and dashed/gray
indicator built in ticket 02 appear live throughout a Session's Tracking
phase, not just before it starts — reused as-is, not reimplemented. As the
Athlete moves between Sets of different Exercises within a Routine, the
required-joints requirement relaxes or tightens automatically (e.g. a Squat
Set into a Push-Up Set stops requiring legs in frame). See `../spec.md`
user stories 3, 14, 23.

**Blocked by:** 01, 02

**Status:** ready-for-agent

- [ ] `SessionScreens.kt`'s `TrackingContent` renders ticket 02's shared
      border component, driven by the same shared distance-scoring pipeline
      and the tracker's `signals`.
- [ ] `SessionViewModel`'s `rawFrames` collection becomes unconditional
      (today gated behind `showPoseOverlay`, debug builds only), so the
      border has data to work with in release builds.
- [ ] The border's dashed/gray requirement visibly changes when the Routine
      moves from one Set's Exercise to the next, using ticket 01's per-step
      profile update.
- [ ] The border sits over the camera preview without obstructing the
      existing rep count, target, or progress text.
- [ ] No change to `SessionEngine`, rep counting, or Form Score computation.
- [ ] Typecheck and the full test suite pass.
- [ ] On-device verification across a multi-Exercise Routine (e.g. a Squat
      Set followed by a Push-Up Set): the border's color responds live to
      distance throughout, and its required-joints behavior changes
      correctly between Sets.

## Comments
