# A ten-Routine catalogue, with AMRAP as its own timed mode

`docs/workout-routines-system.md` designs 10 structured Routines and 6 AMRAP benchmarks; the app ships 3 Routines, and "AMRAP" is only a display tag on one of them. We adopt both as the roadmap: the 10 Routines become the bundled catalogue, and AMRAP is built as a **separate mode**, not as a kind of Routine. A Routine is a fixed, round-free sequence with target reps and rests (CONTEXT.md); an AMRAP has no rep targets and no programmed rest — the Athlete cycles a circuit for a time cap and is scored `rounds × reps per round + extra reps`. Folding that into Routine would make "Round" a Routine concept, which CONTEXT.md deliberately avoids, and would bend Good Set (which needs a target) out of shape.

## Decisions that keep this consistent with what already exists

- **Routines stay flat.** The doc's "3 Rounds" are unrolled into ordered steps when bundled, exactly as the existing three Routines already are. Rounds exist only inside an AMRAP.
- **One threshold pair per Exercise.** The doc's per-Routine angles (Half Squat 120°, Deep Squat ≤ 95°, incline Push-up ≤ 110°) are **not** adopted: they would be Exercise Variants, and Step Jack is the only Variant. Those Routines use the standard Exercise thresholds and carry the variation as coaching text. Promoting one to a real Variant is a separate decision.
- **Step Jack is never hard-wired.** Routine steps store Jumping Jack; low-impact is chosen through the Workout Overview switch, defaulting to Step Jack at BMI ≥ 30 (CONTEXT.md, Exercise Variant). This is why the doc's RT-01/RT-02/AM-01 do not name Step Jack in the seed data.
- **AMRAP work is recorded as Sets.** Each exercise block in a round is stored as a Set, so Active Day, Streak (ADR-0005), Personal Best and Guest parity (ADR-0007) work unchanged; a separate AMRAP result records rounds, extra reps, score and average Form Score. Form Score is tracked but never changes the AMRAP Score, and a rep below the rep threshold is not counted (the doc's AMRWPFAP rule).
- **`RoutineFormat` keeps HIIT/Tabata as display labels only.** A real work/rest interval timer for those stays deferred (`workout-partner-v2` ticket 04, now narrowed to that). AMRAP stops being a `RoutineFormat` value once the mode exists, and "Quick Upper Body", currently tagged AMRAP, is retagged.
- **Recommendation is not decided here.** The doc's BMI bands, the "Khuyên dùng" pin, and AM-02 as an onboarding baseline are advisory metadata. Difficulty tuning (`workout-partner-v2` ticket 02) remains the only mechanism that adjusts a Routine for the Athlete.

## Consequences

The AMRAP ticket has to settle how a partly finished block is stored as a Set and design the schema (config, circuit steps, result), plus a Room migration; Firestore mirrors are added when sync of these records is scoped (ADR-0010). Until then `workout-routines-system.md` is the content source for the catalogue, not a description of what ships.
