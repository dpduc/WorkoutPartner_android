# Authentication Roadmap & Architecture Strategy

This document defines the roadmap, technical strategy, and implementation steps for authentication in **Workout Partner**. It bridges the gap between **fully offline local development (SQLite/Room)** and **cloud synchronization (Firebase Auth & Firestore)**, adhering to [ADR-0001](adr/0001-firebase-as-shared-backend.md), [ADR-0002](adr/0002-offline-first-sync.md), and [ADR-0004](adr/0004-guest-to-account-migration.md).

> **Status (2026-09-16):** Phases 1–4 below are all implemented and shipped
> (Phase 1 via ticket 15, Phases 2–4 via tickets 06–08 and 14 —
> `.scratch/workout-partner-v1/issues/`) — kept here as living architecture
> reference, not open work. `container.authRepository` is never `null`
> anymore: `AppContainer.authGateway` falls back to `LocalAuthGateway`
> whenever constructing `FirebaseAuthGateway` fails (no
> `google-services.json`/plugin applied), so Sign Up/Sign In always render.
> The `com.google.gms.google-services` plugin is now applied in
> `app/build.gradle.kts` (`workout-partner-v3` ticket 01), and Google
> sign-in exists alongside email; phone/SMS sign-in was removed. The only
> remaining gap is Phase 3.1's last item — enabling the Email/Password (and
> Google) providers in a real Firebase console — which is the app owner's own
> manual step (needs their own Firebase project credentials), not something
> an agent can do unattended.
>
> **Also updated since:** sign-in never claims Guest data on its own any more —
> it reports pending data for the Athlete to merge or discard
> ([ADR-0007](adr/0007-guest-feature-parity-stays-local.md)). Where this
> document's Phase 2 and its test list describe the older single-path claim
> hook, the code and `workout-partner-v3` tickets 05/09 are the source of truth.

---

## 🎯 Strategic Vision: The Dual-Mode Auth Model

Currently, the app relies on Firebase Auth (`FirebaseAuthGateway`), but when `google-services.json` is missing or unconfigured, `container.authRepository` remains `null`. This causes `AuthUnavailableScreen` to display and locks out all account-dependent features (Rosters, Quick Count, Streak Shields, Weekly Targets, Settings).

To solve this, the auth system uses a **Dual-Mode Provider** architecture behind the existing `AuthGateway` interface:

```text
                        ┌────────────────────────┐
                        │     AuthRepository     │
                        └───────────┬────────────┘
                                    │
                         uses AuthGateway (interface)
                                    │
            ┌───────────────────────┴───────────────────────┐
            ▼                                               ▼
┌────────────────────────┐                     ┌────────────────────────┐
│    LocalAuthGateway    │                     │   FirebaseAuthGateway  │
│  (Offline / SQLite)    │                     │   (Cloud / Firestore)  │
└────────────────────────┘                     └────────────────────────┘
 • No google-services.json                      • Active when Firebase is
   required                                       provisioned
 • Fast local development &                     • Multi-device sync &
   testing                                        cloud backup
 • Persists to Room `accounts`                  • Backed by Firebase Auth SDK
   + Local Preferences
```

---

## 🗺️ Roadmap & Phase Breakdown

```mermaid
timeline
    title Authentication Implementation Milestones
    section Phase 1 (✅ Shipped, ticket 15) : Local SQLite Fallback
        Design LocalAuthGateway : Instant unblock for development
        Fallback wired into AppContainer : Never leave authRepository null
        Local Account Creation : Enable Sign Up & Sign In locally
    section Phase 2 (✅ Shipped, ticket 08) : Guest Migration
        Claim unowned Sessions & Sets : Migrate accountId == null
        Retroactive Streak calculation : Replay history through StreakCalculator
    section Phase 3 (◐ Partially shipped, ticket 14) : Firebase Provisioning
        Dev google-services.json in place : Plugin applied in app/build.gradle.kts
        Enable FirebaseAuthGateway : Needs Email/Password + Google providers enabled in the console
        OAuth Extension Hooks : Google Sign-In / Credential Manager (shipped)
    section Phase 4 (✅ Shipped, ticket 06) : Sync & Reconnection
        Flush PendingSync outbox : Push local sets to Firestore
        Conflict & Re-authentication : Handle token refresh & offline reconnects
```

---

### Phase 1: Local SQLite Fallback (Immediate Unblocker) — ✅ Shipped (ticket 15)

**Goal:** Allow developers and testers to create accounts, sign in, switch profiles, and test all features without needing Firebase credentials.

