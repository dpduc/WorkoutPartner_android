Status: ready-for-agent

# 04 — Streak Calculator (Seam 2)

## Scope

Pure function, no persistence or clock dependency inside the function itself (inject "today"). Covers user stories 26-31.

- Input: an Account's Active Day history, its Weekly Target (default 3, per `CONTEXT.md`), and an injected "today."
- Output: current Streak count, banked Streak Shield count, and whether the gap-safeguard has fired.
- Rules (see `CONTEXT.md` Streak/Weekly Target/Streak Shield definitions and ADR-0005):
  - Meeting the Weekly Target banks a Streak Shield (banked Shields are capped — pick and document a cap; not specified numerically in the spec).
  - A banked Shield automatically covers one missed week instead of breaking the Streak.
  - Missing the Weekly Target with no Shield banked resets the Streak.
  - The Streak breaks immediately on 3 consecutive days with zero Active Days, regardless of weekly count (the gap-safeguard) — this check is independent of the weekly tally.
  - Multiple Sets/Sessions in one day still count as exactly one Active Day (per `CONTEXT.md`).

## Testing

Per spec's Testing Decisions, unit tests over constructed Active Day history fixtures and an injected "today," covering: target met, target missed with a Shield available, target missed with no Shield, and the gap-safeguard firing mid-week.

## Depends on

Nothing (pure function, independent of tickets 01-03).

## Out of scope

Persistence of Active Days (ticket 06), calendar heatmap / notification UI (tickets 10, 12).
