Status: done

# 13 — Onboarding, safety disclaimer & Guest flow UI

## Scope

Covers user stories 1, 3, 22.

- Safety disclaimer shown on first launch (app isn't a substitute for professional guidance) — must be shown before any tracking screen is reachable.
- "Continue as Guest" entry point that requires no Account.
- Post-Set prompt to create an Account while still a Guest (story 3), wired to ticket 08's migration transaction when the user follows through.
- Sign-up/sign-in screens wired to ticket 07's Auth module.

## Depends on

Ticket 07 (Auth module), ticket 08 (migration transaction).

## Out of scope

The migration transaction's logic (ticket 08), Session flow itself (ticket 09) beyond the point where the post-Set prompt is triggered.

## Comments

Implemented in `app`: `DisclaimerScreen` (story 22) gates every other screen
— `WorkoutPartnerApp`'s initial screen is computed from `OnboardingPrefs`
(`SharedPreferences`, not Room — this is UI-flow state, not app data) as
Disclaimer -> Welcome -> RoutinePicker, shown once each per install, never
on return visits. `WelcomeScreen` (story 1: Continue as Guest / Sign up /
Sign in), `SignUpScreen`/`SignInScreen` (wired to ticket 07's
`AuthRepository` — sign-up already runs the real `GuestAccountMigration`
via `AppContainer`'s existing wiring, ticket 08, not a stub), and
`GuestConversionDialog` (story 3) are the new screens. `accountId` is now
real Guest-vs-Account state derived from `AuthRepository.authState`,
replacing the hardcoded `null` tickets 09-12 used as a placeholder.

Two real issues from `/code-review`'s Spec pass, fixed:
- The Guest-conversion prompt was wired to fire once, after the whole
  *Session* — but spec.md story 3 says "after finishing **a Set**," and
  CONTEXT.md deliberately distinguishes Set from Session. Fixed by adding
  `SessionScreen.onSetFinished` (fires when a Set's summary is
  acknowledged, ticket 09's file gaining this one hook is exactly what its
  own scope line anticipated: "beyond the point where the post-Set prompt
  is triggered"), shown at most once per Session (not once per Set, to
  avoid nagging through a multi-Set Routine) via a `remember`-scoped flag.
- A minor, likely-imperceptible timing note, not fixed further: `accountId`
  comes from the reactive `authState` flow while sign-up's own success
  callback navigates immediately: nothing strictly orders the two, so the
  RoutinePicker top bar could in principle show "Sign up" for one frame
  right after a successful sign-up before `authState` catches up. Judged
  not worth synchronizing further — it's cosmetic and self-corrects on the
  next recomposition, not a data-integrity issue.

Two real issues from `/code-review`'s Standards pass, fixed:
- `AppContainer.authRepository` is now `AuthRepository?`, with the
  `runCatching` guard for the disclosed missing-`google-services.json` gap
  moved there from an ad-hoc wrapper in `MainActivity` — `AppContainer`
  already owns "who touches Firebase and when" (`authGateway`/
  `remoteSyncGateway`'s own `by lazy` doc comment), so this belongs with
  it, not re-solved per call site.
- `AuthScreens.kt`'s catch block was `catch (e: Exception)` around both the
  sign-up/sign-in call *and* the success callback — silently swallowing
  `CancellationException` (breaking structured concurrency) and
  misreporting a bug in the caller's own navigation as "Something went
  wrong." Fixed: rethrows `CancellationException`, and only wraps the
  actual Firebase call.

No new pure-logic tests — this ticket is UI/navigation wiring on top of
already-tested engines (`AuthRepository`/`GuestAccountMigration`, ticket
07/08). Full project build/tests (29 in the app module) green.

Reviewed via `/code-review` against this ticket (Spec: the Set-vs-Session
prompt-timing mismatch above; Standards: the DI-guard placement and the
exception-handling issue above — a third judgement call, `AuthForm` using
`remember`/`rememberCoroutineScope()` instead of a `ViewModel` unlike
`SessionViewModel`/`ProgressViewModel`, left as-is for a simple form with no
config-change-survival requirement stated anywhere).

The full first-run flow (disclaimer -> Guest/sign-up/sign-in -> Session ->
Guest-conversion prompt -> real migration) is wired end-to-end, though
unverified on a device this session (no emulator; still no
`google-services.json` or MediaPipe model asset, tickets 01/03/07's
disclosed gaps) — compilation and the existing engine-level tests are what
this session could verify.
