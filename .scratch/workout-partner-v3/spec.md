Status: ready-for-agent

# Workout Partner v3 — Before You Start, Guest feature parity, Step Jack

Follows `workout-partner-v2` (tickets 01–03 shipped; 04 still `needs-triage`).
Decisions come from a grilling session on 2026-09-18; glossary terms
(**Athlete**, **Exercise Variant**, **Before You Start**, **Form Guide**,
**Position Check**) are defined in `CONTEXT.md`, and the Guest decision is
recorded in `docs/adr/0007-guest-feature-parity-stays-local.md`. Design
source material: `docs/before-you-start-system.md`,
`docs/onboarding_ux_script.md`.

## Problem Statement

An Athlete doing a Routine props the phone up 2 m away so the camera can see
their whole body — and from there they can't read anything on screen. They
start a Session with no idea whether the camera can actually see them, no
reminder of correct form for the Exercises ahead, and during the Session they
get no information they can take in from a distance: small text, no audio.
Tracking quality suffers and they end up walking back to the phone.

Separately, a Guest hits walls: Quick Count and the Roster are locked behind
sign-up, and Streak/Weekly Target don't exist for them at all, even though
their Sets are already stored on-device. The "create an Account" nudge is
framed as unlocking features rather than protecting their progress.

Onboarding asks for an Activity Level on a dry Low/Medium/High scale that
doesn't match how people describe themselves — and the answer isn't even
used: Routine difficulty ignores it.

Athletes with a high BMI or joint limits are asked to do Jumping Jacks with no
low-impact alternative.

Finally, uncommitted auth work adds Google and phone/SMS sign-in; phone sign-in
leaks an Android `Activity` into the data layer's `AuthGateway`, and Google
sign-in silently claims any Guest data on the device.

## Solution

- **Before You Start**: between picking a Routine and its first Set, the
  Athlete sees a Workout Overview, Form Guides for any Exercise/Variant they
  haven't seen before, a Position Check that confirms the camera can see their
  whole body at a workable distance, and a 10-second countdown that announces
  the first Exercise out loud. During the Session, the screen is readable from
  2 m (huge rep count, large Exercise name, wide progress bar) and key moments
  are spoken aloud. Quick Count gets the Position Check only.
- **Guest feature parity**: a Guest can use everything — Routines, Streak,
  Weekly Target, Streak Shields, Roster, Quick Count, Tallies, Progress,
  reminders. An Account adds only cloud backup and multi-device sync. On
  sign-up, all Guest data moves into the new Account. On sign-in to an
  existing Account, the Guest chooses to merge or discard their on-device data.
  The sign-up nudge becomes "Back up your progress".
- **Activity Level** becomes four friendly tiers (Sedentary, Lightly Active,
  Active, Very Active) presented as cards, and actually feeds Routine
  difficulty.
- **Step Jack**, a low-impact Exercise Variant of Jumping Jack with its own
  thresholds and Personal Best, is suggested for BMI ≥ 30 and switchable on the
  Workout Overview.
- **Auth cleanup**: keep Google sign-in, drop phone/SMS sign-in, and route
  every sign-in through the merge-or-discard decision.

## User Stories

### Auth cleanup

1. As an Athlete, I want to sign in with my Google account, so that I don't need to create yet another password.
2. As an Athlete, I want Google sign-in to behave exactly like email sign-in with respect to my Guest data, so that one path doesn't silently do something different from the other.
3. As a developer, I want `AuthGateway` to contain no Android UI types, so that the data module stays testable on the JVM and every gateway (Firebase, Local, Fake) can implement it honestly.
4. As a developer running without Firebase configured, I want Google sign-in to fail with a clear "not available in local mode" message rather than crash, so that the local auth fallback keeps working.

### Activity Level

5. As a new Athlete, I want to pick my activity level from four illustrated cards with plain-language descriptions, so that I can recognise myself instead of guessing what "Medium" means.
6. As a new Athlete, I want the four options to be Sedentary ("I rarely exercise"), Lightly Active ("a few times a week"), Active ("frequently active") and Very Active ("working out is my daily passion"), so that the choice matches how I'd describe myself.
7. As an Athlete, I want to change my Activity Level later in Settings using the same four options, so that I can update it as I get fitter.
8. As an existing Athlete who answered Low/Medium/High before, I want my answer converted automatically, so that I'm not asked again.
9. As an existing Athlete, I want an ambiguous old answer converted to the more conservative new tier (Low→Sedentary, Medium→Lightly Active, High→Active), so that my Routines never unexpectedly get harder.
10. As an Athlete, I want my Activity Level to influence how hard my Routines are, so that the question I answered actually matters.
11. As a Sedentary Athlete, I want Routines nudged easier, so that I'm not overwhelmed starting out.
12. As a Very Active Athlete, I want Routines nudged harder, so that they're still worth doing.
13. As a Lightly Active or Active Athlete, I want my difficulty tuned by BMI and age as before, so that nothing changes for the middle of the range.

