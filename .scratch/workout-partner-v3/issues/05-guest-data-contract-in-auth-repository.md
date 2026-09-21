# 05: Guest-data contract in AuthRepository

**What to build:** `AuthRepository` becomes the single seam for every Guest-data outcome on sign-up and sign-in. Sign-up claims a widened set of Guest data (including Quick-Count-only Guests who never started a Session). Sign-in (email or Google) no longer auto-claims — it returns whether Guest data is pending, with counts, and a separate operation resolves it via Merge, Discard, or Cancel. This ticket is data-layer only, verified by repository tests; the merge/discard prompt UI is ticket 09.

**Blocked by:** 01 (auth cleanup lands first so this doesn't fight phone-auth code being deleted at the same time), 02 (schema).

**Status:** done

- [x] `hasUnclaimedGuestData` counts any unowned Session, any unowned Tracked Profile, or a Guest record carrying non-default state — not Sessions only.
- [x] **Sign-up** (email) claims: unowned Sessions, unowned Tracked Profiles (and their Tallies), Guest body-stats, Guest Weekly Target; recomputes Streak/Shields from the full Set history; clears the Guest record. Runs in a single Room transaction; a failure leaves Guest data unclaimed and intact.
- [x] **Sign-in** (email or Google) returns a result that is either *signed in, nothing pending* or *signed in, Guest data pending* (with counts of Sessions, Tracked Profiles, Tallies). It never claims on its own — Google sign-in's previous auto-claim behavior is removed.
- [x] A new operation resolves pending Guest data with **Merge** or **Discard**:
  - Merge moves the same rows sign-up claims, but keeps the Account's existing Weekly Target and existing body-stats (Guest body-stats fill only if the Account has none); Streak/Shields recomputed across the merged history.
  - Discard deletes unowned Sessions (and their Sets), unowned Tracked Profiles (and their Tallies), and the Guest record, in one transaction, including their pending sync-queue entries.
- [x] **Cancel** signs the Athlete back out immediately (sign-in already completed at the gateway before the prompt), leaving Guest data untouched and unowned.
- [x] Claim, merge and discard each run in a single Room transaction; a failure leaves Guest data unclaimed and intact, never partially moved.
- [x] Guest Sets/Tallies continue to be enqueued for sync at write time regardless of ownership; the sync engine skips/holds ownerless rows until they're owned (Firestore rules require an owner).
- [x] `AuthRepositoryTest`/`GuestAccountMigrationTest` cover: sign-up claiming the full widened set including Quick-Count-only Guest data; email and Google sign-in returning correct pending/nothing-pending results; Merge keeping the Account's Weekly Target and body-stats while filling missing body-stats and recomputing Streak; Discard removing all Guest rows and their queued syncs; Cancel signing out with Guest data left intact; a failing claim leaving data unclaimed.

## Comments

Implemented as spec'd. `AuthRepository.signUp` now calls `AccountRepository.claimGuestData` directly instead of going through the `onGuestDataToMigrate` callback hook — `GuestAccountMigration.kt` (the thin wrapper that only existed to supply that hook) is deleted, and `AppContainer` wiring simplified to match. `AuthRepository` gained a constructor-injected `Clock` (matching `TallyRepository`'s own pattern) since sign-up/sign-in have no ViewModel of their own to hold "today"/zone the way `AccountRepository`'s other callers do.

Landed right after a **concurrent session** finished ticket 06 (Guest UI parity) on the same uncommitted working tree — no worktree isolation between the two sessions, so this ticket paused mid-implementation once that was noticed, and resumed once ticket 06 committed. `AccountRepository.claimGuestData`'s doc comment (written by that session) already flagged the exact gap this ticket fixes (Weekly Target/state not carried onto a new Account), so the two tickets' designs landed compatible rather than colliding — `claimGuestData` and the new `mergeGuestData`/`discardGuestData` build directly on ticket 06's nullable-`accountId`-means-Guest convention.

Caught in review and fixed before commit:
- `claimGuestData`/`mergeGuestData` had near-identical bodies (claim Sessions+Tracked Profiles, fold the Guest row onto the Account, recompute Streak) differing only in which fields the copy keeps — extracted into shared `claimSessionsAndTrackedProfiles`/`applyGuestProfile` helpers, the latter parameterized by the one thing that actually differs (the merge strategy).
- `SignInResult`/`GuestDataResolution` were originally defined inline atop `AuthRepository.kt`; moved to their own `SignInResult.kt` file to match this codebase's established convention for small standalone auth-outcome types (`AuthState.kt`, `GuestDataSummary.kt`).
- Several `MigrationTest.kt` method names (pre-existing, from tickets 01/02, not this ticket's own code) were long enough that Robolectric's temp-directory path — which embeds the test name verbatim — exceeded Windows' 260-character `MAX_PATH`, causing an intermittent, unrelated `SQLiteCantOpenDatabaseException` that was blocking this ticket's own test runs. Shortened them; verified stable across repeated runs afterward.

Not fixed, flagged for a future pass rather than folded into this ticket: `AccountRepository`'s constructor now injects 7 DAOs (`accountDao`, `sessionDao`, `setDao`, `guestProfileDao`, `trackedProfileDao`, `tallyDao`, `pendingSyncDao`), four of which are otherwise "owned" by `SetRepository`/`TallyRepository`/`RosterRepository` — real Divergent Change exposure (a schema change to Session/Set/TrackedProfile/Tally/PendingSync is now also a reason for `AccountRepository` to change). Extracting the Guest-data transactions into a dedicated repository is the obvious fix, but would also need to migrate `updateWeeklyTarget`/`updateNotificationsEnabled`/`recomputeStreak`/`saveGuestProfile` (all `guestProfileDao`-touching, all ticket 06's) to stay coherent — too large and too likely to collide with any other concurrent work to force into this ticket.

Also not addressed (narrow, pre-existing edge case, not something this ticket's acceptance criteria asks for): `GuestDataSummary` only carries Session/Tracked-Profile/Tally counts, so a Guest who is "pending" *solely* because of non-default `GuestProfileEntity` state (e.g. a customized Weekly Target with zero Sessions/Tracked Profiles/Tallies) would see a summary reporting all-zero counts. Still correctly reported as pending (`hasUnclaimedGuestData` checks this case), just not explained by the summary in that specific scenario.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fixes above came from that review.
