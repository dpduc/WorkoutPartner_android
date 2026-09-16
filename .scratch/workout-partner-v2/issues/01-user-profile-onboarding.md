Status: ready-for-agent

# 01 — User profile onboarding (name, age, height, weight, activity level)

## Scope

Today `AccountEntity` (`data/src/main/kotlin/com/workoutpartner/data/AccountEntity.kt`)
holds only `id`, `weeklyTarget`, `bankedShields`, `currentStreak`,
`notificationsEnabled` — no body-stat/demographic fields exist anywhere in
the codebase, and neither `WelcomeScreen` nor `SignUpScreen`/`SignInScreen`
collect any. This ticket adds:

- Name (`String`), Age (`Int`), Height in cm (`Int`), Weight in kg
  (`Double`), and a 3-tier Activity Level (`LOW`/`MEDIUM`/`HIGH`) — new
  `ActivityLevel` enum, e.g. `data/src/main/kotlin/com/workoutpartner/data/ActivityLevel.kt`.
- These land as new columns directly on `AccountEntity` — they're
  attributes of the existing **Account** concept in `CONTEXT.md`, not a new
  noun. Do not name this concept "Profile" — that collides with the
  existing, unrelated **Tracked Profile** term (a Roster entry for someone
  else, per `CONTEXT.md`).
- A new Room entity `GuestProfileEntity` (single fixed-id row,
  `data/src/main/kotlin/com/workoutpartner/data/GuestProfileEntity.kt`)
  holding the same 5 fields, for capture before an Account exists — Guests
  answer this too. `OnboardingPrefs.kt`'s own doc comment reserves
  `SharedPreferences` for UI-flow flags, not app data, so this follows
  ticket 05's Room precedent instead, the same way Guest Sessions/Sets
  already persist locally pre-Account (ADR-0004).
- `WorkoutPartnerDatabase` (`data/src/main/kotlin/com/workoutpartner/data/WorkoutPartnerDatabase.kt`)
  bumps from version 2 to 3; new `MIGRATION_2_3` in `Migrations.kt` adds the
  `accounts` columns and creates the `guest_profile` table.
- `AccountRepository`: `updateProfile(accountId, name, age, heightCm,
  weightKg, activityLevel)`. New `GuestProfileRepository` (or folded into
  `AccountRepository`) with `saveGuestProfile(...)` / `getGuestProfile()` /
  `clearGuestProfile()`.
- Extend the existing Guest→Account migration contract
  (`GuestAccountMigration` / `AccountRepository.claimGuestData`, which
  already reassigns unowned `SessionEntity` rows per ADR-0004/ticket 08) to
  also copy `GuestProfileEntity` into the new `AccountEntity`'s profile
  columns and clear the guest row, in the same transaction.
- New `ProfileSetupScreen` (`app/src/main/kotlin/com/workoutpartner/app/onboarding/`)
  collecting the 5 fields; new `AppScreen.ProfileSetup` state.
- Flow change in `MainActivity.kt`'s `WorkoutPartnerApp`:
  `Disclaimer -> Welcome -> [Guest tap -> ProfileSetup(local) -> RoutinePicker]`
  / `[SignUp -> ProfileSetup(bound to new accountId) -> RoutinePicker]` /
  `[SignIn -> RoutinePicker directly]` (an existing Account already has a
  profile from its own prior sign-up). Add `hasCompletedProfile: Boolean` to
  `OnboardingPrefs.kt` to avoid re-showing it every launch.
- `SettingsScreen.kt`/`SettingsViewModel.kt`: replace the static "Signed in"
  Profile card with real, editable fields for all 5, reusing
  `ProfileSetupScreen`'s field composables where practical; new
  `SettingsViewModel.updateProfile(...)`.
- `CONTEXT.md`: extend the **Account** glossary entry with the new fields;
  add an **Activity Level** term (Low/Medium/High).

## Testing

- Room migration test for `MIGRATION_2_3` (new columns + new table, matching
  ticket 06's existing migration-test pattern for `MIGRATION_1_2`).
- `AccountRepositoryTest`/`GuestAccountMigrationTest` (existing suites):
  extend to cover profile-claim-on-migration alongside the existing
  Session-claim assertions.
- Manual: Guest -> ProfileSetup -> workout -> Sign up -> confirm both the
  profile and the guest Session history land on the new Account.

## Depends on

`workout-partner-v1/issues/15-local-auth-fallback.md` — needs
`authRepository.signUp` working without live Firebase to exercise the
Guest -> Account profile claim end-to-end during development.

## Out of scope

- Any BMI/difficulty math consuming these fields — that's
  `02-routine-difficulty-and-format-tags.md`.
- Quick Count's own reps/duration/form-score reporting —
  `03-quick-count-form-score-and-duration.md`.

## Comments

Implemented as spec'd, combined with ticket 03's schema change into one
`MIGRATION_2_3` (version 2 -> 3) since both landed in the same session —
`accounts` gets `name`/`age`/`heightCm`/`weightKg`/`activityLevel` (all
nullable) and a new `guest_profile` single-row table; `tallies` gets
`formScore`/`durationSeconds` (ticket 03's columns, also nullable).

`GuestProfileEntity`/`GuestProfileDao`/`ActivityLevel` are new; folded the
guest-profile repository methods into `AccountRepository`
(`saveGuestProfile`/`getGuestProfile`/`updateProfile`) rather than a
separate class, per the ticket's own "or folded into AccountRepository"
option. `AccountRepository.claimGuestData` now also copies a saved
`GuestProfileEntity` onto the newly-claimed Account and clears the guest
row, in the same transaction as the Session claim.

New `AppScreen.ProfileSetup` + `ProfileSetupScreen` sit between Guest/Sign-up
and `RoutinePicker`; Sign-in skips it (sets `hasCompletedProfile = true`
directly, since an existing Account already answered this). A Guest who
already answered it and later converts via `GuestConversionDialog` also
skips a second prompt — `claimGuestData` already carried the answer over.
`SettingsScreen`'s Profile card is now editable (backed by
`SettingsViewModel.updateProfile`), seeded from the loaded Account via a
`LaunchedEffect(state.isLoaded)` one-time seed rather than clobbering
in-progress edits on every recomposition.

Tests: `MigrationTest` (hand-built v2 schema + `MIGRATION_2_3.migrate()`
directly, via a plain `SupportSQLiteOpenHelper` — this Room/Robolectric
combination has a known `MigrationTestHelper` driver incompatibility, see
that test's doc comment), plus new cases in `AccountRepositoryTest` and
`GuestAccountMigrationTest` covering profile save/update/claim. Full
`data`+`app` unit test suites pass. `CONTEXT.md`'s Account entry and a new
Activity Level term were added — deliberately not named "Profile" to avoid
colliding with the existing Tracked Profile term.

Not done in this session: manual on-device verification of the new
onboarding flow end-to-end.
