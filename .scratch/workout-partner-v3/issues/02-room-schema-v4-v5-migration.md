# 02: Room schema v4 → v5 migration

**What to build:** A `MIGRATION_4_5`, following the existing `MIGRATION_2_3`/`MIGRATION_3_4` pattern in `Migrations.kt`, that lands every schema change the v3 feature set needs. No app behavior changes in this ticket — it's a pure schema foundation, verified by migration tests only. Downstream tickets build the behavior that uses these columns.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `ActivityLevel` stored values are remapped in both `accounts` and `guest_profile`: `LOW`→`SEDENTARY`, `MEDIUM`→`LIGHTLY_ACTIVE`, `HIGH`→`ACTIVE`. (The Kotlin enum itself — `SEDENTARY`/`LIGHTLY_ACTIVE`/`ACTIVE`/`VERY_ACTIVE` — is part of this ticket too, since the migration needs it to exist; the UI that lets someone pick `VERY_ACTIVE` is ticket 07.)
- [ ] `tracked_profiles.accountId` becomes nullable (null = owned by the Guest), mirroring the existing nullable `sessions.accountId`.
- [ ] `guest_profile` (still a single-row table) gains `weeklyTarget`, `currentStreak`, `bankedShields`, `notificationsEnabled` with the same defaults a new Account gets; its existing body-stats columns become nullable. No placeholder "local guest" Account row is introduced.
- [ ] Sets and Tallies gain a nullable `exerciseVariant` column (null = the Exercise's standard form). Existing rows migrate with `exerciseVariant = null`.
- [ ] `MigrationTest` covers: Activity Level value mapping in both tables, nullable `tracked_profiles.accountId` with existing rows preserved, new `guest_profile` columns with correct defaults, new `exerciseVariant` columns null on existing rows.
- [ ] `docs/firestore-schema.md` and `firestore.rules` are updated to accept the new `exerciseVariant` field (schema doc only in this ticket — Firestore sync behavior for it isn't exercised until Step Jack Sets actually get created in ticket 08).
- [ ] `docs/auth-roadmap.md`'s ERD table is extended to cover this migration's changes.
