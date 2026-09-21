Status: done

# Workout Partner — v1

## Problem Statement

People who want to do bodyweight exercise have no low-friction way to get an accurate rep count and form feedback without another person watching them, and no habit-forming reason to keep coming back after the first session. Separately, people who need to count reps for someone else — a coach, trainer, or teacher assigning drills — have no way to do that without counting by hand.

## Solution

Workout Partner is an Android app that uses on-device pose tracking (MediaPipe Pose Landmarker) to count Reps and score form in real time from the front camera. It offers two modes: self-tracking through bundled Routines with a Duolingo-style weekly Streak, and Quick Count for tracking Reps on behalf of someone else via a lightweight Roster. Data is stored offline-first and, for signed-in Accounts, synced through Firebase so history survives reinstalls and is visible on a separate companion Web dashboard.

## User Stories

> **Superseded in part.** Stories written for an "Account holder" (26–41: Weekly Target, Streak, Shields, heatmap, reminders, Roster, Quick Count) now apply to every **Athlete**, Guest included — `workout-partner-v3` Guest feature parity, [ADR-0007](../../docs/adr/0007-guest-feature-parity-stays-local.md). Story 38 ("no Form Score" in Quick Count) was reversed by `workout-partner-v2` ticket 03. Story 3's prompt is now "Back up your progress". `CONTEXT.md` has the current definitions.

**Accounts & Guest**

1. As a first-time user, I want to start using the app as a Guest without creating an Account, so that I can try it before committing.
2. As a Guest, I want my Sets and Tallies saved locally, so that I don't lose a real workout just because I haven't signed up yet.
3. As a Guest, I want to be prompted to create an Account after finishing a Set, so that I understand my progress isn't permanently saved yet.
4. As a Guest converting to an Account, I want my local history migrated automatically, so that I don't lose anything when I sign up.
5. As a new user, I want to sign up with email/password (or a provider), so that I can create an Account.
6. As a returning user, I want to sign in on a new device, so that I can see my synced history there.
7. As an Account holder, I want my data synced across devices, so that I can switch phones without losing history.

**Rep counting / pose tracking engine**

8. As a user starting a Set, I want the camera to detect my pose landmarks in real time, so that Reps are counted automatically.
9. As a user, I want each Exercise (Squat, Push-up, Sit-up, Lunge, Jumping Jack) to have its own rep-detection logic, so that counts are accurate per movement type.
10. As a user, I want a live rep counter on screen with a beep per counted Rep, so that I know the app is tracking me correctly without looking away.
11. As a user, I want each Rep checked against the Exercise's range-of-motion threshold, so that the Set's Form Score reflects whether I did the movement correctly.
12. As a user, I want the app to pause counting and show a warning if it loses track of me (I step out of frame or get occluded), so that partial or incorrect Reps aren't counted.
13. As a user, I want tracking to resume automatically once I'm back in frame, so that I don't have to restart the Set.
14. As a user, I want a 5-second countdown after tapping start, so that I have time to get into position before counting begins.
15. As a user, I want the app to tell me clearly whether a Set was a Good Set, so that I know if I hit both the rep target and the form threshold.
16. As a developer, I want the rep-counting engine testable independent of the camera, so that its logic can be verified with fixture landmark data.

**Self-tracking Routine flow**

17. As a user, I want to pick from a small set of bundled Routines, so that I don't have to plan a workout myself.
18. As a user, I want a Routine to define a sequence of Exercises, target reps per Set, and rest intervals, so that I'm guided through a full Session without deciding anything mid-workout.
19. As a user, I want an automatic, skippable rest timer between Sets, so that I'm not stuck waiting if I'm ready sooner.
20. As a user, I want a summary after each Set showing reps vs. target and a form note, so that I know what to improve.
21. As a user, I want a summary after a full Session showing overall performance and weekly progress, so that I see how today's workout contributes to my Streak.
22. As a user, I want to see a safety disclaimer on first launch, so that I understand the app isn't a substitute for professional guidance.
23. As a user, I want the app to work fully offline during a Session, so that a spotty connection never interrupts my workout.
24. As a user, I want to review my Personal Bests per Exercise, so that I can see my top rep count and Form Score over time.
25. As a user, I want a Form Score trend line per Exercise, so that I can see whether my technique is improving over time.

