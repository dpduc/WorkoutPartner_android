Status: ready-for-agent

# 07 — Auth module

## Scope

Firebase Auth wrapper. Covers user stories 1, 5, 6, 7.

- Guest session state: using the app without an Account (per `CONTEXT.md`'s Guest definition) — no Firebase Auth identity involved while a Guest, purely local.
- Sign up with email/password (spec doesn't require a specific additional provider — implement email/password as the baseline; treat "or a provider" as an easy extension point, not a hard v1 requirement, since the spec doesn't name which provider).
- Sign in on a new device; synced history should become visible (depends on ticket 06's sync).
- Triggers the Guest → Account migration transaction (ticket 08) on sign-up when a local Guest record exists.

## Depends on

Ticket 06 (repository layer, for wiring sign-in to a synced Account).

## Out of scope

The migration transaction's logic itself (ticket 08), UI screens (ticket 13 for the onboarding/sign-up flow).
