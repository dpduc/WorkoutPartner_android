Status: ready-for-agent

# 13 — Onboarding, safety disclaimer & Guest flow UI

## Scope

Covers user stories 1, 3, 22.

- Safety disclaimer shown on first launch (app isn't a substitute for professional guidance) — must be shown before any tracking screen is reachable.
- "Continue as Guest" entry point that requires no Account.
- Post-Set prompt to create an Account while still a Guest (story 3), wired to ticket 08's migration transaction when the user follows through.
- Sign-up/sign-in screens wired to ticket 07's Auth module.

## Depends on

Ticket 07 (Auth module), ticket 08 (migration transaction).

## Out of scope

The migration transaction's logic (ticket 08), Session flow itself (ticket 09) beyond the point where the post-Set prompt is triggered.
