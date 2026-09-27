Status: ready-for-agent

# Ship the 10-Routine bundled catalogue

## Problem Statement

`docs/workout-routines-system.md` designs a full catalogue of 10 structured
Routines, spread across every BMI band and activity level — from
joint-safe, no-jump work for a beginner with a high BMI, to a 4-round HIIT
circuit for an advanced athlete. None of that has actually shipped:
`BundledRoutines.kt` still seeds the same 3 placeholder Routines
(`Full Body Basics`, `Lower Body Focus`, `Quick Upper Body`) it always has,
so an Athlete opening the Routines picker today sees a shallow, arbitrary
list that doesn't reflect the BMI-aware design the app is supposed to
offer, and doesn't give a beginner, an underweight athlete, or an advanced
athlete anything actually suited to them.

[ADR-0008](../../docs/adr/0008-ten-routine-catalogue-and-separate-amrap-mode.md)
already decided the 10 Routines become the real bundled catalogue (Part B
of that doc, AMRAP, becomes its own mode instead and is explicitly not
decided there) — this spec is that adoption, scoped to what's actually
buildable against today's schema and engine with no open product
questions.

## Solution

Replace `BundledRoutines.kt`'s 3 placeholder Routines with the 10 from
`workout-routines-system.md`, each unrolled from the doc's "Rounds" into a
flat, ordered `RoutineStepEntity` sequence (per ADR-0008 — a Routine has no
round concept; a multi-round design is authored as a longer flat sequence).
Every step uses the standard `Exercise` + its existing
`ExerciseProfiles` thresholds — none of the doc's per-Routine custom angles
(Half Squat 120°, Deep Squat ≤95°, incline Push-up ≤110°) are adopted, per
ADR-0008's own call; they're coaching text, not new Exercise Variants.
Every step stores `Exercise.JUMPING_JACK`, never `ExerciseVariant.STEP_JACK`
directly — Step Jack is chosen through the Workout Overview switch
(`workout-partner-v3` ticket 11), not baked into seed data. No schema
change, no new engine: this is a content change to one object plus its
test, running through the `RoutineFormat`/`RoutineStepEntity`/`SessionEngine`
machinery that already exists.

## User Stories

1. As an Athlete with a BMI ≥ 30 who is new to exercise, I want a Routine
   that never asks me to jump or squat deep, so that I can build a workout
   habit without joint pain.
2. As that same Athlete, I want a second, slightly more active low-impact
   Routine once I've built some consistency, so that I have somewhere to
   progress to without jumping straight to a high-impact Routine.
3. As an underweight Athlete (BMI < 18.5), I want Routines that emphasize
   controlled upper-body and core work, so that I can build muscle rather
   than burn calories I don't have to spare.
4. As that same Athlete, I want a Routine focused on lower-body strength
   with a real depth requirement (Deep Squat), so that I can build leg
   muscle deliberately.
5. As an overweight Athlete (BMI 25–29.9), I want a Routine that mixes
   cardio and strength work at a moderate pace, so that I can burn calories
   without the joint impact a high-BMI Athlete needs to avoid.
6. As that same Athlete, I want a core-focused Routine as an alternative,
   so that I have variety within the intensity band that suits me.
7. As an Athlete with a normal BMI (18.5–24.9) and a moderate activity
   level, I want a well-rounded full-body Routine covering all four core
   movement families, so that I get balanced training in one sitting.
8. As that same Athlete once more advanced, I want a Routine with faster
   reps, shorter rests, and full range-of-motion targets, so that the app
   still challenges me as I improve.
9. As an advanced Athlete, I want a genuinely high-intensity Routine with
   minimal rest between exercises, so that I can get a real cardio
   conditioning effect in a short session.
10. As any Athlete regardless of BMI, I want a short "desk break" Routine I
    can do any time of day, so that I have something for a 10-minute window
    that doesn't require reading a BMI-tailored recommendation first.
11. As an Athlete picking any of the 10 Routines, I want its rep targets and
    rest intervals to match what `workout-routines-system.md` actually
    designed for it, so that the difficulty gradient across the catalogue is
    real and not arbitrary.
