# 01: Random name suggestion + age/height sliders on Profile Setup

**What to build:** Bring `ProfileSetupScreen` in line with `docs/onboarding_ux_script.md` (v3's own cited design source, never implemented): a shuffleable random name suggestion, and age/height sliders in place of their text fields.

**Blocked by:** None directly, but the spec's open questions (language, haptics, weight, validation ranges, whether `Settings` also changes) need answering first — see `../spec.md`. Filed as `needs-triage` for that reason, not because the work itself is unclear.

**Status:** needs-triage

- [ ] A pure, unit-tested name-suggestion source (adjective + noun, e.g. "Mighty Banana") that `ProfileSetupScreen` shows as the name field's placeholder/default, with a shuffle control next to it.
- [ ] Typing into the name field clears the suggestion (the Athlete's own input always wins, never silently overwritten).
- [ ] Age input becomes a slider, range 13–100, default 25.
- [ ] Height input becomes a slider, range 120–220 cm, default 170.
- [ ] Weight stays a text field unless triage decides otherwise (see spec's open questions).
- [ ] `ProfileSetupScreen`'s existing validation (currently `> 0` for age/height/weight) is reconciled with the sliders' fixed range — decide in triage whether the range *is* the validation.

## Comments

Filed 2026-09-22 after the user asked whether this had already shipped. Checked `ProfileSetupScreen.kt` directly: it's four `OutlinedTextField`s plus the Activity Level cards, no slider, no name suggestion. No `workout-partner-v3` ticket names this screen at all, despite the spec listing `onboarding_ux_script.md` as design source material alongside `before-you-start-system.md` — only the latter got built out. Not a regression, just unpicked-up scope; `workout-partner-v3` is fully `done`, so this got its own feature directory per `docs/agents/issue-tracker.md` rather than reopening it.
