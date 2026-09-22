# 14: Quick Count Position Check

**What to build:** Run `BeforeYouStartEngine` for Quick Count too, but configured to start directly at the Position Check phase and skip Overview, Form Guides, and Countdown — keeping Quick Count quick while still confirming the tracked person is properly framed.

**Blocked by:** 12.

**Status:** done

- [x] Starting Quick Count runs the Position Check (ticket 12's same checks and spoken guidance) before counting begins.
- [x] No Workout Overview, Form Guides, or Countdown are shown for Quick Count.
- [x] "Start anyway" is available in Quick Count's Position Check exactly as it is in a Session's.
- [x] `BeforeYouStartEngineTest` covers the Quick Count configuration: starts at Position Check and ends without a countdown.

## Comments

`BeforeYouStartEngine` gained a `quickCount` flag (default `false`, set via the new `forQuickCount()` companion factory rather than passed directly) that changes two things and nothing else: the engine starts at `PositionCheck` instead of `Overview` (an `init` block computes that initial phase), and `advance()`'s `PositionCheck` branch goes straight to `Ready` instead of `Countdown`. `onPoseFrame`, `onTick`, `startAnyway` and the cue logic have no `quickCount` branches at all — the Position Check itself is byte-identical to ticket 12's.

`QuickCountScreen` (new, in `QuickCountScreens.kt`) wraps `QuickCountRunScreen`: while the engine is in `PositionCheck` it renders ticket 12's own `PositionCheckScreen` unchanged, with its own tracker instance (stopped when that phase ends, same one-tracker-per-phase split `BeforeYouStartScreen` uses ahead of a Session); once the engine reaches `Ready`, it renders `QuickCountRunScreen`, which creates its own fresh tracker as before. `MainActivity`'s `AppScreen.QuickCountRun` case now calls this wrapper instead of `QuickCountRunScreen` directly; nothing else in its navigation changed.

Reviewed via `/code-review` (Standards + Spec axes) before commit. Spec: no findings. Standards: one judgement call taken — the engine's constructor started as `private constructor(unseenGuides, quickCount) { public constructor(unseenGuides) : this(...) }` plus the companion factory, flagged as more ceremony than the codebase's other engines use for a defaulted flag; simplified to a single constructor with `quickCount: Boolean = false`, keeping `forQuickCount()` as the documented, preferred way to set it. Two judgement calls left as-is: `QuickCountScreen` and `BeforeYouStartScreen` both `remember` a `PromptSpeaker` and wire `PositionCheckScreen` the same way — flagged as minor Duplicated Code, not worth a shared helper for just these two call sites; and the `init` block's "must come after every property above" comment was checked and is a real constraint (`currentStatus()` dereferences `lastEvaluation`, initialized above it), not a non-problem.
