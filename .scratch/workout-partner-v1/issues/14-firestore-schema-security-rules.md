Status: ready-for-agent

# 14 — Firestore schema & security rules

## Scope

Covers user stories 42, 44, 45 and formalizes ADR-0001 and ADR-0006 as an enforced contract, not just documentation.

- Firestore collections mirroring the Room schema (ticket 05) — this is the de facto contract the separate Web project depends on (per spec's Further Notes), so field names/shapes here should match `CONTEXT.md` terminology exactly (Account, Session, Set, Roster/Tracked Profile, Tally).
- Security rules: Android (the authenticated Account) is the only writer of its own Set/Tally/pose-derived data; the Web project only reads (ADR-0006) — rules should reject any write attempt from outside the owning Account's Android client, not just rely on client-side convention.
- Nothing here changes local behavior for Guests — Guests have no Firestore presence until migration (ticket 08).

## Depends on

Ticket 05 (schema), ticket 06 (repository layer, which is what actually calls Firestore).

## Out of scope

The Web dashboard itself — separate codebase, out of scope per spec. This ticket only owns the backend contract it reads from.

## Comments

Implemented: `firestore.rules` (repo root) and `docs/firestore-schema.md`
— the Firestore schema/rules contract for all five of ticket 05's Room
entities that carry user data (Account, Session, Set, Roster/Tracked
Profile, Tally; Routine/RoutineStep deliberately excluded — bundled app
content, not user data). Field names match `CONTEXT.md`/Room exactly,
spot-checked against `SetEntity.kt`/`TallyEntity.kt`/`AccountEntity.kt`/
`SessionEntity.kt`/`TrackedProfileEntity.kt`.

**A real gap this ticket had to close in `data` (ticket 06), not just
document:** writing an enforceable "only the owning Account can read/write
this" rule requires the document to actually carry an `accountId` field to
check — but neither `SetEntity` nor `TallyEntity` (ticket 05's schema) has
one; ownership is only reachable via a `Session`/`TrackedProfile` join Room
supports but Firestore rules can't express for this access pattern. Fixed
by stamping `accountId` onto the pushed document at sync time
(`FirestoreMappers.toFirestoreMap(accountId)`), resolved by `SyncEngine` via
`SessionDao`/`TrackedProfileDao` lookups — `RemoteSyncGateway.pushSet`/
`pushTally` now take the resolved `accountId` as a parameter.

That resolution step also surfaced and fixed a real behavioral gap against
this ticket's own "Guests have no Firestore presence until migration" line:
previously, `SyncEngine` would have happily pushed a Guest's Set to
Firestore with no ownership information at all the moment it came online,
because nothing checked ownership before pushing. Now a Set is only ever
pushed once its owning Session resolves a real `accountId` — a third
outcome (`not yet owned`) alongside "pushed" and "network failure," left
queued rather than treated as either. Once migration (ticket 08) re-points
the Session, the same already-queued entry resolves and pushes on the next
attempt — no separate re-sync step needed. (Tallies never have this
concern: `TrackedProfileEntity.accountId` is non-null by construction,
ticket 05.)

**Security rules are honest about what they can and can't enforce.** This
ticket's own wording asks for rules that "reject any write attempt from
outside the owning Account's Android client." Firestore rules see an
authenticated uid and the request data — nothing about which client
platform sent it — so without a custom Auth claim minted server-side (no
Cloud Functions/Admin SDK infrastructure exists anywhere in this project),
distinguishing "Android" from "Web, signed in as the same Account" isn't
enforceable. What the rules do enforce: only the authenticated owner can
read their own data at all; Sets/Tallies (`create`-only, field-validated)
can never be edited or deleted by anyone once written; and — this was a
review catch — Accounts/Sessions/Tracked Profiles stay **read-only for
everyone** rather than granting the owning Account `write` access, since
that would just as easily let a Web session signed in as that Account
rewrite its own Account state, exactly what ADR-0006 rules out. All of this
is disclosed directly in `firestore.rules`' own comment block rather than
overclaiming platform-level enforcement that doesn't exist.

Not verified against a live Firestore project/emulator this session — no
Firebase project or `firebase.json` exists yet (ticket 01/07's disclosed
`google-services.json` gap, still open). Written for real, unverified
end-to-end, the same disclosure as ticket 03's CameraPoseTracker / ticket
06's FirestoreSyncGateway.

`SyncEngineTest`/`GuestAccountMigrationTest` (ticket 06) updated for the new
`SyncEngine` constructor shape and ownership-gated behavior, plus a new
test confirming a Guest's Set stays queued until claimed, then syncs on the
very next attempt post-migration. 25 tests passing in `data` (was 23), 29
unchanged in `app`. Full project build/tests green.

Reviewed via `/code-review` against this ticket (Spec: no gaps found — the
accountId-stamping and Guest-sync-gating fixes above were confirmed as
correctly scoped extensions of ticket 06, not unrelated rework or scope
creep) and the repo's ADRs/CONTEXT.md/spec.md (Standards: one hard
violation found and fixed — the original `accounts`/`sessions`/
`trackedProfiles` rules granted `write` to the authenticated owner, which
(per the platform-indistinguishability limitation above) meant a Web
session could tamper with Account state, contradicting ADR-0006; fixed by
making those three collections read-only for everyone until a real write
path and enforcement answer exist).

This closes out the ticket list for `workout-partner-v1`'s v1 scope.
