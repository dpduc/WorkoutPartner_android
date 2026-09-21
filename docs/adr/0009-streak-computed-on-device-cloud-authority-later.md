# Streak is computed on-device now; Cloud Functions become its authority after pull-sync

`docs/data-architecture-roadmap.md` proposes that Cloud Functions, not the client, be the authority for Streak and Streak Shields. [[0005-weekly-target-streak|ADR-0005]] and the v1 spec compute them on the client with `StreakCalculator`. We keep the client calculation as the only implementation for now and adopt the Cloud Function as the **later** authority for Accounts, once the app can pull data back down. A function with no pull path gives Android nothing to reconcile against: Streak would be recomputed in the cloud and never reach the phone, and a second phone would still have nothing to converge on.

Once pull-sync ships, an `onDocumentCreated("sets/{setId}")` function recomputes `currentStreak` and `bankedShields` on the owning `accounts` document. Android stays optimistic after each Set, then adopts the pulled values when a sync completes, so drift between devices is corrected instead of accumulating.

## Consequences

- **The Streak rules live in two languages.** [[0005-weekly-target-streak|ADR-0005]] remains the definition; `StreakCalculator`'s test cases must be exported as shared fixtures that both the Kotlin and the TypeScript implementations pass. Without that, the two will drift and the "authority" will be wrong in a new way.
- **`StreakCalculator` never goes away.** Guests have no cloud ([[0007-guest-feature-parity-stays-local]]) and an Account must still update instantly offline ([[0002-offline-first-sync]]), so the client calculation stays permanently; the function only overrides it for Accounts.
- **Cloud Functions need a paid Firebase plan** and a `functions/` project that does not exist yet. That is a cost decision the owner makes when this is scheduled.
- **Until then, Web shows only what Android last pushed.** The Streak fields in `accounts` are client-writable in the interim and become rule-locked the day the function ships (see [[0010-android-owner-writes-with-field-limits]]).
- Merging Guest data into an existing Account keeps that Account's Weekly Target and recomputes Streak over the merged history ([[0007-guest-feature-parity-stays-local]]); the function must use the same rule.
