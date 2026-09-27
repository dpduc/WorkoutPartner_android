# 01: Pick one consistent body side per tracked angle

**What to build:** Replace `PoseFrameMapper`'s independent per-joint side
selection with a side choice made once per angle (per `ExerciseProfile`'s
`jointA`/`vertex`/`jointC` triple), so `Angle.between` never computes a
cross-body angle from two different physical sides. See `../spec.md` for
the root-cause analysis and the real landmark-CSV evidence this is built
on.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] Land wherever makes sense (see spec.md's Scope note on the seam
      decision) a side-consistent resolution for a profile's three joints:
      score each side (left/right) by its worst-case or mean confidence
      across all three joints, pick the higher-scoring side as a unit if it
      clears `PoseFrameMapper.DEFAULT_VISIBILITY_THRESHOLD` on all three,
      and only fall back to the current per-joint/drop-the-frame behavior
      once neither side has all three joints confidently tracked.
- [ ] `RepCounter`'s existing "frame with a missing joint is skipped, not
      treated as a phase change" behavior (its own doc comment) is
      preserved for the genuine no-consistent-side case — this ticket
      changes *which* frames get skipped (only true occlusion-of-everything
      cases now, not mismatched-but-present sides), not the skip mechanism
      itself.
- [ ] `ClipReplayTest` gains cases for the three new fixtures already
      checked into `core-pose-tracking/src/test/resources/clips/`:
      - `squat.landmarks.csv` — 356 frames, ground truth 10 Squats.
      - `side_squat.landmarks.csv` — 358 frames, ground truth 10 Squats.
      - `pushup_edge.landmarks.csv` — 505 frames, ground truth 10 Push-ups.
      Follow the existing tests' own honesty about real-clip noise (the
      push-up clip's test asserts a range, not an exact count, with a
      comment explaining why) rather than asserting exact equality if the
      fix doesn't fully close the gap to ground truth — but each of these
      three should land much closer than today's 1/10, 6/10, 2/10.
- [ ] Typecheck and the full test suite pass.
- [ ] Re-run the four original app-level clips (`squat.mp4`, `side_squat.mp4`,
      `push_up.mp4`, `push_up_edge.mp4` — under `testvideos/` locally, not
      checked in, same debug `debug_video.mp4` swap used originally) through
      the real Quick Count UI on-device, and compare the new counts to the
      ground truth recorded in spec.md's Problem statement table.

## Comments
