# 01: Ship the 10-Routine catalogue in `BundledRoutines.kt`

**What to build:** Replace `BundledRoutines.kt`'s 3 placeholder Routines
(`full_body_basics`, `lower_body_focus`, `quick_upper_body`) with the 10 real
Routines from `docs/workout-routines-system.md`, adopted by ADR-0008. An
Athlete opening the Routines picker on a fresh install sees all 10 —
`rt_01` through `rt_10` — each with the rep targets, rest intervals, and
step order the source doc actually designed for it, unrolled from its
"Rounds" into flat `RoutineStepEntity` sequences. No schema or engine
change: this runs through the existing `RoutineFormat`/`RoutineStepEntity`/
`SessionEngine`/`RoutineDifficulty` machinery untouched. See `../spec.md`
for the full per-Routine step table, the content decisions made for the
doc's ambiguities (rep ranges, per-leg Lunge counts, format tags), and what's
explicitly out of scope (AMRAP, a real HIIT/Tabata timer, BMI-band
recommendation UI, a reseed mechanism for already-seeded installs).

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `BundledRoutines.kt`'s `all` list contains exactly the 10 Routines
      `rt_01`..`rt_10` (per `../spec.md`'s table) and no longer contains
      `full_body_basics`, `lower_body_focus`, or `quick_upper_body`.
- [x] Each Routine's steps are unrolled from the doc's Rounds into a flat,
      ordered sequence (`orderIndex` 0..n-1, contiguous) using only the base
      `Exercise` enum values — no step stores `ExerciseVariant.STEP_JACK`
      directly.
- [x] Every Routine's final step has `restIntervalSeconds == 0`.
- [x] Lunge steps store the two-leg total (not a per-leg count), per the
      spec's worked conversions.
- [x] `rt_09` (High-Intensity Metabolic HIIT) is tagged `RoutineFormat.HIIT`;
      every other Routine is tagged `RoutineFormat.STANDARD`. No Routine is
      tagged `TABATA` or `AMRAP`.
- [x] New `BundledRoutinesTest` (plain JUnit, no Room/Android dependency)
      asserts: exactly 10 Routines with the right ids; contiguous
      `orderIndex` per Routine; zero-rest final step per Routine; and the
      full step sequence for `rt_01`, `rt_09`, and `rt_10` matches the spec's
      table exactly. No standalone "no step uses `ExerciseVariant.STEP_JACK`"
      assertion — see Comments for why.
- [x] No existing test (`MigrationTest` or otherwise) asserted on the old
      seed content by name/id — confirmed by grep before changing anything,
      and by the full `data`/`app` unit test suites passing after.
- [x] Typecheck and the full test suite pass.
- [ ] Manual spot-check (optional, not done this session): a fresh install
      shows 10 Routines in the picker with plausible names/badges, and
      toggling the Workout Overview's low-impact switch still swaps Jumping
      Jack -> Step Jack inside one of the new Routines.

## Comments

Implemented as spec'd: `BundledRoutines.kt`'s 3 placeholders replaced by
`rt_01`..`rt_10`, each Routine's Rounds unrolled via a small `rounds()`
helper (repeat + zero only the true final step's rest), all 10 step
sequences matching `../spec.md`'s table exactly (verified line-by-line in
review, see below). New `BundledRoutinesTest` covers the structural
invariants plus a full-sequence spot-check of `rt_01`/`rt_09`/`rt_10`.

Reviewed via `/code-review` (Standards + Spec axes, fixed point `9fc13bf`)
before commit:

- **Standards axis**, both fixed: doc comments on the new private helpers
  were single unwrapped 140-300 char lines, against this file's own
  (and `RoutineDifficulty.kt`'s) wrapped-KDoc convention — rewrapped.
  `Triple<Exercise, Int, Int>` stood in for "one flattened step"
  (Primitive Obsession/Data Clump — `.copy(third = 0)` in `rounds()` was
  exactly the positional-access unreadability a bare tuple invites, and
  `BundledRoutinesTest` was independently re-deriving the same shape) —
  replaced with a named `internal data class StepSpec`, shared as-is by
  both the seed data and the test's assertions. `Exercise.rep(...)` was
  renamed to `Exercise.step(...)` (a Mysterious Name collision with
  CONTEXT.md's own "Rep" glossary entry — this builds a step, not a rep).
- **Spec axis**: data-correctness check confirmed all 10 routines' exercises/
  reps/rests/format tags match `../spec.md`'s table exactly, no discrepancies.
  One partial flagged and deliberately kept as-is: the ticket's own checklist
  (above) asks the test to assert "no step uses `ExerciseVariant.STEP_JACK`";
  `BundledRoutinesTest` has a comment explaining this instead of an
  assertion, because `RoutineStepEntity.exercise` is typed `Exercise` with no
  `ExerciseVariant` field at all — the check would be vacuously true and
  would test the type system, not this code. Judged in review as a
  reasonable call, not a coverage gap, so left as a comment rather than
  forcing a no-op assertion just to tick the box literally.

Not done this session: the manual on-device spot-check (last checkbox) —
optional per the ticket's own wording, and this is a pure content/seed-data
change behind an already-tested render path.
