Status: done

# 15 — Local Auth Fallback (LocalAuthGateway)

## Scope

Per `docs/auth-roadmap.md` Phase 1 ("Local SQLite Fallback"): today,
`AppContainer.authGateway` is a plain `by lazy { FirebaseAuthGateway(FirebaseAuth.getInstance()) }`,
and `authRepository: AuthRepository?` swallows any construction failure
with `runCatching { ... }.getOrNull()` (the fix ticket 13's review landed,
moving nullability one level up from the gateway to the repository — see
`AppContainer`'s own doc comment for why). Without a real
`google-services.json` *and* the `com.google.gms.google-services` Gradle
plugin applied in `app/build.gradle.kts` (still deliberately unapplied),
that construction fails every time, `authRepository` is permanently
`null`, and `MainActivity` falls back to `AuthUnavailableScreen` —locking
out every Account-dependent feature (Rosters, Quick Count, Streak
Shields, Weekly Targets, Settings) for anyone without Firebase
provisioned, dev and prod alike.

- Implement `LocalAuthGateway` at
  `data/src/main/kotlin/com/workoutpartner/data/LocalAuthGateway.kt`,
  implementing the existing `AuthGateway` interface as-is (same shape
  `FirebaseAuthGateway`/`FakeAuthGateway` already implement — no interface
  changes, no new methods).
  - Local credential storage keyed by email, e.g. via `SharedPreferences`.
    This is a dev/offline fallback holding no real user data, but still
    store a password *hash* (never plaintext) — the point isn't to invite
    a false sense of security, but there's no reason to store secrets in
    the clear either.
  - `currentUserId: Flow<String?>` backed by a `MutableStateFlow`,
    mirroring `FirebaseAuthGateway`'s contract (`null` while signed out).
  - `signUpWithEmail(email, password)`: validate email format and a
    minimum password length (>= 6, matching Firebase's own rule so the
    roadmap's Phase 3.2 error-message mapping stays consistent regardless
    of which gateway is active); reject an already-registered email;
    generate a `local_usr_<uuid>` id; persist the record and update
    `currentUserId`.
  - `signInWithEmail(email, password)`: verify the stored hash; update
    `currentUserId`; throw on no-such-account or wrong password. Per
    `AuthGateway`'s own doc comment ("a thin wrapper, not an
    error-modeling layer"), this class doesn't need to model specific
    exception types the way Firebase's SDK does — it just needs to throw
    on failure and return the id on success.
  - `signOut()`: sets `currentUserId.value = null`.
- Wire the fallback into `AppContainer` at the **gateway** boundary rather
  than reintroducing per-call-site nullability:
  ```kotlin
  val authGateway: AuthGateway by lazy {
      runCatching { FirebaseAuthGateway(FirebaseAuth.getInstance()) }
          .getOrElse { LocalAuthGateway(appContext) }
  }

  val authRepository: AuthRepository by lazy {
      AuthRepository(authGateway, accountRepository, onGuestDataToMigrate = guestAccountMigration::invoke)
  }
  ```
  This keeps "who touches Firebase and when" in the one place that
  already owns it (per `AppContainer`'s own doc comment) — it just
  relocates the `runCatching` back to the gateway seam now that there's a
  real, non-throwing alternative to fall back to. `authRepository` was
  only ever nullable because nothing existed to fall back to; that reason
  goes away once this lands.
- Update `MainActivity` to match: `authRepository` is no longer nullable,
  so its two `AuthUnavailableScreen` branches (`AppScreen.SignUp` /
  `AppScreen.SignIn`, each currently reached only when
  `container.authRepository == null`) become dead code and should be
  removed rather than left as unreachable defensive branches. Whether
  `AuthUnavailableScreen` itself is deleted or kept around unused is an
  implementer's call — leaning toward deleting it, since nothing else
  references it.

## Testing

`LocalAuthGatewayTest` (Robolectric — already part of `data`'s test
setup, used the same way ticket 06's Room tests are): sign-up persists
credentials and emits the new user id; sign-up with an
already-registered email fails; sign-up rejects a too-short password;
sign-in with correct credentials succeeds and emits the existing user
id; sign-in with a wrong password or unknown email fails; sign-out
resets `currentUserId` to `null`.

No new automated coverage is expected for the `AppContainer`/
`MainActivity` changes themselves — `AppContainer` is a manual DI
composition root touching real `Context`/`FirebaseAuth` and has no
existing test coverage for the same reason. Verify those manually per
`docs/auth-roadmap.md`'s End-to-End User Verification Flow, run with no
`google-services.json` present, confirming Sign Up / Sign In render
instead of `AuthUnavailableScreen` and that ticket 08's Guest → Account
migration still fires correctly through the local gateway.

## Depends on

Ticket 07 (auth module — `AuthGateway`/`AuthRepository`/
`FirebaseAuthGateway`), ticket 08 (Guest → Account migration, already
wired into `AppContainer.authRepository` via `onGuestDataToMigrate` and
untouched by this ticket).

## Out of scope

- Actually provisioning Firebase (`docs/auth-roadmap.md` Phase 3) — a dev
  `google-services.json` already exists locally (gitignored), but
  applying the `com.google.gms.google-services` plugin and enabling
  Email/Password sign-in in a real Firebase project is separate,
  independent work.
- Any UI changes beyond removing `MainActivity`'s now-dead
  `AuthUnavailableScreen` branches — `SignUpScreen`/`SignInScreen`
  themselves already exist (ticket 13) and don't need to know which
  `AuthGateway` is behind `AuthRepository`.
- Social login / `signInWithGoogle` (`docs/auth-roadmap.md` Phase 3.3) —
  `LocalAuthGateway` only needs to satisfy today's `AuthGateway`
  interface (email/password), not anticipate future auth methods.
- Migrating data between a `LocalAuthGateway` identity and a later real
  Firebase identity on the same device (e.g. if Firebase becomes
  available after a user already signed up locally) — not something the
  roadmap or this ticket's scope asks for; flag it as an open question if
  it comes up during implementation rather than silently designing for it.

## Comments

Implemented as spec'd. `LocalAuthGateway` (`data/.../LocalAuthGateway.kt`):
`SharedPreferences`-backed, salted-SHA-256 password hashes, `local_usr_<uuid>`
ids, `currentUserId` persisted across process restarts (not required by this
ticket's text, but a natural extension — `FirebaseAuth` already behaves this
way, and it costs nothing extra to persist one more preference key).

`AppContainer.authGateway` now `runCatching { FirebaseAuthGateway(...) }
.getOrElse { LocalAuthGateway(appContext) }`; `authRepository` is no longer
nullable. `MainActivity`'s two `AuthUnavailableScreen` branches and the
composable itself are deleted — confirmed nothing else referenced it.

`LocalAuthGatewayTest` (Robolectric, 9 cases) covers sign-up persistence/
duplicate-email/short-password/invalid-email rejection, sign-in success/
wrong-password/unknown-email, and sign-out. Full `data`+`app` unit test
suites still pass. Manual E2E verification (fresh install, no
`google-services.json`, Guest workout -> Sign up -> guest migration) not
run in this session — recommend running it before shipping.
