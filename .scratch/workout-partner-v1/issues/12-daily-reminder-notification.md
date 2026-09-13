Status: ready-for-agent

# 12 — Streak-aware daily reminder notification

## Scope

Covers user story 33. Local notification module (WorkManager/AlarmManager per spec's module list).

- Fires only when today is not yet an Active Day AND the Account hasn't yet met its Weekly Target for the week — both conditions checked against ticket 04's Streak Calculator / ticket 06's Active Day data, not an unconditional daily ping.
- Respects the Account's notification preference (per `CONTEXT.md`'s Account fields).
- No notification for Guests without an Account, since Weekly Target/Streak state doesn't exist for them yet.

## Depends on

Ticket 04 (Streak Calculator), ticket 06 (repository layer, for Active Day data and notification preference).

## Out of scope

Any notification content/copy beyond a simple reminder; no push/FCM — this is local-only per spec's module description.
