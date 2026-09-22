# 01: Position Check pass/fail color and a real audio cooldown

**What to build:** Add an orange-red `WARNING_COLOR` for the Position
Check's failing state (green already exists for passing), and give
`PromptSpeaker` a 3-second cooldown so the same line can't be voiced twice
in quick succession. See `../spec.md` for the full problem statement and
the design decisions already made (where the cooldown lives, why, and what
it doesn't touch).

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] `PositionCheckScreen.kt` gains a `WARNING_COLOR` constant (orange-red)
      alongside the existing `PASS_COLOR`.
- [ ] `BodyOutline`'s color is `PASS_COLOR` when both checks pass (unchanged),
      `WARNING_COLOR` otherwise (was plain `Color.White`).
- [ ] Each `Indicator`'s color is `PASS_COLOR` when its own check passes
      (unchanged), `WARNING_COLOR` when it doesn't (was
      `MaterialTheme.colorScheme.onSurfaceVariant`).
- [ ] A small pure function/class (e.g. `SpeechCooldown` or similar) decides,
      given a line's text and when it was last actually spoken, whether it
      should be voiced now — unit-tested directly, independent of
      `TextToSpeech`.
- [ ] `PromptSpeaker.speakNow` consults it for `NORMAL`/`HIGH` priority lines
      before calling `tts.speak(...)`: a repeat of the same text within 3
      seconds of when it was last actually spoken is suppressed instead of
      queued. `CRITICAL` keeps flushing/interrupting unconditionally; `LOW`
      keeps its existing "drop if already speaking" rule, untouched by this
      cooldown.
- [ ] The cooldown clock only advances on an actual `speakNow` call (i.e. on
      lines that passed the `prefs.spokenPromptsEnabled` gate and, for
      `LOW`, weren't already dropped) — a suppressed or gated line doesn't
      itself reset the cooldown window.
- [ ] `PromptSpeaker`'s existing behavior is otherwise unchanged: the
      `spokenPromptsEnabled` gate, pending-lines-before-ready queueing, and
      `shutdown()` all still work exactly as before.
- [ ] Unit tests for the new cooldown seam: same text spoken twice inside
      the window is suppressed the second time; the same text spoken again
      after the window has elapsed is voiced; two *different* texts spoken
      back-to-back are both voiced regardless of timing.
- [ ] Typecheck and the full test suite pass.
- [ ] Verified on the real device: the Position Check's `BodyOutline` and
      indicators turn orange-red while a check is failing and green once it
      passes, and repeating "step back"/"move closer" guidance is
      noticeably less rapid-fire than before — no more than one voicing of
      the same line per 3-second window, confirmed by ear against a timer
      or the debug pose overlay's frame log.

## Comments