### Guest feature parity

14. As a Guest, I want to create a Roster of Tracked Profiles, so that I can count reps for other people without signing up.
15. As a Guest, I want to run Quick Count for a Tracked Profile and get a Tally, so that I can use the app with a training partner right away.
16. As a Guest, I want to see my Tally history per Tracked Profile, so that I can see their progress.
17. As a Guest, I want a Weekly Target I can adjust, so that I can set my own pace.
18. As a Guest, I want a Streak and Streak Shields, so that I get the same motivation as an Account holder.
19. As a Guest, I want to see my Progress screen (Streak, Personal Bests, history), so that I can see how I'm doing.
20. As a Guest, I want to enable the daily reminder, so that I remember to work out.
21. As a Guest, I want every menu entry available to me, so that I never hit a "sign up to use this" wall.
22. As a Guest, I want the post-Set prompt to say "Back up your progress", so that I understand what an Account gives me.
23. As a Guest, I want to see the prompt at most once per Session, so that it's a nudge, not a nag.
24. As a Guest, I want a "Back up your progress" entry in Settings, so that I can sign up whenever I choose.

### Sign-up and sign-in with Guest data

25. As a Guest signing up, I want all my Sessions, Sets, Roster, Tracked Profiles, Tallies, body-stats, Weekly Target and Streak state moved into my new Account, so that I lose nothing.
26. As a Guest signing up, I want my Streak recomputed from my full history, so that my Guest days count.
27. As a Guest who only ever used Quick Count, I want my Roster and Tallies still carried over on sign-up, so that non-Session data isn't overlooked.
28. As a Guest signing in to an Account I already have, I want to be asked whether to merge my on-device data into it or discard it, so that I stay in control.
29. As a Guest with no on-device data, I want sign-in to just work with no prompt, so that I'm not asked a pointless question.
30. As a Guest choosing to merge, I want my Guest Sessions, Sets, Roster and Tallies added to the Account, so that everything is in one place.
31. As a Guest choosing to merge, I want the Account's Weekly Target kept, so that my existing settings aren't overwritten by the Guest defaults.
32. As a Guest choosing to merge, I want my Streak recomputed across the merged history, so that it reflects everything I did.
33. As a Guest choosing to merge, I want the Account's existing body-stats kept if it has them, and my Guest ones used only if it doesn't, so that my established profile isn't overwritten.
34. As a Guest choosing to merge, I want a short confirmation of what was added ("Added 12 workouts and 3 Tracked Profiles to this account"), so that I know it worked.
35. As a Guest choosing to discard, I want to see exactly what will be lost before confirming, so that I don't delete something by accident.
36. As a Guest choosing to discard, I want the Guest data permanently removed, so that it doesn't linger on a shared device.
37. As a Guest who backs out of the merge-or-discard prompt, I want the sign-in cancelled, so that my data is never left ownerless.
38. As an Athlete, I want a failed merge to leave my Guest data intact and still unclaimed, so that I can retry.

### Step Jack

39. As an Athlete with a BMI of 30 or more, I want Step Jack suggested in place of Jumping Jack, so that I get a low-impact option without having to ask.
40. As any Athlete, I want to switch between Jumping Jack and Step Jack on the Workout Overview before starting, so that I can choose what suits my body today.
41. As an Athlete doing Step Jack, I want reps counted at a lower arm angle (≥ 75°) than Jumping Jack, so that the easier movement is counted fairly.
42. As an Athlete doing Step Jack, I want good form judged against its own threshold (≥ 135°), so that my Form Score is meaningful for this movement.
43. As an Athlete, I want a Step Jack Set to be a Good Set against Step Jack's own thresholds, so that switching doesn't punish me.
44. As an Athlete, I want a separate Personal Best for Step Jack, so that my Jumping Jack record isn't mixed with scores on a different scale.
45. As an Athlete, I want my Step Jack Sets shown as "Step Jack" in history and Progress, so that I can tell them apart.
46. As an Athlete, I want Step Jack Sets to count toward Active Days and Streak exactly like any other Set, so that choosing low-impact never costs me my Streak.