#### 1.1 Implement `LocalAuthGateway`
* **Location:** `data/src/main/kotlin/com/workoutpartner/data/LocalAuthGateway.kt`
* **Interface:** Implements `AuthGateway`.
* **Mechanism:**
  * Uses `SharedPreferences` or `EncryptedSharedPreferences` for local credential storage (`email -> passwordHash` and `currentUserId`).
  * Emits `currentUserId: Flow<String?>` as a `MutableStateFlow`.
  * `signUpWithEmail(email, password)`:
    * Validates email format and minimum password length (>= 6 chars).
    * Generates a deterministic or UUID string (`local_usr_<uuid>`).
    * Stores user record locally and updates `currentUserId`.
  * `signInWithEmail(email, password)`:
    * Verifies stored password hash.
    * Updates `currentUserId`.
  * `signOut()`:
    * Sets `currentUserId.value = null`.

#### 1.2 Graceful Fallback in `AppContainer`
* **Location:** `app/src/main/kotlin/com/workoutpartner/app/di/AppContainer.kt`
* **Important:** don't wrap `authGateway` itself in `runCatching`/`getOrElse`. `AppContainer`'s current doc comment explains that nullability was deliberately moved *one level up*, to `authRepository`, after a `/code-review` flagged per-gateway fallback as error-prone — every consumer would have had to remember to re-wrap it. `authGateway` today is a plain `by lazy { FirebaseAuthGateway(FirebaseAuth.getInstance()) }`, and the `runCatching` lives around the `AuthRepository(...)` construction instead:
  ```kotlin
  val authRepository: AuthRepository? by lazy {
      runCatching { AuthRepository(authGateway, accountRepository) }.getOrNull()
  }
  ```
* Phase 1 needs to fit *into* that existing shape rather than reintroduce gateway-level wrapping. The cleanest fit: make `authGateway` itself resolve to `LocalAuthGateway` whenever Firebase isn't available, so `authRepository` construction always succeeds and is never `null` for that reason — for example:
  ```kotlin
  val authGateway: AuthGateway by lazy {
      runCatching { FirebaseAuthGateway(FirebaseAuth.getInstance()) }
          .getOrElse { LocalAuthGateway(appContext) }
  }

  val authRepository: AuthRepository by lazy {
      AuthRepository(authGateway, accountRepository)
  }
  ```
  This still keeps the "who touches Firebase and when" logic in one place (this class), it just relocates the fallback back to the gateway boundary now that there's a real, non-throwing alternative (`LocalAuthGateway`) to fall back to — the earlier code review's objection was about swallowing a *hard failure* per call site, not about the gateway seam itself.
* **Result:** `container.authRepository` is **never null**. `MainActivity` renders the actual Sign Up / Sign In screens — `AuthUnavailableScreen` was dead code once this landed, and has been deleted.

---

### Phase 2: Guest-to-Account Migration (ADR-0004) — ✅ Shipped (ticket 08)

**Goal:** Ensure users who start exercising as a Guest can convert to an Account without losing any workout history or streaks.

> Tallies are deliberately out of scope here, not an oversight: `TrackedProfileEntity.accountId` is non-null (Quick Count/Roster is Account-holder-only), so a Guest structurally cannot have an unowned Tally to begin with. `GuestAccountMigration.kt`'s doc comment flags this as an open tension with `CONTEXT.md`'s user stories, not something this migration silently resolves.

#### 2.1 The Migration Contract (implemented: `AuthRepository.signUp`, `GuestAccountMigration`, `AccountRepository.claimGuestData`)
When a Guest calls `authRepository.signUp()`:
1. `AuthGateway.signUpWithEmail` returns a new `accountId`.
2. `AccountRepository.createAccount(accountId)` creates the local `AccountEntity` row.
3. `AccountRepository.hasUnclaimedGuestData()` checks if any `SessionEntity` rows have `accountId == null`.
4. If unclaimed records exist, `GuestAccountMigration(accountId)` executes `AccountRepository.claimGuestData`, atomically (single Room transaction):
   - Updates `SessionEntity.accountId` from `null` &rarr; `newAccountId` for every unowned Session.
   - Recomputes that account's `currentStreak`/`bankedShields` from its now-complete Set history via `StreakCalculator`.
   - **Does not separately enqueue anything into `pending_sync`** — every Set is already enqueued at write time by `SetRepository.recordSet`, regardless of ownership, so a Guest's Sets are never withheld from the sync queue while unowned. Migration only changes *who owns* the row, not whether it's queued to sync.

