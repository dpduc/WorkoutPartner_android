# 10: Form Guides

**What to build:** Extend `BeforeYouStartEngine`'s Form Guides phase with real content. An Athlete meeting an Exercise (or Step Jack) for the first time in a Routine sees its Form Guide before the Session; guides already seen are skipped automatically; "Review form" on the Overview (stubbed in ticket 03) reopens them anytime.

**Blocked by:** 03.

**Status:** done

- [x] Only unseen Exercises/Variants in the current Routine show a Form Guide automatically; if every one has already been seen, this phase is skipped entirely and the engine advances straight to Position Check.
- [x] Each Form Guide shows: a picture, the starting position, correct-form cues, common mistakes, and what the tracker checks.
- [x] Guides are swipeable, with a "2 of 4"-style position indicator.
- [x] Step Jack has its own Form Guide, distinct from Jumping Jack's.
- [x] A Form Guide is marked seen once viewed, and won't be forced on the Athlete again.
- [x] Seen-Form-Guide state is a device-local preference (not Room, not synced), keyed per Exercise/Variant; it survives a Guest's sign-up or merge (ticket 05/09) unchanged, since it was never tied to ownership in the first place.
- [x] "Review form" on the Workout Overview opens the full set of Form Guides for the Routine's Exercises regardless of seen-state.
- [x] Form Guide content (display name, starting position, cues, mistakes, tracker-check description, static image) is bundled static data per Exercise/Variant, all six guides (five Exercises + Step Jack) present, in string resources.
- [x] `BeforeYouStartEngineTest` covers: only unseen guides appear; all-seen skips straight to Position Check.

## Comments

Implemented as spec'd. `BeforeYouStartEngine` now takes the Routine's unseen `TrackedExercise`s (reusing ticket 08's `(Exercise, ExerciseVariant?)` identity type rather than inventing a second one) and `advance()` decides, from Overview, whether to enter `FormGuides(guides)` or skip straight to `PositionCheck` — the gating condition ticket 03 left for this ticket to add. A new `BeforeYouStartScreen` owns that engine instance and switches between `WorkoutOverviewScreen` and the new `FormGuidesScreen` based on its phase; `WorkoutOverviewScreen` itself stays purely presentational, unaware the engine exists (ticket 03's original separation of concerns, preserved). `MainActivity` now renders `BeforeYouStartScreen` instead of `WorkoutOverviewScreen` directly for `AppScreen.WorkoutOverview` — no new `AppScreen` case was needed, since the whole Before-You-Start flow (Overview, and now Form Guides) lives inside that one screen entry, matching ticket 03's "models the full phase sequence... even though only Overview is wired to real UI yet" framing.

`FormGuidesScreen` is one composable used for both cases the ticket describes — the automatic pre-Session gate (only unseen guides, skipped when empty) and "Review form" (every guide, regardless of seen-state) — told apart only by what the caller passes in; it marks each guide seen the moment its page is displayed, not only on completion, so swiping partway through and backing out still "counts" for the pages actually viewed. Seen-state lives in a new `FormGuidePrefs` (plain `SharedPreferences`, keyed by Exercise+Variant, never by accountId — the same device-local pattern `OnboardingPrefs` already established), so it's untouched by a Guest's sign-up or merge.

Form Guide content (name, starting position, cues, mistakes, tracker-check description) for all six guides — the five Exercises plus Step Jack — is bundled in `strings.xml` per the ticket's explicit ask, a deliberate departure from this app's usual inline-Compose-literal copy convention, wired through a small `FormGuideResources`/`FormGuides` mapping keyed by `TrackedExercise`.

One disclosed shortcut: all six guides currently share one placeholder vector drawable (a generic silhouette) rather than distinct photography per Exercise/Variant — real form photography isn't something this session can produce. The image is already keyed per Exercise/Variant in `FormGuideResources`, so swapping in real art later is a one-line resource-id change per entry, not a redesign; flagged explicitly in review (Spec axis) as an acceptable, disclosed placeholder rather than a checklist gap.

No `RoutineStepEntity`/schema changes and no Step Jack selection UI were added — `RoutineWithSteps.trackedExercises()` is hardcoded to variant-less `TrackedExercise`s with a doc comment explaining why (a Routine's steps carry no Variant of their own until ticket 11 picks one per-Session). Flagged in review (Spec axis) as explicit, disclosed restraint against ticket 11 scope creep, not an oversight.

Reviewed via `/code-review` (Standards + Spec axes) before commit. Standards found no hard violations — only mild, defensible judgement calls (e.g. `FormGuidePrefs`'s function-pair API differing in shape from `OnboardingPrefs`'s `var` properties, justified by its per-key parameterization). Spec found no missing/partial checklist lines and no scope creep, calling out the ticket-11 restraint above as a positive.
