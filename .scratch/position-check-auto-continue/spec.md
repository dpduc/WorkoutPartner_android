Status: ready-for-agent

# Position Check: make "Start anyway" reachable, and the banner honest

## Problem statement

Two issues surfaced live, testing the Position Check on-device (both in
`PositionCheckScreen.kt`/`BeforeYouStartEngine.kt`, `workout-partner-v3`
ticket 12's screen):

1. **"Start anyway" can't be reached by the person it's for.** It only
   renders once `PositionCheckStatus.startAnywayAvailable`
   (`BeforeYouStartEngine.START_ANYWAY_AFTER_SECONDS`, 15 seconds) — by
   which point the Athlete has, by construction, already stepped back far
   enough to fit the body outline (that's the whole point of the Position
   Check). Reaching back to the phone to tap a button on its screen
   contradicts the exact state the button exists to handle: "the checks
   aren't passing and I need to proceed anyway." A companion-assisted setup
   (someone standing near the phone) can reach it; a lone Athlete who has
   correctly followed the on-screen instruction cannot.
2. **The top banner never reflects reality.** "Put your phone down, then
   step back until your whole body fits the outline." is a plain `Text` in
   `PositionCheckScreen.kt` (~line 136), rendered unconditionally for as
   long as the Position Check phase is showing — including once both
   checks (`status.bodyInFrame`, `status.distance == DistanceStatus.OK`)
   are actually passing. `BodyOutline`'s color, just below it in the same
   composable, already computes exactly this pass/fail condition and reacts
   to it (`PASS_COLOR` vs. white) — the banner text sits right next to that
   and ignores it, which reads as the check being stuck even when it
   plainly isn't.

## Root cause

1. `startAnyway()` is a manual-only escape hatch (`BeforeYouStartEngine.kt`):
   nothing calls it except `PositionCheckScreen`'s `Button.onClick`. There
   is no fallback if that tap is physically unreachable.
2. `PositionCheckScreen`'s banner `Text` is unconditional; the pass/fail
   condition it should key off is computed inline, once, only for
   `BodyOutline`'s `color` parameter, and not reused.

## Fix approach

1. **Drop the manual "Start anyway" button entirely** — `startAnyway()`,
   `PositionCheckStatus.startAnywayAvailable`, and the two-threshold design
   originally drafted here (an early "available" point plus a later
   auto-fire backstop) all got removed after a first pass showed the
   in-between button was still confusing on real hardware: the banner could
   read "Looking good — hold still to begin." while the button sat there
   simultaneously implying an override was needed for checks that were
   already passing, and even once restricted to the genuinely-failing case,
   showing a tappable control nobody can reach doesn't help anyone — it
   just delays the inevitable auto-resolution by however long the "give it
   a chance to be tapped" grace window was. `BeforeYouStartEngine.onTick()`
   now has one threshold, `POSITION_CHECK_TIMEOUT_SECONDS` (15s, the same
   value "Start anyway" used to appear at): once reached, with the checks
   still not stably passing, it calls `advance()` on its own — no tap, no
   button, no in-between state to misread.
2. Compute the Position Check's pass/fail condition
   (`status.bodyInFrame && status.distance == DistanceStatus.OK`) once in
   `PositionCheckScreen`, reuse it for both `BodyOutline`'s color (as
   today) and the banner's text/visibility, so the banner actually tracks
   what the outline color already shows.

## Scope

- `BeforeYouStartEngine`: `startAnyway()`/`startAnywayAvailable` removed;
  `onTick()` auto-advances unconditionally at `POSITION_CHECK_TIMEOUT_SECONDS`.
- `PositionCheckScreen`: the "Start anyway" `Button` removed; the banner
  reacts to the same pass/fail condition `BodyOutline` already computes.
- Unit test coverage for the auto-advance threshold in
  `BeforeYouStartEngineTest`, following its existing style (plain
  tick-driven assertions, no Android dependency).

## Out of scope

- Any spoken/TTS announcement of the auto-continue itself — the existing
  Countdown/Ready flow already speaks once Position Check ends
  (`announce_get_ready` etc.), which is enough audible confirmation that
  something happened; adding a new cue here is a separate, smaller product
  decision left for later if it turns out to be needed.
- Redesigning the Position Check's distance thresholds
  (`PositionCheckEvaluator.MIN_SKELETON_HEIGHT_FRACTION`/
  `MAX_SKELETON_HEIGHT_FRACTION`) — already flagged in that object's own
  doc comment as an untuned placeholder; a separate concern from this
  ticket's reachability/honesty fixes.
- The rep-counting side-consistency bug
  (`.scratch/rep-counting-side-consistency/`) — unrelated, tracked
  separately.

## Comments
