# 09: Merge-or-discard prompt on sign-in

**What to build:** Wire ticket 05's data-layer contract into real UI. Signing in (email or Google) to an existing Account while holding on-device Guest data prompts the Athlete to Merge or Discard it; a Guest with no on-device data signs in with no prompt at all.

**Blocked by:** 05.

**Status:** ready-for-agent

- [ ] Sign-in with pending Guest data shows a prompt offering Merge or Discard (not shown at all when there's nothing pending).
- [ ] Choosing Merge shows a short confirmation of what was added afterward (e.g. "Added 12 workouts and 3 Tracked Profiles to this account").
- [ ] Choosing Discard first shows exactly what will be lost, requiring confirmation before anything is deleted.
- [ ] Backing out of the prompt (neither Merge nor Discard chosen) cancels the sign-in — the Athlete is signed back out and their Guest data is untouched and still unclaimed.
- [ ] A failed Merge or Discard leaves Guest data intact and still unclaimed, with a retry path (surfacing ticket 05's transactional failure behavior to the user).
- [ ] Manually verified for both the email sign-in and Google sign-in paths.