12. As an Athlete whose Routine includes a Lunge step, I want the rep target
    to reflect both legs (as the source design intends), so that the target
    isn't silently half of what was designed.
13. As an Athlete, I want every Routine to still show its `RoutineFormat`
    badge (STANDARD/HIIT/etc., `workout-partner-v2` ticket 02), so that the
    catalogue's presentation doesn't regress when its content changes.
14. As an Athlete with the Workout Overview's low-impact switch on, I want
    every Jumping Jack step in any of the 10 Routines to become Step Jack,
    exactly as it already does for the existing 3, so that the new content
    doesn't bypass that switch.
15. As an Athlete whose account's `RoutineDifficulty` tier scales rep/rest
    numbers (`workout-partner-v2` ticket 02), I want that scaling to keep
    applying to the new 10 Routines the same way it does today, so that
    difficulty tuning isn't lost when the catalogue is replaced.
16. As a developer maintaining `BundledRoutines.kt`, I want an automated
    test asserting each Routine's step sequence is internally consistent
    (contiguous order, a zero-rest final step, no hard-wired Step Jack), so
    that a future edit to the catalogue can't silently break those
    invariants the way hand-authored data risks.
17. As an Athlete who already has the app installed with the old 3-Routine
    catalogue seeded, I want to understand that a fresh install (not my
    existing one) is how I'd see the new 10 Routines, so that I'm not
    confused when my existing data doesn't update on its own.
18. As a product owner, I want the AMRAP half of `workout-routines-system.md`
    left alone by this change, so that shipping the Routine catalogue isn't
    blocked on the AMRAP engine's still-open product questions
    (`workout-partner-v2` ticket 04).
19. As an Athlete, I want "Quick Upper Body" (today's only AMRAP-tagged
    Routine) to no longer exist as a mislabeled Routine once the real 10
    ship, so that the app doesn't advertise an AMRAP experience it doesn't
    actually run.
20. As a developer, I want the doc's few internal inconsistencies (Routine
    09 is named "High-Intensity Tabata/HIIT" in the matrix table but
    "High-Intensity Metabolic HIIT" in its detailed write-up; a couple of
    rep counts are given as ranges like "8–10") resolved to one concrete
    value each before they become seed data, so that `BundledRoutines.kt`
    doesn't encode an ambiguity.

## Implementation Decisions

- **Seam**: `BundledRoutines.all` (the private `List<Pair<RoutineEntity,
  List<RoutineStepEntity>>>`) stays the one place this content lives — no
  new seam. `seedIfEmpty`, `RoutineDao`, `SessionEngine`, `RoutineFormat`,
  and `RoutineDifficulty` are all unchanged.
- **Full replacement, not additive.** The existing `full_body_basics`,
  `lower_body_focus`, and `quick_upper_body` entries are removed entirely
  and replaced by the 10 below — ADR-0008 says the 10 Routines *become* the
  bundled catalogue, not that they're added alongside 3 unrelated
  placeholders.
- **IDs**: `rt_01` through `rt_10`, matching the source doc's `Mã Routine`
  column (RT-01..RT-10) lowercased, the same style ADR-0008's own sketch
  used — a change from the existing descriptive-slug style
  (`full_body_basics`), justified because these IDs now have a canonical
  external numbering to stay traceable to.
