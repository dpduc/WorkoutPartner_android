# 09: Merge-or-discard prompt on sign-in

**What to build:** Wire ticket 05's data-layer contract into real UI. Signing in (email or Google) to an existing Account while holding on-device Guest data prompts the Athlete to Merge or Discard it; a Guest with no on-device data signs in with no prompt at all.

**Blocked by:** 05.

**Status:** ready-for-human

- [x] Sign-in with pending Guest data shows a prompt offering Merge or Discard (not shown at all when there's nothing pending).
- [x] Choosing Merge shows a short confirmation of what was added afterward (e.g. "Added 12 workouts and 3 Tracked Profiles to this account").
- [x] Choosing Discard first shows exactly what will be lost, requiring confirmation before anything is deleted.
- [x] Backing out of the prompt (neither Merge nor Discard chosen) cancels the sign-in — the Athlete is signed back out and their Guest data is untouched and still unclaimed.
- [x] A failed Merge or Discard leaves Guest data intact and still unclaimed, with a retry path (surfacing ticket 05's transactional failure behavior to the user).
- [ ] Manually verified for both the email sign-in and Google sign-in paths.

## Comments

Implemented as spec'd: a new `GuestDataPromptDialog` (`app/src/main/kotlin/com/workoutpartner/app/onboarding/GuestDataPromptDialog.kt`) wired into `AuthScreens.kt`'s shared `AuthForm`, which both `SignUpScreen` and `SignInScreen` use. `AuthForm`'s email/Google submit callbacks now return ticket 05's `SignInResult` instead of a bare accountId; a `SignInResult.SignedIn` proceeds immediately, a `SignInResult.GuestDataPending` shows the dialog. `SignUpScreen`'s email path wraps `authRepository.signUp(...)`'s accountId in `SignInResult.SignedIn` (it never has pending data — signUp claims unconditionally), so both callers share one result-handling path.

The dialog is a four-step state machine (`Choosing` -> `ConfirmingDiscard`/`Merged`, or any resolving step -> `Failed` with a retry that reuses the same resolution): Discard requires an explicit second confirmation before `AccountRepository.discardGuestData` runs (nothing is deleted on the first "Discard" tap); Merge reports what was added only after `AccountRepository.mergeGuestData` actually succeeds. Backing out at any point before a resolution completes — the explicit "Cancel" buttons, the dialog's scrim-tap/system-Back dismiss — signs the Athlete back out (`AuthRepository.signOut()`) before returning control, per the ticket's "Cancel isn't a case in `AuthRepository`, the caller just calls `signOut` directly" design (ticket 05's `SignInResult.kt` doc comment). A thrown exception from `resolvePendingGuestData` is caught and surfaced as a retry prompt, not a crash or a silent no-op.

Also intentional, not a bug: "Continue with Google" is shared infrastructure between the Sign Up and Sign In screens (there's only one `AuthRepository.signInWithGoogle`, no separate sign-up variant — Firebase doesn't distinguish the two for an existing Google identity), so the Merge/Discard prompt is reachable from the Sign Up screen's Google button too, not only Sign In's. Documented inline in `SignUpScreen`'s doc comment rather than special-cased away, since special-casing it would mean silently dropping pending Guest data instead of asking.

Caught in review (Standards axis) and fixed before commit: `AlertDialog`'s `onDismissRequest` (the scrim tap / system Back gesture) wasn't gated by the same `isBusy` flag the dialog's buttons already disable during a Merge/Discard call — a user could dismiss mid-resolve and fire `signOut()` concurrently with a still-running merge/discard, risking both `onResolved` and `onCancelled` firing for the same prompt, or a merge/discard mutating Account data after the app had already navigated away on Cancel. Fixed by having `cancel()` itself check and set `isBusy`, so a dismiss (or a rapid double-tap) while a resolution is in flight is now a no-op.

**Not manually verified — no device or emulator available in this session** (`adb devices` returns empty). Everything above was verified by compiling and reading the traced code paths (confirmed in review: the dialog only renders for a genuine `GuestDataPending` result on both the email and Google paths, and never for `SignedIn`), and the full unit test suite passes, but the ticket's own explicit "Manually verified" checklist item could not be completed here — left unchecked rather than claimed. Whoever has device access should exercise both the email and Google sign-in paths (a device/emulator with existing on-device Guest data, signing into an Account that already exists) before considering this ticket fully done; status set to `ready-for-human` rather than closed out as agent-complete, for that reason.

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fix above came from that review. Spec axis confirmed every other checklist line by tracing the actual code paths and found no gaps or wrong implementations.