**Progress / Streaks**

26. As an Account holder, I want to set a Weekly Target (default 3 Active Days), so that my Streak reflects a pace that fits my life.
27. As an Account holder, I want any day I complete at least one Set to count as one Active Day, so that multiple Sessions in a day don't count twice.
28. As an Account holder, I want to earn a Streak Shield when I meet my Weekly Target, so that missing a future week doesn't necessarily break my Streak.
29. As an Account holder, I want a banked Streak Shield to automatically cover one missed week, so that my Streak survives an off week without me doing anything.
30. As an Account holder, I want my Streak to reset if I miss my Weekly Target with no Shield banked, so that the Streak stays meaningful.
31. As an Account holder, I want my Streak to break immediately if I go 3 consecutive days with zero Active Days, regardless of my weekly count, so that I can't cram all my activity into one day and call it consistent.
32. As an Account holder, I want a calendar heatmap of my Active Days, so that I can see my consistency at a glance.
33. As an Account holder, I want a streak-aware daily reminder notification, so that I'm nudged only when I haven't yet logged an Active Day and still need it to hit my Weekly Target.

**Quick Count / Roster**

34. As an Account holder, I want to create Tracked Profiles in my Roster, so that I can count Reps for other people without them needing an Account.
35. As an Account holder, I want to pick a Tracked Profile and an Exercise, then run Quick Count, so that I get an automatic rep count instead of counting by hand.
36. As an Account holder, I want to optionally set a target count for a Quick Count run, so that it auto-stops when the target is reached.
37. As an Account holder, I want to manually stop a Quick Count run at any time, so that I'm not locked into a target I set.
38. As an Account holder, I want Quick Count to produce a raw rep count with no Form Score, so that it stays fast and simple — it's not a form-training feature.
39. As an Account holder, I want each Quick Count run saved as a Tally against the Tracked Profile, so that I can review someone's history later.
40. As an Account holder, I want Quick Count to track only one person in frame at a time, so that counts aren't confused between people.
41. As an Account holder, I want my own Quick Count activity to NOT count toward my personal Streak/Active Days, so that tracking others isn't conflated with my own workout consistency.

**Sync / offline**

42. As an Account holder, I want completed Sets and Tallies to sync to the cloud automatically once I'm back online, so that I don't have to think about connectivity.
43. As an Account holder using two devices, I want my history to reconcile sensibly if I logged data offline on both, so that nothing is silently lost.
44. As a Web dashboard user, I want to see my Account's synced history, Streaks, and Roster read-only, so that I can check my progress from a larger screen.
45. As a developer, I want the repository/sync layer testable against an in-memory database and a fake remote, so that sync and migration logic don't require a live Firebase project to verify.

## Implementation Decisions

**Modules**

- **Pose Tracking Engine**: wraps MediaPipe Pose Landmarker (front camera via CameraX); emits a stream of pose-landmark frames. No rep-counting logic lives here.
- **Rep Counting Engine**: pure Kotlin, one state machine per Exercise (Squat, Push-up, Sit-up, Lunge, Jumping Jack). Consumes the landmark-frame stream, emits Rep events and a Set-level Form Score based on how many Reps passed that Exercise's range-of-motion threshold. No Android framework or camera dependency — this is Seam 1.
- **Streak Calculator**: pure function of an Account's Active Day history, its Weekly Target, and an injected "today," producing current Streak count, banked Streak Shields, and whether the gap-safeguard has fired. No persistence or clock dependency inside the function itself — this is Seam 2.
- **Repository layer**: `AccountRepository`, `SetRepository`, `TallyRepository`, `RosterRepository`. Room-backed (offline-first, per ADR-0002), with a background sync queue pushing to Firestore when online (per ADR-0001). Owns the Guest→Account migration transaction (per ADR-0004) — this is Seam 3.
- **Quick Count module**: reuses the Rep Counting Engine but skips Form Score gating and writes Tallies against a Tracked Profile instead of an Account's own Session. Enforces single-person-in-frame per ADR-0003.
- **Auth module**: Firebase Auth wrapper — sign up, sign in, Guest session state, and triggering migration on sign-up.
- **Notification module**: local, streak-aware daily reminder (WorkManager/AlarmManager), fires only when today isn't yet an Active Day and the Weekly Target isn't yet met.

