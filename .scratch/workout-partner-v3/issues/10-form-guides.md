# 10: Form Guides

**What to build:** Extend `BeforeYouStartEngine`'s Form Guides phase with real content. An Athlete meeting an Exercise (or Step Jack) for the first time in a Routine sees its Form Guide before the Session; guides already seen are skipped automatically; "Review form" on the Overview (stubbed in ticket 03) reopens them anytime.

**Blocked by:** 03.

**Status:** ready-for-agent

- [ ] Only unseen Exercises/Variants in the current Routine show a Form Guide automatically; if every one has already been seen, this phase is skipped entirely and the engine advances straight to Position Check.
- [ ] Each Form Guide shows: a picture, the starting position, correct-form cues, common mistakes, and what the tracker checks.
- [ ] Guides are swipeable, with a "2 of 4"-style position indicator.
- [ ] Step Jack has its own Form Guide, distinct from Jumping Jack's.
- [ ] A Form Guide is marked seen once viewed, and won't be forced on the Athlete again.
- [ ] Seen-Form-Guide state is a device-local preference (not Room, not synced), keyed per Exercise/Variant; it survives a Guest's sign-up or merge (ticket 05/09) unchanged, since it was never tied to ownership in the first place.
- [ ] "Review form" on the Workout Overview opens the full set of Form Guides for the Routine's Exercises regardless of seen-state.
- [ ] Form Guide content (display name, starting position, cues, mistakes, tracker-check description, static image) is bundled static data per Exercise/Variant, all six guides (five Exercises + Step Jack) present, in string resources.
- [ ] `BeforeYouStartEngineTest` covers: only unseen guides appear; all-seen skips straight to Position Check.