### Before You Start — Workout Overview

47. As an Athlete who picked a Routine, I want to see an overview first, so that I know what I'm about to do before I put the phone down.
48. As an Athlete, I want the overview to show the Routine name, estimated duration, difficulty tier, and each Exercise with its (difficulty-adjusted) rep target, so that I know the full plan.
49. As an Athlete, I want safety notes (2 m × 2 m clear space, good lighting, fitted clothing), so that I set up properly.
50. As an Athlete, I want a "Review form" button always available on the overview, so that I can re-read any Form Guide even after I've seen it.
51. As an Athlete who has already seen every Exercise's Form Guide in this Routine, I want to skip straight to the Position Check, so that I'm not slowed down.
52. As an Athlete, I want the Jumping Jack/Step Jack switch to appear on the overview only when the Routine contains Jumping Jack, so that it's not clutter otherwise.

### Before You Start — Form Guides

53. As an Athlete meeting an Exercise for the first time, I want its Form Guide shown before I start, so that I know how to do it correctly.
54. As an Athlete, I want only the Form Guides I haven't seen shown automatically, so that I don't sit through Squat again in every Routine.
55. As an Athlete, I want each Form Guide to show a picture, the starting position, correct-form cues, common mistakes, and what the tracker checks, so that I know exactly what "good" means here.
56. As an Athlete, I want to swipe between Form Guides and see "2 of 4", so that I know how many are left.
57. As an Athlete, I want Step Jack to have its own Form Guide, so that I learn the low-impact version properly.
58. As an Athlete, I want a Form Guide marked as seen once I've viewed it, so that it won't be forced on me again.
59. As a Guest who signs up or merges, I want my seen-Form-Guide history to stay on this device, so that I'm not re-shown guides I've already read.

### Before You Start — Position Check

60. As an Athlete who has put the phone down, I want to see a live camera preview with a body outline, so that I know where to stand.
61. As an Athlete, I want clear indicators for "whole body in frame" and "distance OK", so that I know what's wrong if the check doesn't pass.
62. As an Athlete standing too close or too far, I want to hear "Step back a little" or "Come a bit closer", so that I can fix it without walking to the phone.
63. As an Athlete, I want to hear "Body detected" when I'm in frame, so that I know I'm set.
64. As an Athlete, I want the countdown to start automatically once every check passes and I've held still for 2 seconds, so that I don't need to touch the phone.
65. As an Athlete in a small room or poor light, I want a "Start anyway" button after about 15 seconds, so that an imperfect setup never blocks me.
66. As an Athlete, I want the Position Check to not require lighting to pass in this version, so that I'm not blocked by a check the MVP doesn't reliably measure.

### Before You Start — Countdown

67. As an Athlete, I want a 10-second countdown in huge digits once the Position Check passes, so that I can get into position.
68. As an Athlete, I want the first Exercise and its rep target announced aloud ("Squat. 12 reps."), so that I know what's coming without reading.
69. As an Athlete, I want the countdown to lead straight into the first Set's tracking, so that there's no extra tap.

### During the Session — distance-friendly screen and audio

70. As an Athlete 2 m away, I want the rep count in very large type (≈120sp), so that I can read it at a glance.
71. As an Athlete 2 m away, I want the Exercise name in large type (≈48sp), so that I know which Exercise I'm on.
72. As an Athlete 2 m away, I want a wide progress bar toward the rep target, so that I can see how close I am without reading numbers.
73. As an Athlete, I want to hear the Exercise name and rep target at the start of each Set, so that I know what to do next.
74. As an Athlete, I want to hear "Rest N seconds" when a Set ends, so that I know I can rest.
75. As an Athlete, I want to hear "Get ready — next: Push-up" 5 seconds before rest ends, so that I'm in position in time.
76. As an Athlete, I want to hear "Tracking lost — step back into frame" when I leave the frame, so that I can fix it immediately.
77. As an Athlete, I want to hear "Workout complete" at the end, so that I know I'm done.
78. As an Athlete, I want urgent announcements (tracking lost) to interrupt less important ones, so that they're never stuck in a queue.
79. As an Athlete, I want to turn spoken prompts off in Settings, so that I can work out silently.
80. As an Athlete, I want all spoken lines and Form Guide text in English in this version, with every string translatable later, so that Vietnamese can be added without code changes.

