# 05: Guest-data contract in AuthRepository

**What to build:** `AuthRepository` becomes the single seam for every Guest-data outcome on sign-up and sign-in. Sign-up claims a widened set of Guest data (including Quick-Count-only Guests who never started a Session). Sign-in (email or Google) no longer auto-claims — it returns whether Guest data is pending, with counts, and a separate operation resolves it via Merge, Discard, or Cancel. This ticket is data-layer only, verified by repository tests; the merge/discard prompt UI is ticket 09.

**Blocked by:** 01 (auth cleanup lands first so this doesn't fight phone-auth code being deleted at the same time), 02 (schema).

**Status:** ready-for-agent

- [ ] `hasUnclaimedGuestData` counts any unowned Session, any unowned Tracked Profile, or a Guest record carrying non-default state — not Sessions only.
- [ ] **Sign-up** (email) claims: unowned Sessions, unowned Tracked Profiles (and their Tallies), Guest body-stats, Guest Weekly Target; recomputes Streak/Shields from the full Set history; clears the Guest record. Runs in a single Room transaction; a failure leaves Guest data unclaimed and intact.
- [ ] **Sign-in** (email or Google) returns a result that is either *signed in, nothing pending* or *signed in, Guest data pending* (with counts of Sessions, Tracked Profiles, Tallies). It never claims on its own — Google sign-in's previous auto-claim behavior is removed.
- [ ] A new operation resolves pending Guest data with **Merge** or **Discard**:
  - Merge moves the same rows sign-up claims, but keeps the Account's existing Weekly Target and existing body-stats (Guest body-stats fill only if the Account has none); Streak/Shields recomputed across the merged history.
  - Discard deletes unowned Sessions (and their Sets), unowned Tracked Profiles (and their Tallies), and the Guest record, in one transaction, including their pending sync-queue entries.
- [ ] **Cancel** signs the Athlete back out immediately (sign-in already completed at the gateway before the prompt), leaving Guest data untouched and unowned.
- [ ] Claim, merge and discard each run in a single Room transaction; a failure leaves Guest data unclaimed and intact, never partially moved.
- [ ] Guest Sets/Tallies continue to be enqueued for sync at write time regardless of ownership; the sync engine skips/holds ownerless rows until they're owned (Firestore rules require an owner).
- [ ] `AuthRepositoryTest`/`GuestAccountMigrationTest` cover: sign-up claiming the full widened set including Quick-Count-only Guest data; email and Google sign-in returning correct pending/nothing-pending results; Merge keeping the Account's Weekly Target and body-stats while filling missing body-stats and recomputing Streak; Discard removing all Guest rows and their queued syncs; Cancel signing out with Guest data left intact; a failing claim leaving data unclaimed.
