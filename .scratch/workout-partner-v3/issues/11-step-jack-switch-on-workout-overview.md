# 11: Step Jack switch on Workout Overview

**What to build:** Wire ticket 08's Step Jack engine into the Workout Overview (ticket 03). When the chosen Routine contains Jumping Jack, the Overview shows a Jumping Jack/Step Jack toggle, defaulted to Step Jack when the Athlete's BMI is ≥ 30, switchable either way for that Session only.

**Blocked by:** 08, 03.

**Status:** ready-for-human

- [x] The toggle appears on the Workout Overview only when the selected Routine contains a Jumping Jack step — absent otherwise.
- [x] Defaults to Step Jack when BMI ≥ 30, Jumping Jack otherwise.
- [x] The Athlete can switch either way before starting; the choice applies to the whole Session's Jumping Jack steps and is not persisted between Sessions.
- [x] Switching correctly threads the chosen Variant into the Session's steps so ticket 08's Good Set / Personal Best / persistence behavior applies.
- [ ] Manually verified: starting a Routine containing Jumping Jack at a BMI ≥ 30 profile shows Step Jack pre-selected; completing a Set records it as "Step Jack" in history.

## Comments

Implemented as spec'd. `WorkoutOverviewScreen` gets a `SingleChoiceSegmentedButtonRow` toggle (shown only when `RoutineWithSteps.hasJumpingJack`, a new shared extension), defaulting to `ExerciseVariant.STEP_JACK` when `RoutineDifficulty.isObese(bodyStats)` is true — a new public function reusing `compute`'s existing private `bmiCategory`/BMI formula rather than re-deriving it, exposing exactly the `BmiCategory.OBESE` cutoff (BMI ≥ 30, confirmed inclusive by a dedicated test at exactly 30.0) the ticket asks for.

The choice is hoisted to the new `BeforeYouStartScreen` (ticket 10), not owned by `WorkoutOverviewScreen` itself, so it survives a detour into Form Guides before flowing back through `onReadyForSession(jumpingJackVariant)` → `MainActivity` → `AppScreen.Session(routine, jumpingJackVariant)` → `SessionScreen` → `SessionViewModel`, which applies it to every Jumping Jack step (not just the first) when building `SessionEngine`'s `RoutineStep`s — confirmed in review (Spec axis) to be correctly threaded end-to-end for a multi-step Routine, with `AppScreen.Session(` having only the one call site so there's no bypass path. Plain `remember`ed Compose state, per this ticket's own "not persisted between Sessions."

As a consequence of hoisting the choice this way, `RoutineWithSteps.trackedExercises()` (ticket 10) and the Overview's own step-name labels now reflect the selected Variant too — if Step Jack is toggled on, Form Guides (both the automatic gate and "Review form") and the "What you'll do" list show Step Jack, not stale Jumping Jack labels. Flagged in review (Spec axis) as a reasonable consequence of the toggle rather than unrelated scope creep.

Caught in review (Standards axis) and fixed before commit: three separate call sites (`WorkoutOverviewScreen`'s step labels, `trackedExercises`, and `SessionViewModel`'s `RoutineStep` building) each independently re-derived "does the chosen Variant apply to this step" as `exercise == Exercise.JUMPING_JACK`, and `hasJumpingJack` was computed identically in two files. Consolidated into two shared helpers in `WorkoutOverviewScreen.kt`: `RoutineWithSteps.hasJumpingJack` and a `resolveVariant(exercise, chosenVariant)` function that generalizes the check via `ExerciseVariant.parentExercise` (ticket 08's existing field) instead of hardcoding Jumping Jack — `SessionViewModel` now imports and calls the same function rather than reimplementing it.

**Not manually verified — no device or emulator available in this session** (`adb devices` returns empty), same limitation as ticket 09. Everything else was verified by compiling, the full passing unit test suite (including new tests for `isObese`, `hasJumpingJack`, `resolveVariant`, and `trackedExercises`'s Variant substitution), and `/code-review` tracing the actual navigation/data flow end to end. The ticket's own explicit manual-verification line — Step Jack pre-selected at BMI ≥ 30, and a completed Set recording as "Step Jack" in history — needs a real device; status set to `ready-for-human` for that reason, matching ticket 09's precedent.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the consolidation above came from that review. Spec axis found no missing/partial checklist lines, no scope creep, and specifically confirmed the multi-step threading and inclusive BMI ≥ 30 threshold both hold.
