# Workout Partner

An Android app that uses on-device pose tracking to count exercise reps and judge form, for people working out on their own and for people counting reps on someone else's behalf.

## Language

### People & accounts

**Account**:
A registered identity (email/password or provider sign-in) that owns Routines history, Streak state, and a Roster. Created directly at sign-up, or by migrating a Guest. Also carries the body-stats it answered once during onboarding — name, age, height, weight, and Activity Level — used to tune Routine difficulty; a Guest answers the same questions before an Account exists, and that answer is carried over on migration.
_Avoid_: User, Profile

**Guest**:
Someone using the app without an Account. Their Sets and Tallies persist on-device and are not lost, but stay local until they sign up and migrate into an Account.
_Avoid_: Anonymous user

**Roster**:
The collection of Tracked Profiles belonging to an Account.

**Tracked Profile**:
A lightweight person record an Account creates to run Quick Count for someone else — a name with no login or Account of their own. Not visible or accessible to the person it represents.
_Avoid_: Student, Coachee, Buddy

**Activity Level**:
An Account's self-reported daily activity intensity — Low, Medium, or High — collected alongside its other body-stats and combined with BMI/age to tune Routine difficulty.
_Avoid_: Fitness level, Intensity (ambiguous with a Routine's own difficulty tier)

### Exercises & tracking

**Exercise**:
One of the five movement types the pose-tracking engine recognizes and can count reps for: Squat, Push-up, Sit-up, Lunge, Jumping Jack.

**Rep**:
One complete cycle of an Exercise's motion, detected by that Exercise's state machine from pose landmarks.
_Avoid_: Repetition (fine in prose, but code/data should say Rep)

**Form Score**:
A quality measure derived from how many of a run's Reps passed the Exercise's range-of-motion/angle threshold — computed the same way for a Set and a Quick Count Tally alike. (Earlier versions of this app deliberately withheld Form Score from Quick Count; that decision was reversed.)
_Avoid_: Accuracy, Quality score

### Self-tracking (Routines)

**Routine**:
A fixed, bundled sequence of target Exercises, rep targets, and rest intervals. Authored into the app; not user-editable in v1.
_Avoid_: Program, Workout plan

**Session**:
One instance of an Account working through a Routine, start to finish, made up of one or more Sets.
_Avoid_: Workout (too generic — could mean Session, Set, or Routine)

**Set**:
One continuous block of Reps of a single Exercise performed within a Session, ending at rest or a manual stop. Carries a target rep count and a Form Score.

**Good Set**:
A Set whose actual Reps meet or exceed its target rep count, and whose Form Score is at or above the Exercise's threshold. Both conditions are required — hitting the rep count with bad form is not a Good Set.

**Active Day**:
A calendar day on which the Account completed at least one Set. Counted once per day regardless of how many Sessions happened that day.
_Avoid_: Session (a day can have several Sessions but is only ever one Active Day)

### Progress

**Streak**:
The count of consecutive Weeks in which the Account met its Weekly Target. Broken either by missing a Weekly Target with no Streak Shield banked, or by a run of consecutive days with zero Active Days regardless of the weekly count.
_Avoid_: Daily streak (this app's Streak is week-based, not day-based)

**Weekly Target**:
The number of Active Days an Account aims for each week (adjustable, defaults to 3) to keep their Streak alive.

**Streak Shield**:
A credit an Account banks by meeting its Weekly Target, automatically spent to cover one Week that misses the target instead of breaking the Streak. Banked Shields are capped.

**Personal Best**:
An Account's highest recorded rep count or Form Score for a given Exercise, across all their Sets.

### Quick Count

**Quick Count**:
The mode where an Account records Reps for a Tracked Profile rather than themselves: pick a Tracked Profile, pick an Exercise, optionally set a target, run the camera, get a Tally. Uses the same rep-detection engine as Sets, with no Form Score *gating* — every Rep still counts toward the total regardless of form — but its Tally does report the run's total reps, total time, and average Form Score.
_Avoid_: Coach mode, Roster mode

**Tally**:
The record produced by one Quick Count run — Tracked Profile, Exercise, reps achieved, optional target, timestamp, total time, and average Form Score. Belongs to the Tracked Profile, not to the Account's own Session/Streak history.
_Avoid_: Count (ambiguous with "rep count"), Quick Set
