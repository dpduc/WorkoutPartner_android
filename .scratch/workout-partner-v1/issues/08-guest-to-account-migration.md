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

## Comments

Implemented in `data`: `GuestAccountMigration`, the concrete function wired
into ticket 07's `AuthRepository.onGuestDataToMigrate` hook (which
defaulted to a no-op) — production usage looks like
`AuthRepository(gateway, accountRepository, onGuestDataToMigrate =
GuestAccountMigration(accountRepository)::invoke)`. Everything the
transaction itself needs turned out to already exist once ticket 06's
`AccountRepository.claimGuestData` was in place: it re-points the Guest's
Sessions at the new Account and refreshes the cached Streak from the
now-complete history, inside one Room transaction — satisfying "one
migration transaction, not a background job" (ADR-0004) together with
`AuthRepository.signUp` awaiting this call directly rather than launching
it. The "uploads" half of ADR-0004 already happens for free too, since
`SetRepository.recordSet` enqueues every Set for Firestore sync regardless
of ownership at write time (ticket 06).

**Tallies are not migrated — re-flagged, not silently smoothed over.**
`TrackedProfileEntity.accountId` is non-null (Quick Count/Roster is
Account-holder-only, spec.md stories 34-41), so there is no "unowned Guest
Tally" state to claim; a Guest cannot have created one under the schema as
it stands. This is the same CONTEXT.md/story-2-vs-schema tension ticket 05
already flagged. `/code-review`'s Spec pass caught an early draft's doc
comment conflating Set/Tally sync-queue "upload" with actual migration —
corrected to state plainly that Tallies aren't touched, and what a later
ticket would need to add (a `trackedProfileDao`-based claim step) if that
tension is ever resolved the other way.

4 tests passing (`./gradlew :data:test`, 23 total in the module), per
spec's Testing Decisions ("tested against an in-memory Room database and a
fake remote, alongside ticket 06's sync tests"): migration leaves no
unclaimed data behind and correctly updates the Streak; the Guest's
pre-signup Sets still sync to a shared `FakeRemoteSyncGateway` after
re-ownership; no-guest-data sign-up performs no migration; and — added
after `/code-review`'s Spec pass pointed out the first draft's "no
in-progress window" test only checked post-conditions, which wouldn't have
distinguished "properly awaited" from "backgrounded but happened to finish
before `runTest`'s scheduler let the assertions run" — a `CompletableDeferred`-
gated test proving `signUp` genuinely suspends until migration completes,
with no partial re-ownership observable while it's still pending. Full
project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: two real issues
found and fixed — the Tallies conflation above, and the unproven
"no in-progress window" test, now demonstrated with a synchronization gate
rather than merely asserted) and the repo's ADRs/CONTEXT.md/spec.md
(Standards: no hard violations; one judgement call left as-is —
`GuestAccountMigration` being a class rather than a plain function is
defensible but not strictly required, per the reviewer's own conclusion).

Ticket 13 (onboarding/sign-up UI, for the "create an Account?" prompt) can
now wire into a real migration function instead of a stub.
