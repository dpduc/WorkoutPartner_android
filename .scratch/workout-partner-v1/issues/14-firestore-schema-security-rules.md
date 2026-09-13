Status: ready-for-agent

# 14 — Firestore schema & security rules

## Scope

Covers user stories 42, 44, 45 and formalizes ADR-0001 and ADR-0006 as an enforced contract, not just documentation.

- Firestore collections mirroring the Room schema (ticket 05) — this is the de facto contract the separate Web project depends on (per spec's Further Notes), so field names/shapes here should match `CONTEXT.md` terminology exactly (Account, Session, Set, Roster/Tracked Profile, Tally).
- Security rules: Android (the authenticated Account) is the only writer of its own Set/Tally/pose-derived data; the Web project only reads (ADR-0006) — rules should reject any write attempt from outside the owning Account's Android client, not just rely on client-side convention.
- Nothing here changes local behavior for Guests — Guests have no Firestore presence until migration (ticket 08).

## Depends on

Ticket 05 (schema), ticket 06 (repository layer, which is what actually calls Firestore).

## Out of scope

The Web dashboard itself — separate codebase, out of scope per spec. This ticket only owns the backend contract it reads from.
