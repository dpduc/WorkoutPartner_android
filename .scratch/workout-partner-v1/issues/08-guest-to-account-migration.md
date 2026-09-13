Status: ready-for-agent

# 08 — Guest → Account migration

## Scope

Per ADR-0004 and spec stories 2, 3, 4:

- Guest's Sets and Tallies persist locally (unowned Guest local record, per `CONTEXT.md`).
- On sign-up, migrate local records to the new Account id in **one migration transaction**, not a background job — spec is explicit the user shouldn't see a window where migration is "in progress."
- Prompt to create an Account after finishing a Set while a Guest (story 3) — this ticket owns the migration logic the prompt leads to; the prompt UI itself belongs with onboarding (ticket 13).

## Testing

Per spec's Testing Decisions (Seam 3): the Guest → Account migration transaction is tested against an in-memory Room database and a fake remote, alongside ticket 06's sync tests.

## Depends on

Ticket 06 (repository layer — this is effectively an extension of it), ticket 07 (auth module, for the sign-up trigger point).

## Out of scope

The "create an Account?" prompt UI (ticket 13).
