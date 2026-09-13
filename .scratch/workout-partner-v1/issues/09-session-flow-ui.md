Status: ready-for-agent

# 09 — Self-tracking Session flow UI

## Scope

Covers user stories 10, 14, 15, 17-21, 23. Implements the flow from spec's "Specific interactions":

manual start → 5s countdown → live tracking (rep counter + per-Rep beep) → Set complete → per-Set summary → skippable rest timer → next Set → Session summary (reps vs. target, form note, Weekly Target progress).

- Routine picker (bundled Routines only, per Out of Scope — no custom Routine authoring).
- 5-second countdown after tapping start, before counting begins.
- Live tracking screen: on-screen rep counter, beep per counted Rep, warning banner + auto-resume driven by the Pose Tracking Engine's lost-tracking signal (ticket 03).
- Per-Set summary: reps vs. target, form note, Good Set determination (per `CONTEXT.md` — both rep target AND Form Score threshold required).
- Skippable rest timer between Sets, per Routine's configured rest interval.
- Session summary: overall performance + Weekly Target progress (reads from ticket 04's Streak Calculator output).
- Must work fully offline during a Session (story 23) — no network calls on the critical path; sync happens after, via ticket 06's queue.

## Depends on

Ticket 02 (Rep Counting Engine), ticket 03 (Pose Tracking Engine), ticket 04 (Streak Calculator, for Session summary), ticket 06 (repository layer, to persist Sets/Sessions).

## Out of scope

Streak/Personal Best/trend detail screens (ticket 10), safety disclaimer and Guest-conversion prompt (ticket 13).