**Data model (conceptual — no schema/file details)**

- **Account**: identity, Weekly Target, banked Streak Shield count, current Streak, notification preference.
- **Guest local record**: same shape as an Account's local data, unowned until migration assigns it an Account id.
- **Routine**: name, ordered list of (Exercise, target reps, rest interval). Bundled with the app; not user-editable in v1.
- **Session**: Account id, Routine id, timestamp, its Sets.
- **Set**: Session id, Exercise, target reps, actual reps, Form Score, Good Set flag, timestamp.
- **Roster / Tracked Profile**: owning Account id, display name.
- **Tally**: Tracked Profile id, Exercise, reps achieved, optional target, timestamp.
- Firestore collections mirror the above per ADR-0001. Android is the only writer of Set/Tally/pose-derived data; the Web project only reads (per ADR-0006).

**Specific interactions**

- Session flow: manual start → 5s countdown → live tracking (counter + per-Rep beep) → Set complete → per-Set summary → skippable rest timer → next Set → Session summary (reps vs. target, form note, Weekly Target progress).
- Lost tracking mid-Set: pause counting, show a warning banner, auto-resume on landmark re-detection; no partial Rep is counted for the gap.
- Guest → Account: local records get the new Account id assigned in one migration transaction at sign-up, not a background job — the user shouldn't see a window where migration is "in progress."

## Testing Decisions

Tests target the three seams' external behavior, not internal state-machine transitions, Compose recomposition, or Firestore wire format:

- **Rep Counting Engine** (Seam 1): unit tests driven by fixture landmark-frame sequences per Exercise — a full valid rep cycle, a partial/no-rep cycle, and a below-threshold-form cycle — asserting on Rep count and resulting Form Score. No CameraX, MediaPipe runtime, or UI involved.
- **Streak Calculator** (Seam 2): unit tests over constructed Active Day history fixtures and an injected "today," covering: target met, target missed with a Shield available, target missed with no Shield, and the gap-safeguard firing mid-week.
- **Repository/sync layer** (Seam 3): tests against an in-memory Room database and a fake remote, covering offline write → queue → sync-on-reconnect, and the Guest → Account migration transaction.

No prior art exists in this repo yet — it's greenfield. The first tests written against these seams set the pattern later work follows.

## Out of Scope

- Multi-person pose tracking (ADR-0003)
- In-browser/web pose tracking — Web is a separate project consuming synced data only (ADR-0006)
- Custom/user-built Routines — only the bundled set in v1
- XP, badges, or leagues — gamification is Streak + trend charts + Personal Bests only
- CSV or other data export
- Tracked Profile accounts, self-visibility of their own Tallies, or a "claim your history" flow
- Monetization or payments
- A CMS or remote-editable Routine content pipeline

## Further Notes

- The Web project is a separate codebase sharing the Firebase backend; this spec covers the Android app only, but the Firestore schema decisions here are a de facto contract the Web project depends on.
- Min SDK ~26+ is assumed for MediaPipe GPU-delegate performance; not yet validated against a real device matrix.
- Full settled design and rationale live in `CONTEXT.md` and `docs/adr/0001` through `0006` at the repo root.