### Quick Count

81. As an Athlete running Quick Count, I want the Position Check before counting starts, so that the person I'm counting is properly framed.
82. As an Athlete running Quick Count, I want no overview, Form Guides or countdown, so that Quick Count stays quick.
83. As an Athlete running Quick Count, I want "Start anyway" to be available as in a Session, so that an imperfect frame never blocks counting.

## Implementation Decisions

### Auth

- `AuthGateway` keeps email sign-up/sign-in, Google sign-in, and sign-out. Phone/SMS verification is removed from the interface, all implementations, `AuthRepository`, and the auth screens. No Android UI types appear in `AuthGateway`.
- `LocalAuthGateway` implements Google sign-in by throwing a typed "unavailable in local mode" error that the UI maps to a friendly message; `FakeAuthGateway` supports it for tests.
- The `com.google.gms.google-services` plugin stays applied (already done in the uncommitted work); Credential Manager + Google ID dependencies stay. Firebase console provisioning remains the owner's manual step.

### Sign-in / sign-up contract (the Guest data decision)

- `AuthRepository` becomes the single seam for every Guest-data outcome:
  - **Sign-up** (email): creates the Account and, if any Guest data exists, claims all of it in one transaction — as today, but "any Guest data" is widened.
  - **Sign-in** (email or Google) returns a result that is either *signed in, nothing pending* or *signed in, Guest data pending* carrying a summary of what's pending (counts of Sessions, Tracked Profiles, Tallies). It never claims on its own. Google sign-in no longer auto-claims.
  - A new operation resolves pending Guest data with a choice of **Merge** or **Discard**. **Cancel** signs the Athlete back out, leaving Guest data untouched.
- Because sign-in completes at the gateway before the prompt, "back out cancels sign-in" is implemented as an immediate sign-out; Guest data is never re-owned until Merge is chosen.
- `hasUnclaimedGuestData` counts any unowned Session, any unowned Tracked Profile, or a Guest record carrying non-default state — not Sessions only.
- **Claim on sign-up** moves: unowned Sessions, unowned Tracked Profiles (and with them their Tallies), Guest body-stats, Guest Weekly Target, and recomputes Streak/Shields from the full Set history; then clears the Guest record.
- **Merge on sign-in** moves the same rows, but keeps the Account's Weekly Target and keeps the Account's body-stats when present (Guest body-stats fill only if the Account has none); Streak/Shields are recomputed across the merged history.
- **Discard** deletes unowned Sessions (and their Sets), unowned Tracked Profiles (and their Tallies), and the Guest record, in one transaction. Pending sync-queue entries for deleted rows are removed too.
- Claim, merge and discard each run in a single Room transaction; a failure leaves Guest data unclaimed and intact.
- A Guest's Sets and Tallies continue to be enqueued for sync at write time regardless of ownership (unchanged ADR-0004 behaviour); the sync engine must skip/hold ownerless rows until they're owned, since Firestore rules require an owner.

### Schema (Room v4 → v5)

