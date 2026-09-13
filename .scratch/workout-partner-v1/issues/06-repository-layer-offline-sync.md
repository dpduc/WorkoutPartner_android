Status: ready-for-agent

# 06 — Repository layer & offline sync (Seam 3)

## Scope

`AccountRepository`, `SetRepository`, `TallyRepository`, `RosterRepository` — Room-backed per ADR-0002, with a background sync queue pushing to Firestore when online per ADR-0001. Covers user stories 42, 43, 45.

- Offline write → local queue → sync-on-reconnect for Sets and Tallies.
- Two-device reconciliation: define and document the merge rule for data logged offline on two devices before either synced (spec story 43 asks that nothing is silently lost — last-write-wins is likely too lossy given Sets/Tallies are append-mostly; prefer additive reconciliation and flag any genuine conflict case you find for human review rather than silently picking a side).
- Firestore collections mirror the Room schema (ticket 05) per ADR-0001. Android is the only writer of Set/Tally/pose-derived data; the Web project only reads (ADR-0006) — repository layer should not expose any write path a Web-only reader would need.

## Testing

Per spec's Testing Decisions: tests against an in-memory Room database and a fake remote, covering offline write → queue → sync-on-reconnect, and (jointly with ticket 08) the Guest → Account migration transaction.

## Depends on

Ticket 05 (schema).

## Out of scope

Auth (ticket 07), the migration transaction's business rules (ticket 08 — this ticket just needs to expose whatever primitive the migration needs, e.g. an atomic re-owner-ship operation), Firestore security rules (ticket 14).
