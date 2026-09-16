Status: needs-triage

# 04 — Real HIIT/Tabata/AMRAP timer & round engine (deferred)

## Scope (placeholder — not yet fully specified)

`02-routine-difficulty-and-format-tags.md` ships HIIT/Tabata/AMRAP as
**labels only** on top of the existing rep-target + rest-interval
`SessionEngine`. This ticket is the deferred follow-up: an actual
timer/round-based execution model —

- True Tabata: fixed work/rest seconds (e.g. 20s work / 10s rest) x N
  rounds, driven by a countdown timer rather than rep targets.
- AMRAP: as-many-rounds-as-possible within a fixed total time window.
- HIIT: configurable work/rest interval pairs, possibly per-exercise within
  one circuit.

This needs product decisions not yet made (exact interval lengths per
format/difficulty tier, how rounds interact with existing Set/Good-Set
grading and Weekly-Target/Streak accounting per ADR-0005's weekly-not-daily
cadence, whether `SessionEngine`'s phase state machine can be extended or
needs a parallel timer-based engine) before it's ready for an agent to pick
up. File as `needs-triage` until scoped.

## Depends on

`02-routine-difficulty-and-format-tags.md` (format tagging ships first,
without this).
