Status: ready-for-agent

# Position Check cue clarity: pass/fail color and audio cooldown

## Problem statement

Watching the Position Check run on the real device surfaced two clarity gaps
in `PositionCheckScreen.kt`/`BeforeYouStartEngine.kt` (`workout-partner-v3`
ticket 12):

1. **Fail state has no distinct color.** A passing check already turns green
   (`PASS_COLOR`, both `BodyOutline` and each `Indicator`), but a failing one
   just falls back to plain white/gray — the same as "not yet determined."
   There's no visual cue that pairs with the spoken guidance ("step back",
   "move closer") to make it obvious *something needs fixing right now*.
2. **The "step back" cue is heard too close together.** The engine already
   has a repeat interval (`DISTANCE_CUE_REPEAT_SECONDS = 5`, explicitly
   commented "placeholder, tune on device") that re-queues an unresolved
   distance cue once it's been that long — but `PromptSpeaker.speak()` itself
   has no cooldown of its own: every call queues via `TextToSpeech.QUEUE_ADD`
   regardless of how recently the same line was last spoken. Anything that
   causes the same cue to be requested again sooner than it fully finishes
   playing (frame-to-frame flicker near a threshold, the engine's own
   dedup logic not catching every path) stacks utterances back-to-back with
   no gap, which is what "played continuously" describes.

## What to build

1. **Green on pass, unchanged** — already correct
   (`PositionCheckScreen.kt`'s `PASS_COLOR`, `Color(0xFF2E7D32)`, applied to
   both `BodyOutline` and each `Indicator` when its check passes). No change
   needed; the ticket below just confirms this explicitly rather than
   silently assuming it.
2. **Orange-red on fail** — a new `WARNING_COLOR` replaces the current
   plain-white/`onSurfaceVariant` fallback for `BodyOutline` and each
   `Indicator` when its check is failing, so the failing state reads as
   "needs attention" rather than "neutral/not started," and pairs visually
   with whichever spoken cue is firing.
3. **A real cooldown on repeated audio** — `PromptSpeaker` (the actual
   audio-playing function, not the engine that decides *what* to say) tracks
   when each distinct line was last actually spoken and suppresses a repeat
   of the *same* line within a fixed cooldown window (3 seconds), regardless
   of how many times a caller asks for it in that window. This sits below
   (and is independent of) `BeforeYouStartEngine`'s own
   `DISTANCE_CUE_REPEAT_SECONDS` dedup — it's a safety net at the layer that
   actually produces sound, not a replacement for the engine's own timing.

## Design decisions (judgement calls, not left open)

- **Where the cooldown lives**: in `PromptSpeaker`, not
  `BeforeYouStartEngine`. The engine's job is deciding *what* to say and
  *when a condition is still true*; whether a specific utterance actually
  gets voiced (given what was just spoken) is a speech-layer concern —
  matches this class's own doc comment ("every line is spoken only while
  [prefs]' spoken-prompts setting is on... a CRITICAL line interrupts...
  LOW is dropped if already speaking" — this is one more rule of the same
  kind, not a new mechanism).
- **Scope of the cooldown**: keyed by the announcement's *text*, not global
  — two different lines back-to-back should still both play; only the
  *same* line repeating too soon is suppressed. Applies to `NORMAL`/`HIGH`
  priority (the common case, including Position Check's cues); `CRITICAL`
  keeps interrupting/flushing regardless (it exists specifically to cut
  through), and `LOW`'s existing "drop if already speaking" rule is a
  different, already-adequate throttle — don't double up on it.
- **Pull the cooldown decision into a pure, testable seam.** "Given this
  line and when it was last spoken, should it be voiced now" is plain logic
  with no `TextToSpeech` dependency — pull it into a small function/class
  `PromptSpeaker` calls, the same "pure logic behind a framework adapter"
  shape `BeforeYouStartEngine` and `TrackingStateMachine` already use
  elsewhere in this codebase, so it's unit-tested even though
  `PromptSpeaker` itself isn't.
- **3-second cooldown is a fixed constant for now**, not configurable — same
  "tune later if it's wrong" spirit as `DISTANCE_CUE_REPEAT_SECONDS`'s own
  "placeholder" comment. Don't over-build a settings surface nobody asked
  for.

## Out of scope

- Changing `DISTANCE_CUE_REPEAT_SECONDS` itself (still 5s) — the two
  mechanisms address different problems (when the engine *re-offers* a cue,
  vs. whether the speaker actually *voices* a too-soon repeat) and aren't in
  tension; only add the speaker-side cooldown, don't retune the engine's.
- Any change to `BODY_DETECTED`'s own once-per-check announce logic — it's
  already a one-shot per Position Check (`bodyDetectedAnnounced`), not a
  repeating cue, so it has nothing to cool down.
- Color changes anywhere outside `PositionCheckScreen.kt`'s `BodyOutline`/
  `Indicator` (e.g. no change to `SessionScreens.kt`'s "Lost track of you"
  card, which is a different, already-distinct warning treatment).
