# Data Handling Architecture & Multi-Device Sync Roadmap

This document establishes the architecture, data contracts, and implementation roadmap for professionalizing data management in **Workout Partner**. It bridges the gap between the current local/push-only prototype and an enterprise-grade, offline-first mobile architecture aligning with [ADR-0001](adr/0001-firebase-as-shared-backend.md), [ADR-0002](adr/0002-offline-first-sync.md), [ADR-0004](adr/0004-guest-to-account-migration.md), and [ADR-0006](adr/0006-camera-tracking-lives-in-android-only.md).

> **Status (2026-09-21):** a roadmap, not shipped work — apart from the Guest
> merge/discard prompt (Phase 5's second item), none of the phases below is
> built. Two of its proposals conflicted with existing decisions and have been
> settled by ADR; where this document differs from them, the ADRs win:
> [ADR-0009](adr/0009-streak-computed-on-device-cloud-authority-later.md)
> (Cloud Functions become the Streak authority *after* pull-sync, not before)
> and [ADR-0010](adr/0010-android-owner-writes-with-field-limits.md) (which
> fields the owner may write, and what that means for ADR-0006).

---

## 1. Executive Summary & Gap Analysis

### Current State
* **Push-Only & Partial Sync**: Only `sets` and `tallies` are synced to Firestore via `PendingSyncEntity`. `accounts`, `sessions`, and `trackedProfiles` (Roster) exist solely in local Room storage.
* **No Pull Mechanism**: Workouts or profiles created on another device (or web) never sync down to the local Room database.
* **Dormant Sync Queue**: `SyncEngine.syncPendingChanges()` is never invoked by any background worker or lifecycle event in production code; it only runs in unit tests.
* **Non-Reactive DAOs**: Room DAOs return one-shot `suspend` lists/entities (`getForAccount()`, `getById()`). ViewModels rely on manual, imperative `refresh()` calls; changes from background sync or other screens cannot update the UI automatically.
* **Leaky Database Entities**: Room `@Entity` classes (`AccountEntity`, `SetEntity`, `TrackedProfileEntity`) are passed directly into Presentation ViewModels and Composables.
* **Unenforced Gamification**: Streaks and banked shields are calculated entirely on the client without server validation, leaving multi-device usage vulnerable to state divergence.
* ~~**Undefined Guest Merging**~~ — resolved (`workout-partner-v3` tickets 05 and 09): signing in to an existing Account with Guest data now asks the Athlete to merge or discard ([ADR-0007](adr/0007-guest-feature-parity-stays-local.md)). Only the "download the Account's cloud history afterwards" half remains, and it needs the Phase 2 pull path.

### Target State
* **Bidirectional Hybrid Sync**: Additive push and pull for workout activities (Sessions, Sets, Tallies), combined with **Strict Cloud Authority** for high-stakes gamification states (Streaks, Shields, Milestones).
* **Automated WorkManager Orchestration**: Multi-trigger sync execution via `SyncWorker` (expedited post-workout sync, lifecycle/reconnect triggers, and 6-hour periodic constraints).
* **Reactive Single Source of Truth (SSOT)**: Room DAOs expose `Flow<T>`, Repositories transform entities to pure **Domain Models**, and ViewModels expose `StateFlow` via `.stateIn()`.
* **Optimistic UI with Sync Indicators**: Real-time optimistic updates during workouts paired with subtle sync state badges (`Synced`, `Syncing`, `Offline`).
* **Interactive Conflict Resolution**: ✅ shipped — the merge-or-discard prompt on sign-in (ticket 09).

---

## 2. Core Architectural Pillars

### Pillar A: The Hybrid Sync Model
To balance offline-first gym tracking with tamper-proof gamification:
1. **Lesson/Activity Flow (Client-Optimistic & Additive)**:
   * When a user records a Set or Tally, it is written immediately to Room with a client-generated UUID.
   * The client updates its local optimistic streak immediately for instant user feedback.
   * Changes are queued in `pending_sync` and synced additively to Firestore. Two devices logging offline produce distinct documents with zero collisions.
2. **Gamification State (Strict Cloud Authority — later, per [ADR-0009](adr/0009-streak-computed-on-device-cloud-authority-later.md))**:
   * Until pull-sync exists, Streak and Shields are computed on-device by `StreakCalculator`. After it ships, Streak counts and banked shields are calculated by Firebase Cloud Functions triggered by Firestore writes; Guests stay client-computed. Personal Bests stay client-side.
   * From that point, Firestore security rules reject client writes to the gamification fields (see [ADR-0010](adr/0010-android-owner-writes-with-field-limits.md)); before it, they are client-writable.
   * The client pulls the authoritative cloud gamification state on sync completion, smoothly reconciling any optimistic drift.

```text
 ┌────────────────────────────────────────────────────────────────────────┐
 │                              ANDROID CLIENT                            │
 │                                                                        │
 │  [ Workout Screen ] ──(Record Set)──► [ Room DB (SSOT) ]              │
 │                                             │                          │
 │                                        (Outbox Queue)                  │
 │                                             ▼                          │
 │                                     [ SyncEngine / Worker ]            │
 └─────────────────────────────────────────────┬──────────────────────────┘
                                               │
                   1. Push Additive Sets       │  3. Pull Authoritative
                      & Sessions               │     Streak & Missing Data
                                               ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                           FIREBASE BACKEND                             │
 │                                                                        │
 │  Firestore: sets/{setId} ──► [ Cloud Function: onSetCreated ]          │
 │                                     │                                  │
 │                              (Calculate & Validate)                    │
 │                                     ▼                                  │
 │                           Firestore: accounts/{accountId}              │
 │                           (currentStreak, bankedShields)               │
 └────────────────────────────────────────────────────────────────────────┘
```

---

### Pillar B: Reactive SSOT Data Flow
Eliminate manual `refresh()` calls by establishing unidirectional, reactive streaming:

```text
Room SQLite ──(Flow<Entity>)──► Repository ──(Flow<DomainModel>)──► ViewModel ──(StateFlow<UiState>)──► Compose UI
      ▲                                                                                                       │
      └────────────────────────── User Mutation / Sync Pull ──────────────────────────────────────────────────┘
```

1. **DAOs**: Expose `Flow<List<SetEntity>>`, `Flow<AccountEntity?>`, `Flow<List<TrackedProfileEntity>>`.
2. **Repositories**: Convert entities to clean Domain models (`Account`, `WorkoutSet`, `TrackedProfile`) and expose `Flow<DomainModel>`.
3. **ViewModels**: Transform domain flows into presentation `UiState` via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ...)`.
4. **Writes**: Any write (local UI completion or background Firestore pull) writes directly to Room. Room invalidates query observers, and UI updates reactively and automatically.

---

### Pillar C: WorkManager Multi-Trigger Orchestration
Sync execution is managed via a dedicated `SyncWorker` triggered across multiple conditions:

| Trigger Point | Mechanism | Policy / Constraints |
|---|---|---|
| **Workout Finished** | Expedited `OneTimeWorkRequest` | `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`, `NetworkType.CONNECTED` |
| **App Foreground / Resume** | Lifecycle Observer | Trigger background coroutine if online; skip if recently synced |
| **Network Reconnected** | `ConnectivityManager.NetworkCallback` | Immediate execution of pending outbox queue |
| **Periodic Background Sync** | `PeriodicWorkRequest` (6 hours) | Constraints: `NetworkType.CONNECTED`, `BatteryNotLow` |
| **Account Sign-in / Migration** | Direct Coroutine Trigger | Full bidirectional pull and outbox flush |

---

## 3. Data Contracts & Firestore Schema

### 3.1 `accounts/{accountId}`
Mirrors the Account domain model.

| Field | Type | Modifiable By | Notes |
|---|---|---|---|
| `id` | string | System | Matches Firebase Auth UID. |
| `name` | string? | Client (Owner) | Onboarding / Profile name. |
| `age` | int? | Client (Owner) | Profile age. |
| `heightCm` | int? | Client (Owner) | Profile height in cm. |
| `weightKg` | double? | Client (Owner) | Profile weight in kg. |
| `activityLevel` | string? | Client (Owner) | `SEDENTARY`, `LIGHTLY_ACTIVE`, `ACTIVE`, `VERY_ACTIVE`. |
| `weeklyTarget` | int | Client (Owner) | Weekly target active days (1–7). |
| `notificationsEnabled` | bool | Client (Owner) | Reminder toggle. |
| `currentStreak` | int | Client until the Cloud Function ships, then **Cloud Function Only** | Consecutive weeks met (ADR-0009, ADR-0010). |
| `bankedShields` | int | Client until the Cloud Function ships, then **Cloud Function Only** | Banked shields (ADR-0009, ADR-0010). |
| `updatedAt` | timestamp | Client / Cloud | Conflict resolution timestamp. |

### 3.2 `sessions/{sessionId}`
Mirrors a completed workout Session.

| Field | Type | Modifiable By | Notes |
|---|---|---|---|
| `id` | string | Client (Owner) | UUID matching `SessionEntity.id`. |
| `accountId` | string | Client (Owner) | Owning Account UID (non-null in cloud). |
| `routineId` | string | Client (Owner) | Bundled routine ID. |
| `timestamp` | timestamp | Client (Owner) | Session start/completion timestamp. |

### 3.3 `sets/{setId}`
Append-only immutable record of a completed exercise set.

| Field | Type | Modifiable By | Notes |
|---|---|---|---|
| `id` | string | Client (Owner) | UUID matching `SetEntity.id`. Immutable. |
| `accountId` | string | Client (Owner) | Owning Account UID. |
| `sessionId` | string | Client (Owner) | Owning Session ID. |
| `exercise` | string | Client (Owner) | Enum name (`SQUAT`, `PUSH_UP`, etc.). |
| `targetReps` | int | Client (Owner) | Target rep count. |
| `actualReps` | int | Client (Owner) | Completed rep count. |
| `formScore` | int | Client (Owner) | 0–100 score. |
| `goodSet` | bool | Client (Owner) | Reps >= target && formScore >= threshold. |
| `exerciseVariant` | string? | Client (Owner) | `null` for the standard form, `STEP_JACK` for Step Jack. |
| `timestamp` | timestamp | Client (Owner) | Completion timestamp. |

### 3.4 `tallies/{tallyId}`
Append-only immutable record of a Quick Count session for a Tracked Profile.

| Field | Type | Modifiable By | Notes |
|---|---|---|---|
| `id` | string | Client (Owner) | UUID matching `TallyEntity.id`. Immutable. |
| `accountId` | string | Client (Owner) | Owning Account UID. |
| `trackedProfileId` | string | Client (Owner) | ID of the profile in the Roster. |
| `exercise` | string | Client (Owner) | Enum name. |
| `repsAchieved` | int | Client (Owner) | Rep count. |
| `target` | int? | Client (Owner) | Optional target. |
| `formScore` | int? | Client (Owner) | Average form score. |
| `durationSeconds` | int? | Client (Owner) | Duration of the run. |
| `exerciseVariant` | string? | Client (Owner) | Same meaning as on `sets`. |
| `timestamp` | timestamp | Client (Owner) | Completion timestamp. |

### 3.5 `trackedProfiles/{profileId}`
Roster profiles owned by an Account.

| Field | Type | Modifiable By | Notes |
|---|---|---|---|
| `id` | string | Client (Owner) | UUID matching `TrackedProfileEntity.id`. |
| `accountId` | string | Client (Owner) | Owning Account UID. |
| `displayName` | string | Client (Owner) | Profile display name. |
| `isDeleted` | bool | Client (Owner) | Soft-delete tombstone for multi-device sync. |
| `updatedAt` | timestamp | Client (Owner) | Last modification timestamp. |

---

## 4. Firestore Security Rules Specification

Update `firestore.rules` to enforce the hybrid security model. The exact field lists are fixed by [ADR-0010](adr/0010-android-owner-writes-with-field-limits.md); this section is the summary, and it ships with the Phase 2 push path, not before:
1. **Read Access**: Only the authenticated owner (`request.auth.uid == resource.data.accountId`) can read their own documents.
2. **Activity Immutability**: `sets` and `tallies` remain create-only (no update, no delete).
3. **Sessions & Profiles**: Owner allowed create/update (with tombstone support). `accounts` is owner-updatable for the settings and body-stats fields only.
4. **Gamification Lockdown — once the Cloud Function ships** ([ADR-0009](adr/0009-streak-computed-on-device-cloud-authority-later.md)): updates submitted by clients must NOT alter `currentStreak` or `bankedShields` (`!request.resource.data.diff(resource.data).affectedKeys().hasAny(['currentStreak', 'bankedShields'])`). Before then those two fields are client-writable.
5. **Web's read-only role** ([ADR-0006](adr/0006-camera-tracking-lives-in-android-only.md)) is a scope rule of the Web project, not something these rules can enforce.

---

## 5. Phased Implementation Roadmap

```mermaid
timeline
    title Data Architecture Implementation Roadmap
    section Phase 1 : Reactive SSOT & Domain Decoupling
        Room DAOs Flow emissions : Expose Flow<T> on DAOs
        Pure Domain Models : Create Account, WorkoutSet, TrackedProfile
        Repository Flow Refactor : Map entities to Domain models
        ViewModel stateIn Migration : Replace imperative refresh() with StateFlow
    section Phase 2 : Bidirectional Sync Engine & Firestore Schema
        Expand Firestore Rules : Allow owner writes on Sessions, Roster & Profile
        RemoteSyncGateway Pull API : Implement pullSets, pullSessions, pullProfiles
        SyncEngine Push & Pull Pipeline : Full bidirectional reconciliation in Room
        Outbox Queue Expansion : Support Session and Roster pending syncs
    section Phase 3 : WorkManager Multi-Trigger Automation
        SyncWorker Implementation : CoroutineWorker running SyncEngine
        SyncScheduler Service : Expedited post-workout & 6h periodic work
        Lifecycle & Connectivity Listeners : Auto-trigger sync on resume & reconnect
        AppContainer & Application Wiring : Wire into WorkerFactory
    section Phase 4 : Cloud-Authoritative Gamification Backend
        Cloud Function onSetCreated : Serverless active days & streak calculator
        Authoritative Account Sync : Pull verified streaks/shields into local cache
        Optimistic Drift Reconciliation : Smooth transition from local to verified
    section Phase 5 : UI Sync State & Interactive Migration UX
        SyncStatusBadge Component : Visual indicators (Syncing, Synced, Offline)
        ProgressScreen Sync Integration : Embed sync badge on streak/heatmap cards
        GuestMergeConflictDialog : Interactive prompt on existing account sign-in
