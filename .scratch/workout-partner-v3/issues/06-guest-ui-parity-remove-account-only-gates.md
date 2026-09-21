# 06: Guest UI parity — remove Account-only gates

**What to build:** A Guest can use every feature the app has — Routines, Quick Count, Roster, Weekly Target, Streak, Streak Shields, Progress, reminders — with no "sign up to use this" wall anywhere. Screens that currently branch on a nullable Account id (meaning "locked") instead receive "the Athlete" (Account id or Guest) and just work. The sign-up nudge changes from unlocking-features framing to backup framing.

**Blocked by:** 02 (schema — `guest_profile` needs its new streak/target/notification columns).

**Status:** done

- [x] The Quick Count tile, Roster screen, overflow menu entries, Settings (Weekly Target, reminders), and Progress all work fully for a Guest — every existing `accountId != null` gate in `MainActivity.kt` and elsewhere is removed for these.
- [x] A Guest can create a Roster of Tracked Profiles and run Quick Count against them, producing Tallies, without signing up.
- [x] A Guest sees Tally history per Tracked Profile.
- [x] A Guest has an adjustable Weekly Target, and a Streak with Streak Shields, backed by the new `guest_profile` columns from ticket 02.
- [x] A Guest sees their Progress screen (Streak, Personal Bests, history).
- [x] A Guest can enable the daily reminder.
- [x] Post-Set prompt copy changes to "Back up your progress"; still shown at most once per Session.
- [x] Settings gains a "Back up your progress" entry for Guests (this entry surfaces sign-up/sign-in; it does not itself need ticket 09's merge/discard prompt to exist yet — a Guest with no existing Account to sign into just signs up normally).
- [x] Existing `RosterRepository`/`TallyRepository` tests extended to cover creating and listing Tracked Profiles/Tallies for a Guest (no Account).

## Comments

Implemented as spec'd. The key move: `TrackedProfileEntity.accountId` was already widened to nullable by ticket 02, so `RosterRepository`/`RosterViewModel`/`RosterScreen` just needed their `accountId: String` params widened to `String?` and `TrackedProfileDao`'s query switched from `= :accountId` to `IS :accountId` (SQL's `= NULL` never matches, so a single query needs to handle both cases via `IS`). The same `IS` fix went into `SetDao.getForAccount` so `SetRepository.getSetsForAccount`/`AccountRepository.recomputeStreak` work for a Guest's Set history too.

`AccountRepository.updateWeeklyTarget`/`updateNotificationsEnabled`/`recomputeStreak` now branch on `accountId == null` to read/write the `GuestProfileEntity` singleton row instead of an `AccountEntity`. `SetRepository.recordSet`'s streak-refresh gate (`if (accountId != null)`) is gone — a Guest's Sessions now get the same post-Set streak recompute an Account's do.

Caught in review (Standards axis) and fixed before commit: `GuestProfileEntity`'s own doc comment had already warned that `AccountRepository.saveGuestProfile` blindly `upsert`s a fresh entity, which would silently reset Streak/Shields/Weekly Target/notifications back to defaults the moment those became real, accumulated state instead of always-default placeholders. Fixed by reading the existing row and `.copy`-ing only the body-stats fields onto it; covered by a new test (`saveGuestProfile does not stomp Streak state already accumulated on the Guest row`).

Also caught in review: `AccountRepository.claimGuestData` (sign-up's migration primitive) still only copies body-stats onto the new Account and discards the Guest's Weekly Target/Streak/Shields/notification preference — harmless before this ticket (those columns were always unused defaults on the Guest row) but now a real, if narrow, data-loss gap for a Guest who customized their target before signing up. Left unfixed and explicitly re-flagged in that method's doc comment rather than patched here: ticket 05 owns the actual sign-up/merge contract for this widened Guest state (including Merge's "Account's existing target wins" rule, which a plain copy here would get wrong for that path), so fixing it now would risk conflicting with ticket 05's own design.

Settings' "Back up your progress" entry surfaces both Sign up and Sign in (per the ticket's own "surfaces sign-up/sign-in" line), routing to the existing `AppScreen.SignUp`/`AppScreen.SignIn` screens — no new screen needed. While touching `SettingsScreen`, also widened its now-always-non-null `authRepository: AuthRepository?` parameter to `AuthRepository` (ticket 15 already made `container.authRepository` unconditional, so the nullable gate was dead code).

Reviewed via `/code-review` (Standards + Spec axes) before commit; the two fixes above came from that review.
