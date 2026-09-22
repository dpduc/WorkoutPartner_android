Status: needs-triage

# Onboarding UX refinements — name suggestion, sliders

## Problem statement

`docs/onboarding_ux_script.md` was cited as `workout-partner-v3`'s design
source material for its Before You Start work, alongside
`docs/before-you-start-system.md`. Before You Start's half was built; this
document's own subject — the Profile Setup screen an Athlete fills in once,
during onboarding (`workout-partner-v2` ticket 01) — never got a ticket. No
`workout-partner-v3` ticket names, blocks, or defers it; it was simply not
picked up. `ProfileSetupScreen.kt` today is four plain `OutlinedTextField`s
(name, age, height, weight) plus the Activity Level cards
(`workout-partner-v3` ticket 07) — checked directly against the source on
2026-09-22.

The design doc's three asks are unbuilt:
1. A random, upbeat placeholder name (`[positive adjective] + [playful
   noun]`, e.g. "Mighty Banana") with a shuffle control, used as-is if the
   Athlete doesn't want to type one.
2. An age slider/scroll picker, range 13–100, default 25.
3. A height slider, range 120–220 cm, default 170, with haptic feedback per
   step.

The doc doesn't ask for a weight control; `ProfileSetupScreen` keeps weight
as a text field.

## Why this is its own spec, not a `workout-partner-v3` ticket

`workout-partner-v3` (`.scratch/workout-partner-v3/spec.md`) is fully shipped
(14/14 tickets `done`). Per `docs/agents/issue-tracker.md`, new work gets its
own feature directory rather than reopening a closed one.

## Open questions (why this is `needs-triage`, not `ready-for-agent`)

- **Language.** The source doc is Vietnamese throughout — the placeholder
  name formula, its example names, and the screen copy. `workout-partner-v3`
  settled on English-only for every spoken/written string this version
  (spec.md, "Out of Scope": "Vietnamese or any non-English speech/text").
  Does that carry over here, or is onboarding meant to ship Vietnamese
  strings now? If English: who supplies the adjective/noun word lists and
  the example names (this doc's `Mighty Banana`, `Swift Potato` etc. are
  Vietnamese-flavoured examples, not a ready English list)?
- **Haptics.** `workout-partner-v3`'s spec also put haptics out of scope
  ("Haptics and colour-coded full-screen borders") for the Session screen
  specifically — does that reasoning (no ticket built a haptics wrapper
  anywhere in the app yet) extend to this slider's "haptic feedback when
  sliding" ask, or is a single `HapticFeedback.performHapticFeedback` call
  fine as a one-off?
- **Weight.** Add a slider/stepper for consistency with age/height, or leave
  it as a text field (no control existed for it in the source doc, whichever
  was probably reflecting a genuine choice to leave decimals to typing)?
- **Validation ranges.** Should the age/height *sliders'* range (13–100,
  120–220) become the actual validation bounds `ProfileSetupScreen` enforces
  (today it only checks `> 0`), or just the slider's travel, with typed
  edge-values (if a text-entry fallback is kept) still accepted beyond it?
- **Component reuse.** `SettingsScreen` reuses `ProfileSetupScreen`'s
  `ActivityLevelPicker` for changing Activity Level later. Do the age/height
  sliders and name shuffle belong there too (a returning Athlete editing
  their profile), or is this one-time-onboarding only, leaving `Settings`
  on text fields?

## Suggested scope once triaged

- `ProfileSetupScreen.kt`: replace the age and height `OutlinedTextField`s
  with sliders (`androidx.compose.material3.Slider`), defaulting to 25 and
  170; keep the underlying `Int` fields `ProfileSetupScreen`'s `onSubmit`
  already sends.
- A small `RandomNameSuggestion` (or similar) pure function/object in the
  `onboarding` package, list-backed, with a shuffle affordance next to the
  name field; typing in the field clears the suggestion, per the source
  doc's own interaction note ("if the user starts typing, the default
  suggestion disappears").
- Unit-test the name suggestion generator (deterministic given a seed/index)
  the same way other pure logic in this codebase is tested — no Compose
  test needed for the slider/text-field swap itself, consistent with this
  spec's UI-is-a-thin-adapter precedent elsewhere.

## Out of scope (until triage answers the questions above)

- Vietnamese strings.
- Haptic feedback.
- A weight slider.
- Changing `Settings`' editing flow.
