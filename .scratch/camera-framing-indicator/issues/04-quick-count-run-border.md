# 04: Quick Count Run gets the same border

**What to build:** The same continuous-color border and dashed/gray
indicator built in ticket 02 appear live throughout a Quick Count run —
reused as-is, not reimplemented. Quick Count tracks a single Exercise for its
whole run, so the required-joints requirement stays fixed for the duration
(no per-step changes, unlike ticket 03's Session case). See `../spec.md`
user stories 4, 15, 23.

**Blocked by:** 01, 02

**Status:** ready-for-agent

- [ ] `QuickCountScreens.kt`'s `QuickCountRunScreen` renders ticket 02's
      shared border component, driven by the same shared distance-scoring
      pipeline and the tracker's `signals`.
- [ ] `QuickCountViewModel` gains a `rawFrames` collection (it has none
      today), so the border has data to work with.
- [ ] The border sits over the camera preview without obstructing the
      existing rep count/target UI.
- [ ] No change to `QuickCountEngine`, rep counting, or Form Score
      computation.
- [ ] Typecheck and the full test suite pass.
- [ ] On-device verification: a Quick Count run shows the live color border
      responding to distance, and the dashed/gray state when the run's
      Exercise's required joints aren't visible.

## Comments