- **Rounds unrolled to flat steps**, per Routine (`Exercise`, target reps,
  rest seconds, in order — the final step of every Routine gets 0 rest,
  matching the existing two Routines' own convention, regardless of what
  rest value the source doc gives for the transition into cool-down):

  | ID | Name | Format tag | Flattened steps (Exercise ×reps, rest s) |
  | --- | --- | --- | --- |
  | rt_01 | Joint-Safe Mobility & Tone | STANDARD | Squat×8/35, Push-up×6/35, Jumping Jack×15/45, Squat×8/35, Push-up×6/35, Jumping Jack×15/0 |
  | rt_02 | Gentle Low-Impact Cardio | STANDARD | Squat×10/30, Jumping Jack×20/30, Lunge×12/40, Squat×10/30, Push-up×8/30, Jumping Jack×20/0 |
  | rt_03 | Lean Muscle Upper & Core | STANDARD | (Push-up×10/45, Sit-up×12/45, Push-up×6/60) × 3, last rest 0 |
  | rt_04 | Lower Body Muscle Builder | STANDARD | (Squat×12/40, Lunge×20/40, Squat×10/60) × 3, last rest 0 |
  | rt_05 | Steady Metabolic Burner | STANDARD | (Jumping Jack×25/25, Squat×12/30, Push-up×8/30, Sit-up×10/45) × 3, last rest 0 |
  | rt_06 | Core Stability & Flow | STANDARD | (Sit-up×14/30, Squat×12/30, Jumping Jack×20/40) × 3, last rest 0 |
  | rt_07 | Full Body Basics Plus | STANDARD | (Squat×12/25, Push-up×10/25, Sit-up×12/25, Jumping Jack×25/45) × 3, last rest 0 |
  | rt_08 | Athletic Power Circuit | STANDARD | (Lunge×14/20, Push-up×12/20, Squat×15/20, Jumping Jack×30/40) × 3, last rest 0 |
  | rt_09 | High-Intensity Metabolic HIIT | HIIT | (Jumping Jack×35/15, Push-up×12/15, Squat×15/15, Sit-up×12/35) × 4, last rest 0 |
  | rt_10 | Express Desk-Worker Reset | STANDARD | Squat×10/25, Jumping Jack×20/25, Lunge×8/35, Squat×10/25, Jumping Jack×20/25, Sit-up×10/0 |

  (Squat above always means the base `Exercise.SQUAT` — the doc's "Half
  Squat"/"Deep Squat" naming inside RT-01/RT-02/RT-04 is coaching text only,
  per ADR-0008, not a different threshold or Variant.)
