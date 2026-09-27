Status: done

# 07 — Auth module

## Scope

Firebase Auth wrapper. Covers user stories 1, 5, 6, 7.

- Guest session state: using the app without an Account (per `CONTEXT.md`'s Guest definition) — no Firebase Auth identity involved while a Guest, purely local.
- Sign up with email/password (spec doesn't require a specific additional provider — implement email/password as the baseline; treat "or a provider" as an easy extension point, not a hard v1 requirement, since the spec doesn't name which provider).
- Sign in on a new device; synced history should become visible (depends on ticket 06's sync).
- Triggers the Guest → Account migration transaction (ticket 08) on sign-up when a local Guest record exists.

## Depends on

Ticket 06 (repository layer, for wiring sign-in to a synced Account).

## Out of scope

The migration transaction's logic itself (ticket 08), UI screens (ticket 13 for the onboarding/sign-up flow).

## Comments

Implemented in `data`: `AuthGateway`/`FirebaseAuthGateway`/`FakeAuthGateway`
mirror ticket 06's `RemoteSyncGateway`/`FirestoreSyncGateway`/
`FakeRemoteSyncGateway` split — email/password only (the ticket's baseline;
adding a provider later means adding another `signUpWith*`/`signInWith*`
pair, not a redesign). `AuthState` (`Guest`/`SignedIn`) is a plain mapping
of whether Firebase reports a current user at all; no anonymous-auth API is
used anywhere, per CONTEXT.md's Guest definition ("_Avoid_: Anonymous
user"). `AuthRepository.signUp` creates the local `AccountEntity` and, if
`AccountRepository.hasUnclaimedGuestData()` (new, small addition) says this
device has unclaimed Guest data, calls `onGuestDataToMigrate(accountId)` —
**detecting and triggering**, per this ticket's own wording, not migrating.

That callback defaults to a no-op. First draft had it default to calling
`AccountRepository.claimGuestData` directly — `/code-review`'s Spec pass
correctly caught this as performing the migration's actual substantive
effect (re-ownership + Streak recompute) as shipped default behavior, not
merely exposing a trigger point, which oversteps this ticket's own "the
migration transaction's logic itself (ticket 08)" out-of-scope line.
Corrected: the default is now an inert `{}`, and ticket 08 supplies the real
function — likely one built on `claimGuestData`, plus whatever else a full
transaction needs (e.g. deciding what happens if it fails partway through,
since `signUp` has already created the Firebase identity and local Account
row by the time this callback runs).

`signIn` similarly stayed minimal after review: it authenticates and
returns the accountId only, without fabricating a local `AccountEntity` row
for an Account this device has never cached. This ticket's own line ("Sign
in on a new device; synced history should become visible") stays genuinely
unmet — it depends on ticket 06's disclosed no-pull-sync gap, not something
this ticket can paper over.

7 tests passing (`./gradlew :data:test`, 20 total in the module): Guest by
default, sign-up creates a local Account and flips `authState`, the
migration callback fires with the right accountId only when Guest data
exists (and is a no-op by default even when it does), sign-in returns an
existing Account's id without inventing local state, sign-out returns to
Guest. Full project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one real scope-creep
issue — the original `onGuestDataToMigrate` default performed real migration
effects rather than just triggering; fixed as described above, and
confirmed the ticket's own `signIn` promise is honestly left open rather
than half-solved) and the repo's ADRs/CONTEXT.md/spec.md (Standards: no
hard violations; simplified `FakeAuthGateway` to drop
Firebase-validation-mimicking logic (duplicate-email/wrong-password checks)
that no test exercised, matching `FakeRemoteSyncGateway`'s minimalism; and
extracted the `Task<T>.awaitResult()` Play-Services-to-suspend bridge out of
`FirestoreSyncGateway` into a shared `Tasks.kt` so `FirebaseAuthGateway`
doesn't redefine it).

Ticket 08 (Guest -> Account migration) and ticket 13 (onboarding/sign-up UI)
depend on this and are now unblocked on the auth piece.
