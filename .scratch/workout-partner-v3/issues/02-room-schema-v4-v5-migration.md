# 02: Room schema v4 → v5 migration

**What to build:** A `MIGRATION_4_5`, following the existing `MIGRATION_2_3`/`MIGRATION_3_4` pattern in `Migrations.kt`, that lands every schema change the v3 feature set needs. No app behavior changes in this ticket — it's a pure schema foundation, verified by migration tests only. Downstream tickets build the behavior that uses these columns.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `ActivityLevel` stored values are remapped in both `accounts` and `guest_profile`: `LOW`→`SEDENTARY`, `MEDIUM`→`LIGHTLY_ACTIVE`, `HIGH`→`ACTIVE`. (The Kotlin enum itself — `SEDENTARY`/`LIGHTLY_ACTIVE`/`ACTIVE`/`VERY_ACTIVE` — is part of this ticket too, since the migration needs it to exist; the UI that lets someone pick `VERY_ACTIVE` is ticket 07.)
- [x] `tracked_profiles.accountId` becomes nullable (null = owned by the Guest), mirroring the existing nullable `sessions.accountId`.
- [x] `guest_profile` (still a single-row table) gains `weeklyTarget`, `currentStreak`, `bankedShields`, `notificationsEnabled` with the same defaults a new Account gets; its existing body-stats columns become nullable. No placeholder "local guest" Account row is introduced.
- [x] Sets and Tallies gain a nullable `exerciseVariant` column (null = the Exercise's standard form). Existing rows migrate with `exerciseVariant = null`.
- [x] `MigrationTest` covers: Activity Level value mapping in both tables, nullable `tracked_profiles.accountId` with existing rows preserved, new `guest_profile` columns with correct defaults, new `exerciseVariant` columns null on existing rows.
- [x] `docs/firestore-schema.md` and `firestore.rules` are updated to accept the new `exerciseVariant` field (schema doc only in this ticket — Firestore sync behavior for it isn't exercised until Step Jack Sets actually get created in ticket 08).
- [x] `docs/auth-roadmap.md`'s ERD table is extended to cover this migration's changes.

## Comments

Implemented as spec'd, plus a real bug caught and fixed in review: `tallies.trackedProfileId` carries `ON DELETE CASCADE` against `tracked_profiles`, and SQLite implicitly cascade-deletes referencing rows when the *referenced* table is dropped, under foreign-key enforcement — which Room enables by default (confirmed empirically for this project's Room version: `PRAGMA foreign_keys` reads `1` on a freshly-opened Room database). The first version of `MIGRATION_4_5` rebuilt `tracked_profiles` with the obvious create-new/drop-old/rename-new sequence and silently wiped every Tally in testing; a rename-old-table-out-of-the-way-first variant tried next lost the Tally too, because SQLite auto-rewrites *other* tables' `REFERENCES` clauses to follow a renamed table when FK enforcement is on, so the danger just followed the rename to its new name. `PRAGMA foreign_keys=OFF` isn't usable as a workaround either — Android's `SQLiteOpenHelper` already has a transaction open by the time a `Migration.migrate()` call runs, and that pragma is a documented no-op mid-transaction.

The fix that actually survives (empirically verified, and now covered by a permanent regression test — `migrating 4 to 5 rebuilds tracked_profiles without cascade-deleting tallies` in `MigrationTest.kt`, which also proves the FK is genuinely restored afterward by cascading a manual delete): strip `tallies`' FK constraint first (recreate it with no `REFERENCES` clause, so nothing points at `tracked_profiles`), rebuild `tracked_profiles` freely, then recreate `tallies` a second time to restore its FK against the final table — picking up its own new `exerciseVariant` column in that same pass rather than a third rebuild. `guest_profile` has no incoming FK, so its recreate stayed the simple create-new/drop-old/rename-new shape throughout.

`MigrationTest`'s `openV4Database` fixture now includes the real FK clauses on `tracked_profiles`/`tallies` (the original fixture omitted them, which is why the bug wasn't caught immediately) and enables `PRAGMA foreign_keys = ON` to match Room's real default — without that, none of this would have been exercised at all.

`ProfileSetupScreen.kt`/`SettingsScreen.kt` needed minimal fixes (default value + an exhaustive `when`) to keep compiling once `ActivityLevel` went from 3 to 4 values; still the old segmented-button control, real four-card redesign is ticket 07's.
