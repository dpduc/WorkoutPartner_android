Status: ready-for-agent

# 12 — Streak-aware daily reminder notification

## Scope

Covers user story 33. Local notification module (WorkManager/AlarmManager per spec's module list).

- Fires only when today is not yet an Active Day AND the Account hasn't yet met its Weekly Target for the week — both conditions checked against ticket 04's Streak Calculator / ticket 06's Active Day data, not an unconditional daily ping.
- Respects the Account's notification preference (per `CONTEXT.md`'s Account fields).
- No notification for Guests without an Account, since Weekly Target/Streak state doesn't exist for them yet.

## Depends on

Ticket 04 (Streak Calculator), ticket 06 (repository layer, for Active Day data and notification preference).

## Out of scope

Any notification content/copy beyond a simple reminder; no push/FCM — this is local-only per spec's module description.

## Comments

Implemented in `app`: pure `ReminderPolicy` (fires only when notifications
are enabled, today isn't yet an Active Day, and this Monday-Sunday week
hasn't yet reached the Weekly Target — never an unconditional daily ping)
with its own unit tests, mirroring `ProgressStats`'s pure-policy split.
`DailyReminderWorker` is the WorkManager `CoroutineWorker` glue: checks
`AuthGateway.currentUserId` (null = Guest, no notification per this
ticket's own scope line), reads the Account's Weekly Target/notification
preference and Set history, and posts a local notification via
`NotificationCompat` — no push/FCM. Scheduled as a daily
`PeriodicWorkRequest` from `WorkoutPartnerApplication`. Added the
`POST_NOTIFICATIONS` runtime permission (required on Android 13+) with a
best-effort, non-blocking request from `MainActivity` alongside the
existing Camera permission gate.

An earlier draft had `DailyReminderWorker` construct its own
`WorkoutPartnerDatabase`/`AccountRepository`/`SetRepository` instances and
read `FirebaseAuth.getInstance().currentUser` directly, bypassing both
`AppContainer` (duplicating the exact wiring it already does) and ticket
07's `AuthGateway` abstraction — both review passes caught this as a real
DI/layering issue, not a style nit. Fixed with a `WorkoutPartnerWorkerFactory`
that hands the Worker `AppContainer`'s actual singletons: this required
disabling WorkManager's default `androidx.startup` auto-initializer in the
manifest (it runs before `Application.onCreate()`, too early for
`AppContainer` to exist) and having `WorkoutPartnerApplication` call
`WorkManager.initialize(...)` itself once the container is built. Also
switched from an inline `Clock.systemDefaultZone()` to an injected `Clock`
parameter, matching the project's "inject the clock" convention used
throughout `core-streaks`/`AccountRepository`/`SetRepository`.

`ReminderPolicy`'s week-boundary/target-met check is a deliberate, tested
parallel implementation of `core-streaks.StreakCalculator`'s own semantics,
not a call into it — `StreakCalculator`'s public `StreakStatus` output
doesn't expose "has this week's target been met so far" as its own value.
Documented as an accepted duplication risk (if `StreakCalculator`'s
week rules ever change, this needs updating to match), not an oversight.

5 tests passing (`./gradlew :app:test`, 29 total in the module, all in
`ReminderPolicyTest`): notifications-off, already-Active-today,
target-already-met-this-week, and the two "should remind" cases (target not
yet met this week; a prior week's Active Days don't count toward this
week's target). Full project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: no missing/partial
scope bullets found; the week-boundary duplication above was flagged and
documented rather than silently left implicit) and the repo's
ADRs/CONTEXT.md/spec.md (Standards: two hard violations found and fixed —
the DI/database-duplication issue and the `AuthGateway`-bypassing
`FirebaseAuth` call, both described above — plus the clock-injection
convention violation).

Known gaps carried forward, same as tickets 09/11: unverified against a
live Firebase project/device this session (no emulator, still no
`google-services.json`); a `PeriodicWorkRequestBuilder(1, DAYS)` fires
roughly every 24h from whenever first enqueued rather than at a specific
time of day — acceptable per this ticket's own scope (no delivery-time
requirement stated), not engineered around further.
