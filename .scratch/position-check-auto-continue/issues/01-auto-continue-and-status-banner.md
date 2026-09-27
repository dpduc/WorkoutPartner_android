# 01: Auto-continue past Position Check, and a status-aware banner

**What to build:** `BeforeYouStartEngine` resolves Position Check on its own
after a fixed timeout — no tap, no button — and `PositionCheckScreen`'s top
banner reflects whether the checks are actually passing instead of always
showing the same "step back" instruction. See `../spec.md` for the
reachability problem, the root cause, and why a manual "Start anyway"
button (even a delayed/backstopped one) didn't hold up.

**Blocked by:** None.

**Status:** done

- [x] `BeforeYouStartEngine.startAnyway()` and
      `PositionCheckStatus.startAnywayAvailable` removed — no manual
      override exists anymore, tap-gated or otherwise.
- [x] `onTick()`'s Position Check branch auto-advances (`advance()`) once
      `secondsElapsed` reaches `POSITION_CHECK_TIMEOUT_SECONDS` (15s, the
      same value the old button used to appear at), if the phase hasn't
      already left Position Check via the normal stability pass.
- [x] `PositionCheckScreen`'s "Start anyway" `Button` (and the now-unused
      `Button` import) removed.
- [x] `PositionCheckScreen` computes
      `status.bodyInFrame && status.distance == DistanceStatus.OK` once and
      reuses it for both `BodyOutline`'s existing color logic and the top
      banner — the banner shows "Looking good — hold still to begin." once
      passing instead of the fixed "step back" instruction, and swaps back
      if it stops passing.
- [x] `BeforeYouStartEngineTest` updated: every test that referenced
      `startAnyway()`/`startAnywayAvailable` either removed (the concept no
      longer exists) or replaced with a test of the unconditional timeout
      (fires at `POSITION_CHECK_TIMEOUT_SECONDS`, checks still failing;
      `forQuickCount` variant reaches `Ready` the same way).
- [x] Typecheck and the full test suite pass. `PositionCheckScreen`'s
      banner change is Compose UI, same "not unit-tested, thin framework
      adapter" caveat the rest of that screen already carries — verified
      on-device instead.
- [x] Verified on the real device: Position Check resolves within the
      timeout with no tap ever sent, and the banner text changes once the
      checks actually pass.

## Comments

First pass kept "Start anyway" as a button (with a later, unconditional
auto-fire backstop) rather than removing it outright, reasoning a
companion-assisted setup near the phone might still want the early manual
option. Verified working on-device (auto-advance confirmed via
`side_squat.mp4` reaching the Run screen with zero taps sent, banner
confirmed via `squat.mp4` showing "Looking good — hold still to begin."
with both checks green) — see this ticket's prior commit for those
screenshots' description.

The owner's live feedback after that pass: showing "Start anyway" *at all*
is still confusing and still doesn't help anyone reach it — worse, it could
render at the same moment as "Looking good," which reads as contradictory
(why would checks that are passing need an override?). Asked directly
whether to keep a gated button or drop it outright; the owner chose to drop
it. Reworked to the single-timeout design described above: no button,
nothing to reach, nothing to misread.

Re-verified on-device after the rework (Samsung SM-S938B, same debug
video-replay pipeline):
- `side_squat.mp4`: Position Check ran past the 15s timeout with zero taps
  and zero button ever rendered; landed on the Quick Count Run screen
  tracking reps on its own.
- `squat.mp4`: banner read "Looking good — hold still to begin." the moment
  both checks passed, no button present anywhere in that or any other
  observed frame.

`/code-review` outcome (Standards + Spec sub-agents, fixed point `1a2cf94`,
run against the final single-timeout diff): Spec axis found nothing —
every checklist item confirmed genuinely implemented, no scope creep.
Standards axis found one real nit, fixed: the test
`a failing check keeps the phase from advancing however long it lasts` kept
its old name even though this diff makes that name's claim false (a
failing check now only holds the phase for up to
`POSITION_CHECK_TIMEOUT_SECONDS`, not indefinitely) — renamed to
`...before the timeout`. A minor `when`-vs-`if` duplication nit in
`onTick()` was left as-is per the reviewer's own read (current form is more
readable given the inline comment distinguishing why each branch fires).
