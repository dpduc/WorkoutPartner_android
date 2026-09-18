# 07: Activity Level four-tier cards + difficulty tuning

**What to build:** Replace the 3-way Low/Medium/High segmented control with four selectable cards (Sedentary, Lightly Active, Active, Very Active — each with a plain-language description) in both Profile setup and Settings, and make the answer actually affect Routine difficulty.

**Blocked by:** 02 (schema — the `ActivityLevel` enum and value remap land there).

**Status:** ready-for-agent

- [ ] Profile setup shows four cards: Sedentary ("I rarely exercise"), Lightly Active ("a few times a week"), Active ("frequently active"), Very Active ("working out is my daily passion").
- [ ] Settings offers the same four options to change Activity Level later.
- [ ] An existing Athlete's old Low/Medium/High answer (already remapped by ticket 02's migration) displays correctly as one of the four tiers with no re-prompt.
- [ ] `BodyStats` gains `activityLevel`. `RoutineDifficulty.compute` adds an activity score to the existing BMI + age-band score: Sedentary −1, Lightly Active 0, Active 0, Very Active +1. Existing Easy/Standard/Challenging thresholds are unchanged. Missing Activity Level scores 0 (same as today).
- [ ] `RoutineDifficultyTest` covers each Activity Level's effect, including tipping across tier boundaries, and confirms Lightly Active/Active behave exactly as the old default did (nothing changes for the middle of the range) and missing-Activity-Level behavior is unchanged.
