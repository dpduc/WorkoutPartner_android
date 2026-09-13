# Firestore schema (ticket 14)

Formalizes [ADR-0001](adr/0001-firebase-as-shared-backend.md) ("Firestore
collections mirror the Room schema", ticket 05) as the de facto contract
the separate Web project depends on (spec.md's Further Notes). Field names
follow `CONTEXT.md` terminology exactly, matching the Room entities in
`data/src/main/kotlin/com/workoutpartner/data/` and their
`toFirestoreMap()` conversions (`FirestoreMappers.kt`) where those exist.
Security rules enforcing this shape live in [`firestore.rules`](../firestore.rules)
at the repo root.

**Currently implemented (Android writes, per `SyncEngine`/`FirestoreSyncGateway`, ticket 06/14):**

## `sets/{setId}`

One completed Set (CONTEXT.md). `{setId}` is the same client-generated UUID
as `SetEntity.id` (Room). Immutable once written — never updated or deleted.

| Field | Type | Notes |
|---|---|---|
| `id` | string | Same as the document id. |
| `accountId` | string | The owning Account's Firebase Auth uid. Not a Room column on `SetEntity` itself (ownership there is via `SessionEntity.accountId`) — stamped on at push time so security rules can check it directly. |
| `sessionId` | string | The Session this Set was performed in. |
| `exercise` | string | One of `SQUAT`, `PUSH_UP`, `SIT_UP`, `LUNGE`, `JUMPING_JACK` (`core-rep-counting.Exercise`'s names). |
| `targetReps` | int | |
| `actualReps` | int | |
| `formScore` | int | 0-100. |
| `goodSet` | bool | Both conditions per CONTEXT.md's Good Set definition. |
| `timestamp` | timestamp | |

## `tallies/{tallyId}`

One Quick Count run's Tally (CONTEXT.md). `{tallyId}` matches `TallyEntity.id`.
Immutable once written. Never carries a Form Score.

| Field | Type | Notes |
|---|---|---|
| `id` | string | |
| `accountId` | string | The Tracked Profile's owning Account — stamped on at push time, same reasoning as `sets.accountId`. |
| `trackedProfileId` | string | |
| `exercise` | string | |
| `repsAchieved` | int | |
| `target` | int or null | Optional (spec.md story 36). |
| `timestamp` | timestamp | |

**Not yet implemented — schema defined ahead of the write path, a disclosed
gap in `SyncEngine`'s own doc comment ("Account/Session/Roster documents
themselves are Room-only... a real gap against ADR-0001... left for a
later ticket"). `firestore.rules` makes all three read-only for now, for
everyone (including the owning Account) — not just because nothing writes
them yet, but because granting "the authenticated owner" write access would
just as easily let a Web session signed in as that Account rewrite its own
Account state, which ADR-0006 rules out; there's no platform-distinguishing
mechanism available to narrow that further (see `firestore.rules`' own
comment block). A later ticket needs both the real write path and an
answer to that before these can accept writes:**

## `accounts/{accountId}`

Mirrors `AccountEntity`. `{accountId}` is the Firebase Auth uid.

| Field | Type |
|---|---|
| `weeklyTarget` | int |
| `bankedShields` | int |
| `currentStreak` | int |
| `notificationsEnabled` | bool |

## `sessions/{sessionId}`

Mirrors `SessionEntity`. A Guest's Session (`accountId` null in Room) has no
Firestore presence at all until migration (ticket 08) — see "Guests" below.

| Field | Type |
|---|---|
| `accountId` | string |
| `routineId` | string |
| `timestamp` | timestamp |

## `trackedProfiles/{profileId}`

Mirrors `TrackedProfileEntity` — an Account's Roster.

| Field | Type |
|---|---|
| `accountId` | string |
| `displayName` | string |

**Deliberately not mirrored:** `routines`/`routine_steps` (Room's
`RoutineEntity`/`RoutineStepEntity`). Bundled app content, not user data —
ships with the app, per ADR-0001's "mirrors the above" concerning
Account-owned data specifically (see `RoutineEntity`'s own doc comment).

## Guests have no Firestore presence until migration

Per this ticket's own scope line: nothing about a Guest's local data
(`SessionEntity`/`SetEntity` with a null `accountId`) reaches Firestore
until the Guest -> Account migration (ticket 08) re-points it. `SyncEngine`
(ticket 14) enforces this on the Android side — a Set/Tally is only ever
pushed once its owner resolves to a real `accountId`, not queued-and-hoped
— and `firestore.rules`' `ownsAccount(...)` checks mean even a
maliciously-crafted write attempt without a valid, matching
`request.auth.uid` would be rejected regardless.

## Known gap: no pull path (cross-references `SyncEngine`'s and ticket 06's disclosed gap)

Web reading "Streaks... read-only" (spec.md story 44) needs the `accounts`/
`sessions` collections above to actually be populated by Android, which
isn't built yet (see "Not yet implemented" above) — this schema is ready
for it, but populating it is a later ticket's work, not this one's.
