# Test clips to record

Clips of a real person exercising, used to test rep counting, form scoring and
the Position Check without anyone standing in front of the phone. The two clips
recorded so far (a 5 s jumping-jack clip and an 18 s push-up clip that switches
camera angles) proved the approach but are too noisy and too short to tune on:
the push-up count swings between 3 and 10 with tiny settings changes. The clips
below fix that by isolating one variable at a time.

## How to record every clip

- Phone in **portrait**, propped still (not hand-held), front camera, **~2 m
  away**, lens at roughly chest height. This is how the app is meant to be used.
- **Whole body in frame, head to feet**, with room above and below. The exception
  is any clip marked "too close / too far".
- **One camera angle per clip, no cuts, no edits.** If you want a second angle,
  record a second clip.
- Plain, well-lit room, fitted clothing that contrasts with the background.
  One person only, unless the clip says otherwise.
- 1080p, 30 fps. Keep each clip **under 20 seconds**.
- Start by **standing still for 3 seconds** (this also exercises the Position
  Check's stability test) and end standing still for 2 seconds.
- **Count out loud or write down the true number of reps** and note which were
  good, shallow or sloppy. Put it in the file name and in a text file next to the
  clip (format below).

## File naming and the truth file

`<exercise>_<view>_<what>_<N>reps.mp4`, for example
`squat_front_good_8reps.mp4`. Next to it, `squat_front_good_8reps.truth.txt`:

```
reps: 8
per rep (g = good form, s = shallow/sloppy, x = should not count): g g g g g g g g
notes: anything odd (phone bumped at 0:12, dog walked through, ...)
```

## Priority 1: the minimum useful set (12 clips)

These give an honest picture of counting and form for all five exercises.

| # | Clip | Reps | Why |
|---|------|------|-----|
| 1 | `jumpingjack_front_good_10reps` | 10 | Baseline for the exercise with the most data so far |
| 2 | `jumpingjack_front_lazy_8reps` | 8 | Arms only reach shoulder height: should count but **fail form** |
| 3 | `squat_front_good_8reps` | 8 | Full-depth squats, facing the phone |
| 4 | `squat_side_good_8reps` | 8 | Same squats from the side (knee angle is easiest to see here) |
| 5 | `squat_front_shallow_8reps` | 8 | Only a quarter squat: should **not count**, or count and fail form |
| 6 | `pushup_side_good_8reps` | 8 | Phone on the floor at your side, whole body in frame |
| 7 | `pushup_side_shallow_8reps` | 8 | Half range of motion: should fail form |
| 8 | `situp_side_good_8reps` | 8 | Phone on the floor at your side |
| 9 | `lunge_front_alternating_10reps` | 10 | 5 per leg, facing the phone |
| 10 | `lunge_side_onelegged_8reps` | 8 | Same leg every time, from the side |
| 11 | `stepjack_front_good_10reps` | 10 | Step Jack has never been recorded |
| 12 | `positioncheck_walkin_and_settle` | 0 | Walk into frame from off-screen, stand still 4 s (does the check pass by itself?) |

## Priority 2: what makes counting break

Record these once the first set exists.

- **Speed:** `squat_front_fast_10reps` (about 1 s per rep) and
  `squat_front_slow_6reps` (about 4 s per rep, with a pause at the bottom).
- **Pause mid-rep:** `pushup_side_pause_6reps`, stop halfway down for 2 s in
  each rep. Should still be one rep each.
- **Half reps:** `jumpingjack_front_partial_6reps`, alternate a full jack with a
  half jack. Truth file marks the half ones `x` or `s`.
- **Lost tracking:** `squat_front_stepout_8reps`, step out of frame for 3 s
  after rep 4 and come back. Should pause and resume, not lose or invent reps.
- **Occlusion:** `squat_front_chair_8reps`, a chair or table hiding the legs
  below the knees. Should degrade gracefully, not count phantom reps.
- **Loose clothing:** one clip in a baggy hoodie (`jumpingjack_front_baggy_8reps`).
- **Low light:** `squat_front_dim_8reps`, room lit by a single lamp.
- **Camera height:** `squat_front_phonelow_8reps` (phone on the floor) and
  `squat_front_phonehigh_8reps` (phone on a shelf above head height).
- **Angle:** `squat_45deg_good_8reps` and `jumpingjack_45deg_good_8reps`,
  standing at about 45 degrees to the phone instead of square on.
- **Two people:** `squat_front_twopeople_8reps`, the exerciser plus a second
  person standing still behind them (the app tracks one person only).

## Priority 3: Position Check

The Position Check's distance rule measures skeleton height, which we already
know misjudges horizontal exercises (the push-up clip reads "too far").

- `pose_standing_1m` (too close: torso or legs cut off), `pose_standing_2m`
  (right distance), `pose_standing_3_5m` (too far), 10 s each, standing still.
- `pose_standing_swaying`: standing but shifting weight and moving the arms for
  10 s, should not pass the stillness test.
- `pose_plank_side` and `pose_situp_start_side`: the starting position of
  push-ups and sit-ups, which are horizontal.
- `pose_partial_body`: standing with the feet just out of frame.

## Later, if needed

- Different people: a much shorter and a much taller person, and someone with a
  different build, doing the same good squat set (`squat_front_good_8reps`).
- Outdoor lighting and a busy background.
- The same exercise recorded on two different phones.

## Using a clip

1. Copy it to the phone as `debug_video.mp4` inside the app's private folder
   (debug builds only):
   ```
   adb push clip.mp4 /data/local/tmp/debug_video.mp4
   adb shell "run-as com.workoutpartner.app cp /data/local/tmp/debug_video.mp4 files/debug_video.mp4"
   ```
2. Run the Session in the app. The debug build draws the tracked skeleton over
   the screen (green = used, red = low confidence) and logs reps and form
   scores under the tag `SessionTrace` (`adb logcat -s SessionTrace`).
3. When the clip finishes, the app writes `debug_video.landmarks.csv` next to
   it. Pull it (`adb exec-out run-as com.workoutpartner.app cat
   files/debug_video.landmarks.csv > clip.landmarks.csv`) and copy it to
   `core-pose-tracking/src/test/resources/clips/`. `ClipReplayTest` replays it
   through the real counter and asserts against the truth file's numbers, so
   counting can be tuned on the PC without the phone.