```

### Phase 1: Reactive SSOT & Domain Decoupling
* **Deliverables**:
  1. Define pure domain models in `com.workoutpartner.data.domain`: `Account`, `WorkoutSet`, `WorkoutSession`, `TrackedProfile`, `Tally`.
  2. Update `AccountDao`, `SetDao`, `SessionDao`, `TrackedProfileDao`, `TallyDao` to add `Flow`-returning queries.
  3. Update repositories to expose reactive `Flow` methods mapping entities to domain models.
  4. Refactor `ProgressViewModel`, `RosterViewModel`, and `SettingsViewModel` to bind to repositories via `stateIn()`.
* **Testing**: Robolectric tests verifying that inserting a new row in Room immediately emits updated values down the ViewModel's `StateFlow`.

### Phase 2: Bidirectional Sync Engine & Firestore Schema
* **Deliverables**:
  1. Update `firestore.rules` to support owner writes on `sessions`, `trackedProfiles`, and user-editable fields of `accounts`.
  2. Extend `RemoteSyncGateway` and `FirestoreSyncGateway` with pull operations: `pullSessions`, `pullSets`, `pullTallies`, `pullTrackedProfiles`.
  3. Enhance `SyncEngine` with a two-phase cycle: **Push Outbox** -> **Pull Remote Deltas** -> **Upsert into Room**.
  4. Support soft-deleted tombstones (`isDeleted`) for `TrackedProfileEntity` sync across devices.
* **Testing**: Unit tests with `FakeRemoteSyncGateway` verifying that sets/sessions created remotely are inserted into Room on sync.

### Phase 3: WorkManager Multi-Trigger Automation
* **Deliverables**:
  1. Create `SyncWorker: CoroutineWorker` delegating to `SyncEngine.syncPendingChanges()`.
  2. Implement `SyncScheduler`:
     * Expedited `OneTimeWorkRequest` triggered upon `recordSet` or `recordTally`.
     * Periodic `PeriodicWorkRequest` (6 hours) requiring `NetworkType.CONNECTED`.
  3. Add app lifecycle resume observer and network connectivity callback to trigger sync when regaining internet.
  4. Register `SyncWorker` in `WorkoutPartnerWorkerFactory`.
* **Testing**: WorkManager testing library tests verifying constraint satisfaction and worker retry behavior on network failure.

### Phase 4: Cloud-Authoritative Gamification Backend
> Scheduled **after** Phase 2's pull path ([ADR-0009](adr/0009-streak-computed-on-device-cloud-authority-later.md)). Requires a paid Firebase plan and shared fixtures so the TypeScript port passes `StreakCalculator`'s cases.

* **Deliverables**:
  1. Author Cloud Functions script (`functions/src/index.ts`) with `onDocumentCreated("sets/{setId}")`:
     * Aggregates active days for the owning account.
     * Evaluates streaks and banked shields matching `StreakCalculator` logic.
     * Writes updated gamification stats to `accounts/{accountId}` via Firebase Admin SDK.
  2. Update `SyncEngine` to fetch the authoritative account state and update local Room cached streak columns.
* **Testing**: Cloud Functions unit/emulator tests with simulated set insertions across multi-week active day scenarios.

### Phase 5: UI Sync State & Interactive Migration UX
* **Deliverables**:
  1. Create `SyncStatusBadge` composable displaying status icons:
     * `SYNCED`: Cloud with subtle checkmark.
     * `SYNCING`: Animated spinning sync indicator.
     * `OFFLINE_PENDING`: Cloud-slash icon with "Saved locally" tooltip.
  2. Integrate `SyncStatusBadge` into `ProgressScreen` and session summary screen.
  3. ~~Create `GuestMergeConflictDialog`~~ — shipped as the merge-or-discard prompt (`workout-partner-v3` ticket 09, [ADR-0007](adr/0007-guest-feature-parity-stays-local.md)). *Merge* assigns Guest data to the Account (the Account's Weekly Target wins); *Discard* permanently deletes it. Downloading the Account's cloud history afterwards depends on Phase 2's pull path.
* **Testing**: Compose UI tests verifying dialog choices and sync badge state transitions.

---

## 6. Testing & Quality Assurance Matrix

| Test Layer | Focus Area | Tools & Methodologies |
|---|---|---|
| **Data Layer Unit Tests** | Room DAO Flow emissions, Repository domain mapping | Robolectric, In-memory Room, Coroutines `Turbine` |
| **Sync Engine Tests** | Push outbox, pull deltas, conflict resolution, offline queueing | JUnit 4, `FakeRemoteSyncGateway`, In-memory Room |
| **WorkManager Tests** | Worker factory injection, periodic scheduling, backoff retry | `androidx.work.testing.WorkManagerTestInitHelper` |
| **Security Rules Tests** | Unauthorized reads, client tampering with streak fields | `@firebase/rules-unit-testing`, Firestore Emulator |
| **UI State & UX Tests** | Sync badge transitions, Guest conflict dialog user choices | `androidx.compose.ui.test.junit4`, Compose rule |