- `ActivityLevel` becomes `SEDENTARY`, `LIGHTLY_ACTIVE`, `ACTIVE`, `VERY_ACTIVE`. The migration rewrites stored values in both `accounts` and `guest_profile`: `LOW`→`SEDENTARY`, `MEDIUM`→`LIGHTLY_ACTIVE`, `HIGH`→`ACTIVE`.
- `tracked_profiles.accountId` becomes nullable (null = owned by the Guest), mirroring `sessions.accountId`.
- The single-row `guest_profile` table becomes the Guest's own record: it gains `weeklyTarget`, `currentStreak`, `bankedShields`, `notificationsEnabled`, with the same defaults as a new Account. Its body-stats columns become nullable, since a Guest can now have streak state before (or without) answering onboarding. No placeholder "local guest" Account row is introduced.
- Sets and Tallies gain a nullable `exerciseVariant` column (null = the Exercise's standard form; `STEP_JACK` for Step Jack). Firestore schema doc and security rules are updated to accept the new field.
- Guest Streak recompute reads Sets from unowned Sessions, the same way the Account recompute reads an Account's Sets.
- Seen-Form-Guide state and speech on/off are device-local preferences (not Room, not synced), keyed per Exercise/Variant.

### Activity Level → difficulty

- `BodyStats` gains `activityLevel`. `RoutineDifficulty.compute` adds an activity score to the existing BMI + age-band score: Sedentary −1, Lightly Active 0, Active 0, Very Active +1. Thresholds for Easy/Standard/Challenging are unchanged. Missing Activity Level scores 0.
- Profile setup and Settings replace the 3-way segmented control with four selectable cards (title, one-line description, illustration placeholder).

### Step Jack (core-rep-counting)

- A new `ExerciseVariant` concept in `core-rep-counting`, with `STEP_JACK` as its only value, parented to `JUMPING_JACK`.
- `ExerciseProfiles` and `RepCounter` resolve a profile by Exercise plus optional Variant. Step Jack uses the same elbow–shoulder–hip joints and increasing direction as Jumping Jack, with `repThresholdDegrees = 75` and `formThresholdDegrees = 135` (placeholders, same spirit as the existing thresholds).
  - *Update 2026-09-21:* real footage showed smoothed Jumping Jack arm peaks of 130–137°, so no rep could reach the 150° form bar. As shipped in `ExerciseProfiles.kt`: Jumping Jack rep 90° / form **125°**; Step Jack rep 75° / form **110°** (kept below Jumping Jack's, as the easier movement). Still placeholders.
- `RoutineStep`/`CompletedSet` carry the optional Variant; the Session's Good Set judgement uses the Variant's threshold.
- Suggestion rule: when BMI ≥ 30, Jumping Jack steps in the chosen Routine default to Step Jack on the Workout Overview; the Athlete can toggle it for the whole Session. The choice is not persisted between Sessions.
- `ProgressStats.personalBests` keys Personal Bests by Exercise + Variant.

### Before You Start

- A new pure-Kotlin `BeforeYouStartEngine` (no Android, camera or clock dependency — same pattern as `SessionEngine`), fed ticks and pose frames by its ViewModel. Phases: Overview → Form Guides (only unseen Exercises/Variants in this Routine; skipped entirely if none, or if the Athlete taps Skip when allowed) → Position Check → Countdown (10s) → Ready.
- Position Check evaluation lives inside the engine, as a pure function of pose frames:
  - Body in frame: at least 28 of 33 landmarks with confidence ≥ 0.5.
  - Distance: skeleton height between 40% and 80% of frame height; below → "too far", above → "too close".
  - Stability: landmark displacement below a threshold for 2 s.
  - All pass → auto-advance to Countdown. "Start anyway" becomes available 15 s after the Position Check phase begins. Lighting is not checked in this version.
- Quick Count runs the same engine configured to start at Position Check and skip Countdown.
- Form Guide content is bundled static data per Exercise/Variant (display name, starting position, form cues, common mistakes, tracker-check description, static image), all in string resources. Six guides: the five Exercises plus Step Jack.
- Workout Overview's estimated duration is derived from the difficulty-adjusted steps (an assumed seconds-per-rep plus rest intervals).

### Session audio and distance-friendly UI

- A new pure `SessionAnnouncer` maps a `SessionPhase` transition (previous → next) plus the Routine steps to zero or more announcements, each with a priority (Critical, High, Normal, Low). It covers: Set start ("Squat. 12 reps."), Set end + rest ("Rest 30 seconds."), 5 s before rest ends ("Get ready. Next: Push-up."), tracking lost ("Tracking lost. Step back into frame."), Session complete ("Workout complete.").
- A thin Android text-to-speech wrapper plays announcements; Critical interrupts the queue, Low is dropped if the queue is busy. It respects the speech on/off preference (default on).
- The Session's Tracking phase layout is reworked for distance: rep count ≈120sp, Exercise (or Variant) name ≈48sp, full-width progress bar, secondary info ≈28sp.
- The existing 5 s pre-Set countdown inside `SessionEngine` is unchanged; Before You Start's 10 s countdown precedes the Session as a whole.

### Guest UI gating

- Remove every Account-only gate: the Quick Count tile, Roster screen, overflow menu, Settings (Weekly Target, reminders) and Progress all work with no Account. Screens receive "the Athlete" (Account id or Guest) instead of a nullable Account id meaning "locked".
- Post-Set prompt copy changes to "Back up your progress"; once per Session as today. Settings gains the same entry for Guests.

## Testing Decisions

- Good tests exercise external behaviour through the highest seam available — inputs in, observable results out — never private helpers or internal state. Engines are driven with fixture pose frames and manual ticks, never a real clock or camera. Data-layer tests use the in-memory Room database and `FakeAuthGateway`, not mocks of DAOs.
- Seams and what each covers:
  1. **`AuthRepository`** (widened; prior art `AuthRepositoryTest`, `GuestAccountMigrationTest`): sign-up claims Sessions, Tracked Profiles, Tallies, body-stats, Weekly Target and recomputes Streak; Quick-Count-only Guest data is detected and claimed; email and Google sign-in return "pending" with correct counts when Guest data exists and "nothing pending" otherwise; Merge keeps the Account's Weekly Target and body-stats, fills missing body-stats, recomputes Streak; Discard removes all Guest rows and their queued syncs; Cancel signs out and leaves Guest data intact; a failing claim leaves data unclaimed.
  2. **Room migration v4→v5** (prior art `MigrationTest`): Activity Level value mapping in both tables; nullable `tracked_profiles.accountId` with existing rows preserved; new `guest_profile` columns with defaults; new `exerciseVariant` columns null on existing rows.
  3. **`RosterRepository` / `TallyRepository`** (prior art: their existing tests): creating and listing Tracked Profiles and Tallies for the Guest.
  4. **`RoutineDifficulty.compute`** (prior art `RoutineDifficultyTest`): each Activity Level's effect, including tipping across tier boundaries, and missing Activity Level behaving as before.
  5. **`RepCounter` / `ExerciseProfiles`** (prior art `RepCounterTest` with `PoseLandmarkFrameFixtures`): a Step Jack arm sweep to ~80° counts a rep that Jumping Jack would not; Form Score uses the 135° threshold.
  6. **`ProgressStats.personalBests`** (prior art `ProgressStatsTest`): Step Jack and Jumping Jack Sets produce separate Personal Bests.
  7. **`BeforeYouStartEngine`** (new; modelled on `SessionEngineTest`): only unseen Form Guides appear; all-seen skips straight to Position Check; each Position Check failure mode reports correctly; pass + 2 s stable auto-advances; "Start anyway" appears only after 15 s; countdown reaches Ready after 10 ticks; the Quick Count configuration starts at Position Check and ends without a countdown.
  8. **`SessionAnnouncer`** (new; style of `ReminderPolicyTest`): the correct lines and priorities for each phase transition, including Variant names ("Step Jack. 20 reps.") and difficulty-adjusted targets.
  9. **`SessionEngine`** (prior art `SessionEngineTest`): a Step Jack step judges Good Set against Step Jack thresholds.
- Not tested: the text-to-speech wrapper, Compose layouts, camera preview, Credential Manager/Google UI flow — thin adapters over framework APIs.

## Out of Scope

- Phone/SMS sign-in.
- Firebase Anonymous Auth for Guests (rejected in ADR-0007).
- Firebase console provisioning (owner's manual step).
- Rounds in Routines, and the HIIT/Tabata/AMRAP interval timer engine (`workout-partner-v2` ticket 04).
- Guided Warm-Up, raise-hand-to-start gesture, voice commands.
- Form-warning audio ("Go lower"), rep-count audio every N reps, encouragement lines.
- Animated (GIF/WebP/Lottie) Form Guide media — static images only.
- Lighting check in the Position Check.
- Haptics and colour-coded full-screen borders.
- Vietnamese or any non-English speech/text (strings are externalised for it).
- Pulling an existing Account's history from Firestore onto a new device.
- Custom/user-authored Routines.

## Further Notes

- Suggested ticket order: (1) auth cleanup — already half-done in the working tree, commit first; (2) schema v5 migration; (3) Guest parity data layer + sign-in contract; (4) Guest UI ungating + prompt copy; (5) merge/discard prompt UI; (6) Activity Level cards + difficulty; (7) Step Jack core-rep-counting; (8) Workout Overview; (9) Form Guides; (10) Position Check; (11) countdown + `SessionAnnouncer` + TTS; (12) distance-friendly Session screen; (13) Step Jack switch on the Overview; (14) Quick Count Position Check. Steps 7–12 are independent of 2–5 and can run in parallel.
- All angle and Position Check thresholds are placeholders to be tuned against real footage.
- `docs/auth-roadmap.md`'s ERD section and `docs/firestore-schema.md` need updating alongside the schema ticket; `GuestAccountMigration`'s doc comment about Tallies being structurally impossible for Guests becomes obsolete.
