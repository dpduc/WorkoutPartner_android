# 07: Activity Level four-tier cards + difficulty tuning

**What to build:** Replace the 3-way Low/Medium/High segmented control with four selectable cards (Sedentary, Lightly Active, Active, Very Active — each with a plain-language description) in both Profile setup and Settings, and make the answer actually affect Routine difficulty.

**Blocked by:** 02 (schema — the `ActivityLevel` enum and value remap land there).

**Status:** done

- [x] Profile setup shows four cards: Sedentary ("I rarely exercise"), Lightly Active ("a few times a week"), Active ("frequently active"), Very Active ("working out is my daily passion").
- [x] Settings offers the same four options to change Activity Level later.
- [x] An existing Athlete's old Low/Medium/High answer (already remapped by ticket 02's migration) displays correctly as one of the four tiers with no re-prompt.
- [x] `BodyStats` gains `activityLevel`. `RoutineDifficulty.compute` adds an activity score to the existing BMI + age-band score: Sedentary −1, Lightly Active 0, Active 0, Very Active +1. Existing Easy/Standard/Challenging thresholds are unchanged. Missing Activity Level scores 0 (same as today).
- [x] `RoutineDifficultyTest` covers each Activity Level's effect, including tipping across tier boundaries, and confirms Lightly Active/Active behave exactly as the old default did (nothing changes for the middle of the range) and missing-Activity-Level behavior is unchanged.

## Comments

Implemented as spec'd. New `ActivityLevelPicker` composable (`app/src/main/kotlin/com/workoutpartner/app/ui/components/ActivityLevelPicker.kt`) renders the four cards and is shared by `ProfileSetupScreen` and `SettingsScreen`, replacing each screen's own `SingleChoiceSegmentedButtonRow` — both screens previously duplicated the label-formatting logic too (`ProfileSetupScreen` had its own `displayLabel()`, `SettingsScreen` derived a label from the enum name directly, which capitalized differently: "Lightly active" vs "Lightly Active"). Centralizing picks one consistent capitalization ("Lightly Active") for both screens, a small user-visible text change on Settings, not a functional one.

`BodyStats` gained `activityLevel: ActivityLevel? = null` (defaulted so the existing `RoutineDifficultyTest` fixtures without it keep compiling unchanged, which doubles as this ticket's "missing Activity Level" regression check). `RoutineDifficulty.compute` adds the activity score into the existing BMI+age total before applying the unchanged tier thresholds.

Caught in review (Standards axis) and fixed before commit: `ActivityLevelPicker`'s doc comment claimed it replaced a "3-way Low/Medium/High" control, which was wrong — that 3-tier scale was already gone (remapped to the current 4-tier `ActivityLevel` by ticket 02's migration); what this ticket actually replaced was a 4-way segmented-button placeholder that listed the tiers but had no room for a description. Also made the picker's `displayLabel()` extension `private` (it had no external callers, only `description()` beside it in the same file already was).

Reviewed via `/code-review` (Standards + Spec axes) before commit; the fix above came from that review. Spec axis found no missing, wrong, or extra behavior.
