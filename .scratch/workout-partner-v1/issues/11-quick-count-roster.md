Status: ready-for-agent

# 11 — Quick Count / Roster

## Scope

Covers user stories 34-41. Per `CONTEXT.md`'s Quick Count/Tally/Tracked Profile/Roster definitions and ADR-0003.

- Roster CRUD: create/list Tracked Profiles (name only, no login/Account of their own; not visible to the person represented).
- Quick Count run: pick a Tracked Profile + an Exercise, optional target count, run the camera.
- Reuses the Rep Counting Engine (ticket 02) but skips Form Score gating entirely — Quick Count Tallies are raw counts only (no Form Score field populated).
- Auto-stop when the optional target is reached; manual stop always available.
- Enforce single-person-in-frame per ADR-0003 — if the Pose Tracking Engine (ticket 03) surfaces multiple candidate people, Quick Count should refuse to count rather than guess which is the Tracked Profile.
- Saves each run as a Tally against the Tracked Profile (ticket 05/06 for persistence).
- Quick Count activity must NOT count toward the Account's own Streak/Active Days (story 41) — make sure ticket 04/06's Active Day derivation only looks at Sets, never Tallies.

## Depends on

Ticket 02 (Rep Counting Engine), ticket 03 (Pose Tracking Engine, for the single-person-in-frame signal), ticket 06 (repository layer, for Tally/Roster persistence).

## Out of scope

Any Tracked Profile self-visibility or "claim your history" flow — explicitly out of scope per spec.
