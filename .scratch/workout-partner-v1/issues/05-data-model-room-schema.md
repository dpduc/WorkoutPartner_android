Status: done

# 05 — Data model & Room schema

## Scope

Room entities and DAOs for the conceptual data model in `spec.md`'s Implementation Decisions:

- **Account**: identity, Weekly Target, banked Streak Shield count, current Streak, notification preference.
- **Guest local record**: same shape as an Account's local data, unowned until migration assigns it an Account id (see ADR-0004 / ticket 08).
- **Routine**: name, ordered list of (Exercise, target reps, rest interval) — bundled/seeded data per `CONTEXT.md`, not user-editable in v1.
- **Session**: Account id, Routine id, timestamp, its Sets.
- **Set**: Session id, Exercise, target reps, actual reps, Form Score, Good Set flag, timestamp.
- **Roster / Tracked Profile**: owning Account id, display name.
- **Tally**: Tracked Profile id, Exercise, reps achieved, optional target, timestamp.

Offline-first per ADR-0002: this schema is the local source of truth; sync queue is built on top of it in ticket 06.

## Depends on

Ticket 01 (project scaffold, for the Room dependency).

## Out of scope

Sync queue / Firestore mirroring (ticket 06), repository classes themselves (ticket 06) — this ticket is schema/DAO only.

## Comments

Implemented in `data`: seven Room entities (`AccountEntity`, `RoutineEntity`
+ `RoutineStepEntity`, `SessionEntity`, `SetEntity`, `TrackedProfileEntity`,
`TallyEntity`) covering the full conceptual data model, with a DAO per
entity (`RoutineDao` assembles `RoutineWithSteps` from two explicitly-
ordered queries rather than a Room `@Relation`, to avoid relying on its
unspecified row order) and a `WorkoutPartnerDatabase` schema class. Added
the KSP plugin + `room-compiler` (first needed here) and a dependency on
`core-rep-counting` (reusing its `Exercise` enum across
Set/RoutineStep/Tally, same shared-vocabulary pattern as ticket 03) and
`core-streaks` (`AccountEntity`'s default Weekly Target).

"Guest local record: same shape as an Account's local data, unowned until
migration assigns it an Account id" is implemented as `SessionEntity.
accountId` being nullable, not a parallel Guest-shaped entity — Streak/
Weekly Target/Shields are Account-holder-only concepts (spec.md stories
26-31), so a Guest has nothing to store there yet; once ticket 08's
migration re-points their Sessions/Sets at a new Account row, ticket 06 can
replay their *full* history (Guest period included, since it's the same
tables) through `StreakCalculator` to seed that row's initial Streak state.
Reasoning spelled out in `AccountEntity.kt`'s doc comment.

Known, flagged spec tension, not resolved here: CONTEXT.md's Guest
definition and spec.md user story 2 both describe a Guest's "Sets and
Tallies" persisting locally, which would require Guests to have Tracked
Profiles — in apparent conflict with user stories 34-41, which restrict
Quick Count/Roster to Account holders. This schema follows the ticket's own
literal scope list and stories 34-41 (`TrackedProfileEntity.accountId` is
non-null), documented in that file rather than silently picked either way;
if a later ticket resolves it the other way, that column becomes nullable
the same way `SessionEntity.accountId` already is.

No dedicated test file: this ticket has no "Testing" section of its own
(unlike tickets 02/04), and spec.md's Testing Decisions explicitly scope
"tests against an in-memory Room database" to the Repository/sync layer
(Seam 3, ticket 06) — this ticket's verification is Room/KSP's own schema
validation (entities, foreign keys, indices, converters) succeeding at
compile time, plus the full project build. Both green
(`./gradlew :data:compileDebugKotlin`, `./gradlew clean assembleDebug
testDebugUnitTest test`). Schema exported to `data/schemas/` for future
migrations.

Reviewed via `/code-review` against this ticket (Spec: raised the Guest
local-record question above — judged as already correctly handled per the
"Account-holder-only Streak" reading, reasoning now made explicit rather
than left implicit; the Guest-Tally tension was already flagged, just
reinforced) and the repo's ADRs/CONTEXT.md/spec.md (Standards: no hard
violations; added the missing `onDelete`-choice disclosure on the
Session/TrackedProfile -> Account foreign keys, matching this diff's own
practice for its CASCADE relationships).

Ticket 06 (repository layer) depends on this and is now unblocked.
