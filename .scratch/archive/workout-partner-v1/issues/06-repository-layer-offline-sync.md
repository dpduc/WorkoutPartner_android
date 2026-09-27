Status: done

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

## Comments

Implemented in `data`: `AccountRepository`, `SetRepository` (also owns
Session lifecycle — the module list doesn't name a separate
SessionRepository, and a Session has no meaning apart from its Sets),
`TallyRepository`, `RosterRepository`. `SetRepository.recordSet`/
`TallyRepository.recordTally` write to Room and enqueue a `PendingSyncEntity`
(the offline outbox) in one transaction; `SyncEngine.syncPendingChanges`
replays the queue against a `RemoteSyncGateway` (real: `FirestoreSyncGateway`;
fake: test-only `FakeRemoteSyncGateway`), stopping at the first failure and
leaving the rest queued. `AccountRepository.recomputeStreak` keeps the
cached Streak columns (ticket 05) in sync via `core-streaks.StreakCalculator`;
`claimGuestData` is the atomic re-ownership primitive ticket 08's migration
needs (re-points unowned Sessions, then recomputes the Streak from the now-
complete history, in one transaction) — not the migration itself.

Two-device reconciliation (spec story 43): Sets/Tallies use client-generated
UUID ids (ticket 05) written by id (never `.add()`), so two devices' offline
writes become two different Firestore documents once both sync — additive
by construction, no merge logic needed. Flagged, not resolved (per this
ticket's own instruction): `AccountEntity`'s `weeklyTarget`/
`notificationsEnabled` are user preferences with no principled auto-merge if
two offline devices set them differently; moot for now since Account
Firestore sync isn't built here at all.

Known, honestly-disclosed gaps, not fixed by this change:
- Firestore mirroring is Set/Tally only. Account/Session/Roster stay
  Room-only — a real gap against ADR-0001's "Firestore collections mirror
  the above," left for a later ticket.
- **No pull-sync exists.** `SyncEngine` only pushes; nothing downloads
  another device's already-synced Sets/Tallies back into this device's Room.
  So `recomputeStreak`/`getSetsForAccount` are only ever accurate against
  *this* device's own local history, not the true cross-device union —
  spec story 43's "reconcile sensibly" isn't fully closed by this ticket.
  Building the pull path needs Session/Account Firestore mirroring first
  (a Set carries no `accountId` to query by), which is a bigger lift than
  this ticket's "Sets and Tallies" push-only scope — left for a later
  ticket rather than rushed. (Caught by `/code-review`'s Spec pass —
  `SyncEngine`'s doc comment originally implied this already worked;
  corrected to state the gap plainly.)
- `CameraPoseTracker`-style disclosure: `FirestoreSyncGateway` is real
  production code but unverified against a live Firebase project this
  session (still no `google-services.json`, ticket 01's original gap).
  Exercised in tests only via the fake gateway, per spec's Testing
  Decisions.

Added the Robolectric test dependency (`testOptions.unitTests.
isIncludeAndroidResources = true`) so Room's in-memory database could
actually run as a plain JVM unit test in this session, rather than being
written-but-unverified like tickets 01/03's instrumented-only gaps. Bumped
the Room schema to v2 (`pending_sync` table) with an explicit `Migration(1,
2)`.

13 tests passing (`./gradlew :data:test`): `SyncEngineTest` (queued items
sync once online; stay queued while offline and drain once reconnected;
two devices' Sets reconcile additively against a shared fake remote),
`SetRepositoryTest`/`TallyRepositoryTest`/`AccountRepositoryTest` (write +
enqueue, Streak recompute on a signed-in Session vs. no-op on a Guest's,
`claimGuestData` re-owning history and recomputing from it), and a light
`RosterRepositoryTest`. Full project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one real
documentation-accuracy issue — `SyncEngine`'s doc comment overclaimed that
Streak reconciliation already worked "once synced" when no pull path exists
to make that true; corrected, see the gap above) and the repo's
ADRs/CONTEXT.md/spec.md (Standards: no hard violations; unified
`RosterRepository`'s constructor to the same `WorkoutPartnerDatabase`-first
shape as its siblings, deduplicated the repeated `UUID.randomUUID()`
id-generator default into one `newEntityId()`, and documented the
same-database-instance invariant `SetRepository`'s atomicity with
`AccountRepository` depends on).

Tickets 07 (auth) and 08 (Guest -> Account migration) depend on this and are
now unblocked on the repository/sync piece.
