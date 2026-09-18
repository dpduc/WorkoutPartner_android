# 01: Auth cleanup — Google-only sign-in

**What to build:** Finish the already-uncommitted auth work in the working tree. Keep email and Google sign-in; remove phone/SMS sign-in entirely (interface, all gateway implementations, and the auth screens). `AuthGateway` must carry no Android UI types (the `android.app.Activity` parameter on phone verification goes away with phone sign-in itself). In local mode (no Firebase configured), Google sign-in fails with a clear, typed "not available in local mode" error that the UI maps to a friendly message — it must not crash.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `AuthGateway` interface: `sendPhoneVerificationCode` and `signInWithPhoneCode` are removed; `signInWithGoogle` remains; no method takes an `Activity` or other Android UI type.
- [ ] `FirebaseAuthGateway` implements Google sign-in for real; phone verification code is removed.
- [ ] `LocalAuthGateway.signInWithGoogle` throws a typed "unavailable in local mode" error; `FakeAuthGateway` supports Google sign-in for tests.
- [ ] `AuthRepository` drops `sendPhoneVerificationCode`/`signInWithPhoneCode`; `signInWithGoogle` remains (its Guest-claim behavior is reworked separately in ticket 05, not here).
- [ ] Phone/SMS UI (entry field, code-verification step) is removed from the auth screens; Google sign-in UI remains.
- [ ] `app/build.gradle.kts` / `gradle/libs.versions.toml`: the `com.google.gms.google-services` plugin and Credential Manager + Google ID dependencies stay; any phone-auth-only dependency is removed if one was added.
- [ ] `AndroidManifest.xml` reflects the same (no phone-auth-only manifest entries left over).
- [x] Existing `FakeAuthGateway`-based auth tests still pass; a new case covers the local-mode Google sign-in error message.

## Comments

Implemented as spec'd, plus one correction found in review: `AuthRepository.signInWithGoogle` was calling `ensureLocalAccount()`, which unconditionally created a local Account row and auto-claimed any unclaimed Guest data on every Google sign-in — the exact "Google sign-in silently claims any Guest data" bug the spec's problem statement names, and precisely the behavior ticket 05's pending/merge/discard contract exists to replace. Rather than ship that regression for three tickets, `signInWithGoogle` now delegates straight through like `signIn` does (touches no local Account/Guest state), matching user story 2 ("Google sign-in to behave exactly like email sign-in with respect to my Guest data"). `ensureLocalAccount` is deleted — it had no other caller once phone sign-in was removed.

No phone-auth-only Gradle dependency or manifest entry existed to remove (phone verification used Firebase's built-in `PhoneAuthProvider`, no extra artifact); `app/build.gradle.kts`, `AndroidManifest.xml`, `gradle/libs.versions.toml` were already correct from the prior uncommitted work and needed no changes here.

Reviewed via `/code-review` (Standards + Spec axes) before commit; both findings above came from that review.