**Superseded by `workout-partner-v3` ticket 05:** `GuestAccountMigration.kt` (the thin `onGuestDataToMigrate` hook wrapper this section describes) is deleted — `AuthRepository.signUp` calls `AccountRepository.claimGuestData` directly. The migration contract itself also widened well beyond this section's description: it now also claims unowned Tracked Profiles/Tallies and the Guest's Weekly Target (Tallies are no longer out of scope — `TrackedProfileEntity.accountId` became nullable in ticket 02), and sign-in no longer silently ignores pending Guest data — it reports it via `SignInResult.GuestDataPending` for the Athlete to resolve with `AccountRepository.mergeGuestData`/`discardGuestData`. See that ticket's file for the current contract.

---

### Phase 3: Firebase Provisioning & Production Cloud Auth — ◐ Partially shipped (dev project + Firestore rules, ticket 14)

**Goal:** Enable secure, production-grade cloud authentication with Firebase.

#### 3.1 Provisioning Checklist
- [x] Create a Firebase Project in the Firebase Console — a dev project (`workout-partner-2455c`) already exists.
- [x] Register Android app with package name `com.workoutpartner.app` — confirmed in the local `google-services.json`.
- [x] `google-services.json` already exists at `app/google-services.json` for local/dev use (gitignored per `.gitignore:31` — each developer/environment provisions their own; a separate prod project still needs the same steps repeated).
- [x] **In `app/build.gradle.kts`, apply the plugin** — done (`alias(libs.plugins.google.services)`), alongside the Credential Manager / Google ID dependencies.
- [ ] Enable the **Email/Password** and **Google** authentication providers in Firebase Console > Authentication > Sign-in method (owner's manual step).

#### 3.2 Error Handling & User Feedback
Map Firebase Auth exceptions to user-friendly messages on UI:
* `FirebaseAuthWeakPasswordException` &rarr; "Password must be at least 6 characters."
* `FirebaseAuthInvalidCredentialsException` &rarr; "Invalid email or password."
* `FirebaseAuthUserCollisionException` &rarr; "An account already exists with this email."
* `FirebaseNetworkException` &rarr; "Network error. You can continue offline in Local Mode."

#### 3.3 Extensibility for Social Logins
The `AuthGateway` interface was extended this way (shipped). It takes the Google ID token rather than an `Activity`, so the data module carries no Android UI types; `LocalAuthGateway.signInWithGoogle` throws a typed "unavailable in local mode" error:
```kotlin
interface AuthGateway {
    val currentUserId: Flow<String?>
    suspend fun signUpWithEmail(email: String, password: String): String
    suspend fun signInWithEmail(email: String, password: String): String
    suspend fun signInWithGoogle(idToken: String): String
    suspend fun signOut()
}
```

---

### Phase 4: Sync Engine & Reconnection Strategy (ADR-0002) — ✅ Shipped (ticket 06)

**Goal:** Reconcile local SQLite mutations with Cloud Firestore upon connection.

```text
[Workout Complete]
        │
        ├── 1. Insert SetEntity into local Room DB
        └── 2. Enqueue PendingSyncEntity (kind=SET, id=setId)
                    │
                    ▼ (SyncEngine detects online network)
           [FirestoreSyncGateway.pushSet(...)]
                    │
                    ▼ (On Success)
           [Delete from PendingSyncEntity]
```

1. **Outbox Pattern:** Writes never block on network availability. All sets and tallies are written to Room first, and a `pending_sync` row is recorded.
2. **Batch Flushing:** `SyncEngine.syncAll()` drains the queue when `remoteSyncGateway` reports availability.
3. **Idempotency:** Document IDs in Firestore match client UUIDs (`SetEntity.id`, `TallyEntity.id`), preventing duplicate writes on retry.

---

## 🗄️ Entity & Database Relationships (ERD)

The Room/SQLite schema is already structured to support this strategy:

| Table | Primary Key | Foreign Key | Nullable Account Field? |
| :--- | :--- | :--- | :--- |
| `accounts` | `id` (Text) | None | N/A (Primary identity) |
| `sessions` | `id` (UUID) | `accountId` &rarr; `accounts.id` | **Yes** (NULL for Guest, set on migration) |
| `sets` | `id` (UUID) | `sessionId` &rarr; `sessions.id` | N/A (Inherits ownership via Session) |
| `tracked_profiles` | `id` (UUID) | `accountId` &rarr; `accounts.id` | **Yes** (NULL for a Guest's own Roster, `workout-partner-v3` ticket 02) |
| `tallies` | `id` (UUID) | `trackedProfileId` &rarr; `tracked_profiles.id` | N/A (Inherits ownership via Profile) |
| `pending_sync` | `queueId` (Long) | None (Outbox queue) | N/A |
| `guest_profile` | `id` (Int, fixed single row) | None | N/A (pre-Account, device-local only) |

`workout-partner-v2` (tickets 01/02/03, schema version 2 &rarr; 4) extended
this without any new tables besides `guest_profile`:
- `accounts` gained `name` (Text), `age` (Int), `heightCm` (Int), `weightKg`
  (Real), and `activityLevel` (Text, `LOW`/`MEDIUM`/`HIGH`) — all nullable,
  since an Account may not have answered onboarding yet (ticket 01).
- `guest_profile` mirrors those same five columns (all non-null — it's only
  ever inserted once the user actually submits them), for a Guest's
  pre-Account answers; claimed onto `accounts` and cleared on sign-up.
- `routines` gained `format` (Text, `STANDARD`/`HIIT`/`TABATA`/`AMRAP`),
  defaulted to `STANDARD` — a display tag only, per `RoutineFormat`'s own
  doc comment (ticket 02).
- `tallies` gained `formScore` (Int) and `durationSeconds` (Int), both
  nullable — a Tally recorded before ticket 03 landed genuinely has
  neither. This reverses the file's own earlier claim that "Quick Count
  Tallies never have a Form Score"; see `CONTEXT.md`'s Form Score/Tally
  entries and `firestore.rules`' `tallies` match block, both updated
  alongside this.

`workout-partner-v3` (ticket 02, schema version 4 &rarr; 5) is a pure schema
foundation for the Guest-parity/Activity-Level/Step-Jack work that follows —
no new tables, no app behavior changes:
- `ActivityLevel` becomes a 4-tier scale (`SEDENTARY`/`LIGHTLY_ACTIVE`/
  `ACTIVE`/`VERY_ACTIVE`, replacing `LOW`/`MEDIUM`/`HIGH`); existing
  `accounts`/`guest_profile` rows are remapped LOW&rarr;SEDENTARY,
  MEDIUM&rarr;LIGHTLY_ACTIVE, HIGH&rarr;ACTIVE (the conservative direction —
  see `MIGRATION_4_5`'s doc comment).
- `tracked_profiles.accountId` becomes nullable (table row above) — a Guest
  can now own a Roster too (ticket 06 is what actually lets them).
- `guest_profile` gains `weeklyTarget` (Int), `currentStreak` (Int),
  `bankedShields` (Int), `notificationsEnabled` (Bool) — the same
  Streak/Weekly-Target/Shields/notification state an Account carries,
  same defaults — and its five body-stats columns become nullable, since a
  Guest can now have Streak state before (or without) answering onboarding.
- `sets` and `tallies` each gain a nullable `exerciseVariant` (Text) column
  — null for an Exercise's standard form, the variant's name (e.g.
  `"STEP_JACK"`) otherwise. Deliberately typed as a raw column for now;
  ticket 08 defines the real `ExerciseVariant` type in `core-rep-counting`.

---

## 🧪 Verification & Testing Strategy

### Unit Tests
* `LocalAuthGatewayTest` (✅ written, ticket 15 — 9 cases, all passing):
  * Verify registration persists credentials and emits user ID.
  * Verify sign-in validates password correctly.
  * Verify sign-out resets `currentUserId` to `null`.
* `AuthRepositoryTest` (✅ exists today):
  * Verify `authState` emits `AuthState.Guest` when user is null, and `AuthState.SignedIn(uid)` when set.
  * Verify `onGuestDataToMigrate` trigger fires when guest data exists.
* `GuestAccountMigrationTest` / `AccountRepositoryTest` (✅ exist today):
  * Verify all orphan sessions (`accountId == null`) update to the new account ID.
  * Verify calculated streaks and shields match expected test workout history.

### End-to-End User Verification Flow
1. **Fresh Start as Guest:** Open app &rarr; Accept disclaimer &rarr; "Continue as Guest".
2. **Perform Workout:** Run and complete a routine session &rarr; Set saved with `accountId == null`.
3. **Account Conversion:** Tap "Sign up" &rarr; Register with email and password.
4. **Validation:** 
   * Account profile created.
   * Past guest workout is claimed.
   * Streaks and shields calculate immediately.
   * Roster and Quick Count become unlocked.
