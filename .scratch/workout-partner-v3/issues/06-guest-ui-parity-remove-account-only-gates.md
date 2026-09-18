# 06: Guest UI parity — remove Account-only gates

**What to build:** A Guest can use every feature the app has — Routines, Quick Count, Roster, Weekly Target, Streak, Streak Shields, Progress, reminders — with no "sign up to use this" wall anywhere. Screens that currently branch on a nullable Account id (meaning "locked") instead receive "the Athlete" (Account id or Guest) and just work. The sign-up nudge changes from unlocking-features framing to backup framing.

**Blocked by:** 02 (schema — `guest_profile` needs its new streak/target/notification columns).

**Status:** ready-for-agent

- [ ] The Quick Count tile, Roster screen, overflow menu entries, Settings (Weekly Target, reminders), and Progress all work fully for a Guest — every existing `accountId != null` gate in `MainActivity.kt` and elsewhere is removed for these.
- [ ] A Guest can create a Roster of Tracked Profiles and run Quick Count against them, producing Tallies, without signing up.
- [ ] A Guest sees Tally history per Tracked Profile.
- [ ] A Guest has an adjustable Weekly Target, and a Streak with Streak Shields, backed by the new `guest_profile` columns from ticket 02.
- [ ] A Guest sees their Progress screen (Streak, Personal Bests, history).
- [ ] A Guest can enable the daily reminder.
- [ ] Post-Set prompt copy changes to "Back up your progress"; still shown at most once per Session.
- [ ] Settings gains a "Back up your progress" entry for Guests (this entry surfaces sign-up/sign-in; it does not itself need ticket 09's merge/discard prompt to exist yet — a Guest with no existing Account to sign into just signs up normally).
- [ ] Existing `RosterRepository`/`TallyRepository` tests extended to cover creating and listing Tracked Profiles/Tallies for a Guest (no Account).
