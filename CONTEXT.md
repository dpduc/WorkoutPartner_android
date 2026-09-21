# Workout Partner

An Android app that uses on-device pose tracking to count exercise reps and judge form, for people working out on their own and for people counting reps on someone else's behalf.

## Language

### People & accounts

**Athlete**:
Whoever is using the app on this device — either an Account holder or a Guest. Owns Sessions, Streak state, a Weekly Target, Personal Bests, a Roster, and the body-stats (name, age, height, weight, Activity Level) answered once during onboarding and used to tune Routine difficulty. Account and Guest are the two states an Athlete can be in; every feature is available in both.
_Avoid_: User, Profile, Player

**Account**:
An Athlete with a registered identity (email/password or provider sign-in). The only thing it adds over a Guest is cloud backup and multi-device sync. Created directly at sign-up, or by migrating a Guest.
_Avoid_: User, Profile

**Guest**:
An Athlete without an Account. Can use every feature — Routines, Streaks, Weekly Target, a Roster and Quick Count — but everything stays on-device. Signing up migrates all of it (Sessions, Sets, Roster, Tallies, Streak state) into the new Account. Signing in to an *existing* Account instead asks the Guest whether to merge their on-device data into that Account or discard it.
_Avoid_: Anonymous user

**Roster**:
The collection of Tracked Profiles belonging to an Athlete.

**Tracked Profile**:
A lightweight person record an Athlete creates to run Quick Count for someone else — a name with no login or Account of their own. Not visible or accessible to the person it represents.
_Avoid_: Student, Coachee, Buddy

**Activity Level**:
An Athlete's self-reported current activity, one of four tiers — Sedentary ("I rarely exercise"), Lightly Active ("a few times a week"), Active ("frequently active"), Very Active ("working out is my daily passion") — collected alongside the other body-stats and combined with BMI/age to tune Routine difficulty.
_Avoid_: Fitness level, Intensity (ambiguous with a Routine's own difficulty tier), Low/Medium/High (the retired 3-tier scale)

### Exercises & tracking

**Exercise**:
One of the five movement types the pose-tracking engine recognizes and can count reps for: Squat, Push-up, Sit-up, Lunge, Jumping Jack.

**Exercise Variant**:
An alternative way of performing an Exercise — same movement family and same tracked joint angle, but its own rep-counting and Form Score thresholds. Currently only one: **Step Jack**, a low-impact Jumping Jack that steps out to the side instead of jumping. Suggested to Athletes with a BMI of 30 or over, but the Athlete can switch either way before a Session. Because its thresholds differ, it is scored and ranked separately from its parent Exercise.
_Avoid_: Modification, Regression, Low-impact mode

**Rep**:
One complete cycle of an Exercise's motion, detected by that Exercise's state machine from pose landmarks.
_Avoid_: Repetition (fine in prose, but code/data should say Rep)

**Form Score**:
A quality measure derived from how many of a run's Reps passed the Exercise's (or Exercise Variant's) range-of-motion/angle threshold — computed the same way for a Set and a Quick Count Tally alike. (Earlier versions of this app deliberately withheld Form Score from Quick Count; that decision was reversed.)
_Avoid_: Accuracy, Quality score

### Self-tracking (Routines)

**Routine**:
A fixed, bundled, flat sequence of target Exercises, rep targets, and rest intervals. Authored into the app; not user-editable. Has no notion of repeated rounds — a multi-round plan is authored as a longer flat sequence. The bundled catalogue is 10 Routines (see ADR-0008).
_Avoid_: Program, Workout plan, Round (only modelled inside an AMRAP)

**AMRAP**:
A timed benchmark, separate from a Routine: the Athlete cycles a fixed circuit of Exercises for as many Rounds as possible within a time cap, resting when they choose. Scored `complete Rounds × reps per Round + extra reps`; Form Score is tracked but does not change the score, and a Rep below the Exercise's rep threshold is not counted. Its blocks are recorded as Sets. Not yet built (ADR-0008).
_Avoid_: Challenge, Test (in code and data; fine in prose)

**Before You Start**:
The preparation flow an Athlete goes through between picking a Routine and its first Set: a Workout Overview, Form Guides for any Exercises they haven't seen, a Position Check, then a countdown into the Session.
_Avoid_: Pre-workout, Setup wizard

**Form Guide**:
The how-to card for one Exercise or Exercise Variant — correct form, common mistakes, and what the pose tracker checks. Shown to an Athlete once per Exercise/Variant until seen; always reviewable afterwards.
_Avoid_: Exercise Guide Card, Tutorial

**Position Check**:
The camera-framing check that confirms the whole body is in frame at a workable distance before tracking starts. Advisory, not blocking — the Athlete can start anyway. Used both before a Session and before a Quick Count run.
_Avoid_: Camera Setup, Calibration

**Session**:
One instance of an Athlete working through a Routine, start to finish, made up of one or more Sets.
_Avoid_: Workout (too generic — could mean Session, Set, or Routine)

**Set**:
One continuous block of Reps of a single Exercise (or Exercise Variant) performed within a Session, ending at rest or a manual stop. Carries a target rep count and a Form Score.

**Good Set**:
A Set whose actual Reps meet or exceed its target rep count, and whose Form Score is at or above the Exercise's (or Exercise Variant's) threshold. Both conditions are required — hitting the rep count with bad form is not a Good Set.

**Active Day**:
A calendar day on which the Athlete completed at least one Set. Counted once per day regardless of how many Sessions happened that day.
_Avoid_: Session (a day can have several Sessions but is only ever one Active Day)

### Progress

**Streak**:
The count of consecutive Weeks in which the Athlete met their Weekly Target. Broken either by missing a Weekly Target with no Streak Shield banked, or by a run of consecutive days with zero Active Days regardless of the weekly count.
_Avoid_: Daily streak (this app's Streak is week-based, not day-based)

**Weekly Target**:
The number of Active Days an Athlete aims for each week (adjustable, defaults to 3) to keep their Streak alive. When Guest data is merged into an existing Account, the Account's Weekly Target wins.

**Streak Shield**:
A credit an Athlete banks by meeting their Weekly Target, automatically spent to cover one Week that misses the target instead of breaking the Streak. Banked Shields are capped.

**Personal Best**:
An Athlete's highest recorded rep count or Form Score for a given Exercise or Exercise Variant, across all their Sets. A Step Jack and a Jumping Jack each have their own.

### Quick Count

**Quick Count**:
The mode where an Athlete records Reps for a Tracked Profile rather than themselves: pick a Tracked Profile, pick an Exercise, optionally set a target, run the camera, get a Tally. Uses the same rep-detection engine as Sets, with no Form Score *gating* — every Rep still counts toward the total regardless of form — but its Tally does report the run's total reps, total time, and average Form Score.
_Avoid_: Coach mode, Roster mode

**Tally**:
The record produced by one Quick Count run — Tracked Profile, Exercise, reps achieved, optional target, timestamp, total time, and average Form Score. Belongs to the Tracked Profile, not to the Athlete's own Session/Streak history.
_Avoid_: Count (ambiguous with "rep count"), Quick Set
