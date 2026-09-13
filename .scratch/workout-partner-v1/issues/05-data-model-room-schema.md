Status: ready-for-agent

# 05 — Data model & Room schema

## Scope

Room entities and DAOs for the conceptual data model in `spec.md`'s Implementation Decisions:

- **Account**: identity, Weekly Target, banked Streak Shield count, current Streak, notification preference.
- **Guest local record**: same shape as an Account's local data, unowned until migration assigns it an Account id (see ADR-0004 / ticket 08).
- **Routine**: name, ordered list of (Exercise, target reps, rest interval) — bundled/seeded data per `CONTEXT.md`, not user-editable in v1.
- **Session**: Account id, Routine id, timestamp, its Sets.
- **Set**: Session id, Exercise, target reps, actual reps, Form Score, Good Set flag, timestamp.
- **Roster / Tracked Profile**: owning Account id, display name.
- **Tally**: Tracked Profile id, Exercise, reps achieved, optional target, timestamp.

Offline-first per ADR-0002: this schema is the local source of truth; sync queue is built on top of it in ticket 06.

## Depends on

Ticket 01 (project scaffold, for the Room dependency).

## Out of scope

Sync queue / Firestore mirroring (ticket 06), repository classes themselves (ticket 06) — this ticket is schema/DAO only.