- **Lunge reps are totals across both legs**, not per-leg, matching the one
  existing Lunge step's own convention (`lower_body_focus`'s Lunge is a
  single total). Where the source doc states a per-leg count with its own
  total in parentheses (e.g. RT-02's "6 reps mỗi bên (tổng 12 reps)"), that
  total is used directly; where only a per-leg count is given (RT-04's
  "10 reps mỗi bên", RT-08's "7 reps/chân", RT-10's "4 reps/chân"), the
  total is computed as double it (20, 14, 8 respectively).
- **Rep ranges collapsed to one number.** RT-03's "8–10" Push-up and
  "10–12" Sit-up become 10 and 12 (the upper bound) — a concrete content
  decision made here, the same "picked here, not a settled product
  decision" spirit `BundledRoutines.kt`'s own doc comment already claims
  for its current 3 Routines.
- **Format tags**: only `rt_09` is tagged `HIIT` — it's the one Routine the
  source doc itself names for it ("High-Intensity Metabolic HIIT" in its
  detailed section; the matrix table's "High-Intensity Tabata/HIIT" is
  treated as the same Routine under its more specific name). Every other
  Routine is tagged `STANDARD`. None are tagged `TABATA` or `AMRAP` — no
  Routine in the replacement catalogue is a real Tabata interval design
  (that engine doesn't exist yet, ticket `04-interval-timer-engine.md`), and
  `AMRAP` stops being used as a Routine tag at all now that `quick_upper_body`
  is gone, matching ADR-0008's direction that AMRAP shouldn't remain a
  `RoutineFormat` value once real AMRAP exists — it simply reaches zero
  users of that tag now, ahead of that engine.
- **No coaching-text or warm-up/cool-down fields added.** The source doc's
  warm-up/cool-down copy and per-step form cues ("Đùi song song sàn", "giữ
  thẳng lưng") aren't modeled anywhere today (`RoutineStepEntity` has no
  notes field, and Before You Start has no warm-up/cool-down phase) — they
  stay documentation, not seed content, consistent with the existing 3
  Routines carrying no such text either.
- **BMI-band association is not modeled.** Each Routine's target BMI band
  (e.g. rt_01 for BMI ≥ 30) is descriptive/advisory only, exactly as
  ADR-0008 already ruled — no "recommended for you" tag or filter is added
  to the picker in this change.

## Testing Decisions

- New `BundledRoutinesTest` (plain JUnit, no Android/Room dependency — the
  same "assert on the plain data" style as `RoutineDifficultyTest`), added
  next to `BundledRoutines.kt`'s module, asserting over every entry in
  `BundledRoutines.all` (the object's `all` property will need to go from
  `private` to internal/testable visibility, or the test iterates via
  `seedIfEmpty` against a real in-memory `RoutineDao` — prefer exposing
  `all` for a direct, fast, non-Room test, matching how `RoutineDifficulty`
  is tested as a pure function rather than through the database):
  - Exactly 10 Routines, with ids `rt_01`..`rt_10`.
  - Each Routine's steps have contiguous `orderIndex` starting at 0.
  - Each Routine's last step has `restIntervalSeconds == 0`.
  - No step anywhere uses `ExerciseVariant.STEP_JACK` — every Jumping Jack
    step is the base `Exercise.JUMPING_JACK` (the "never hard-wired"
    invariant from ADR-0008).
  - Spot-check a handful of Routines' full step sequences against the table
    above (at minimum `rt_01`, `rt_09`, `rt_10` — the shortest, the
    round-heaviest, and the two-BMI-band one).
- No new `SessionEngine`/`RoutineDifficulty`/migration coverage is needed —
  none of those are touched. If a `MigrationTest` or similar currently
  asserts on the specific old seed content (`full_body_basics` et al.) by
  name, it will need updating to match; confirm there isn't one before
  assuming this is content-only.
- Manual/on-device verification is optional for this change (it's seed data
  behind an already-tested render path), but worth a quick pass: open the
  Routines picker on a fresh install and confirm 10 Routines appear with
  plausible names/badges, and that toggling the Workout Overview's
  low-impact switch still swaps Jumping Jack -> Step Jack inside one of the
  new Routines.

## Out of Scope

- **AMRAP (Part B of the source doc) entirely** — the 6 AMRAP benchmarks,
  `AmrapConfigEntity`/`AmrapCircuitStepEntity`/`AmrapResult` schema, the
  round/time-cap scoring engine, and its own Room migration. ADR-0008
  already flags this as needing its own scoping pass; `workout-partner-v2`
  ticket `04-interval-timer-engine.md` is `needs-triage` for exactly this
  reason and stays that way.
- **A real HIIT/Tabata work/rest interval timer.** `rt_09`'s `HIIT` tag is
  a label only, same as every other `RoutineFormat` value today — it still
  runs through the plain rep-target + rest-interval `SessionEngine`.
- **BMI-band "recommended for you" pinning/filtering** in the Routines
  picker UI — advisory metadata per ADR-0008, not built here.
- **Promoting any per-Routine angle variation (Half Squat, Deep Squat,
  incline Push-up) to a real Exercise Variant** with its own thresholds —
  ADR-0008 explicitly declines this; Step Jack remains the only Variant.
- **A reseed-on-update mechanism** for installs that already seeded the old
  3-Routine catalogue (`seedIfEmpty` only ever seeds once, when the table is
  empty). `RoutineDao`'s own doc comment already anticipates this as "a
  future reseed on update" — this spec ships the new content for fresh
  installs only; making existing installs pick it up is a separate,
  unscoped change.
- **Any warm-up/cool-down phase or per-step coaching-text field.** Not part
  of today's Before You Start flow or `RoutineStepEntity`; the doc's copy
  for these stays documentation.

## Further Notes

- This spec deliberately covers only the half of `workout-routines-system.md`
  that ADR-0008 already adopted with no open questions. The AMRAP half
  needs its own spec once `04-interval-timer-engine.md` is actually
  triaged — that ticket's own comment already narrows its remaining scope
  to "the HIIT/Tabata work/rest interval timer" now that AMRAP has been
  carved out as a separate mode, so a future AMRAP spec and a future
  interval-timer spec are two different pieces of work, not one.
- `docs/workout-routines-system.md`'s own header note ("content
  specification for a roadmap, not a description of what ships") should be
  revisited once this ships — at that point the Routine half of the doc
  *is* what ships, and only the AMRAP half remains roadmap-only.
