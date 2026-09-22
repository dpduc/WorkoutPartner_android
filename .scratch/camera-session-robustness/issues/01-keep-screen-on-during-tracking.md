# 01: Keep the screen on during camera-active screens

**What to build:** Apply `Modifier.keepScreenOn` to every screen that has a
live `PoseTracker` running against the real camera, so the display doesn't
time out mid-Session/Quick Count/Position Check the way it would on any other
screen. See `../spec.md` for why this doesn't need any manual flag
bookkeeping (it's scoped to composition automatically) and why the app
backgrounding is already handled for free.

**Blocked by:** None.

**Status:** done

- [x] The Position Check screen (`BeforeYouStartScreen.kt`'s
      `PositionCheckScreen`, used by both a Session's own Before You Start
      flow and Quick Count's ticket-14 wrapper) keeps the screen on for as
      long as it's shown.
- [x] A Session's Set-tracking screen(s) in `SessionScreens.kt` keep the
      screen on for as long as a Set is actively tracking.
- [x] `QuickCountScreens.kt`'s `QuickCountRunScreen` keeps the screen on for
      as long as it's actively tracking.
- [x] Any screen that stops actively tracking (Set finished/paused to rest,
      Quick Count stopped, navigating away entirely) lets the screen return
      to its normal timeout behavior — verify this is automatic once the
      modifier's composable leaves composition/is no longer part of the
      active screen, rather than something that needs an explicit "turn it
      back off" call.
- [x] `VideoPoseTracker`-backed debug screens are **not** required to keep
      the screen on (no real camera at risk there) — fine either way, just
      not a requirement to add or avoid.
- [x] Typecheck and the full test suite pass. This is a thin Compose
      modifier addition with no new business logic — no new unit tests are
      expected; note in Comments if a Compose UI test seam already exists
      for these screens and is extended, otherwise this stays
      manually/on-device verified like the rest of this module's UI layer.
- [x] Verified on the real device: screen does not dim/lock during an
      idle-handed Position Check/Set/Quick Count run past the device's
      normal timeout, and does return to normal timeout behavior once
      tracking stops.

## Comments

Implemented with `Modifier.keepScreenOn()` (`androidx.compose.ui`, stable
since 1.9.0-alpha01 — confirmed by actually compiling against this repo's
pinned Compose UI 1.12.1, not assumed from the BOM) on the tracking-scoped
root `Box` in all three screens: `PositionCheckScreen`, `SessionScreens.kt`'s
`TrackingContent` (only rendered during `SessionPhase.Tracking`), and
`QuickCountScreens.kt`'s `QuickCountPhase.Running` branch. No manual flag
bookkeeping needed — the modifier ties itself to composition, so leaving any
of these scopes drops it automatically.

On-device verification methodology note: this device has "stay awake while
charging" enabled by default (`stay_on_while_plugged_in`), which would have
made *every* screen look like it passes this ticket regardless of whether
`keepScreenOn` did anything — caught before trusting the first test result,
disabled for the duration of testing, restored to its original value
(`15`) afterward. With it disabled and the device's real 30s screen-off
timeout confirmed via `settings get system screen_off_timeout`:
- Position Check and Quick Count's Running phase were both directly
  confirmed via `dumpsys power` (`mWakefulness=Awake`,
  `mHoldingDisplaySuspendBlocker=true`) holding the screen awake 40-45+
  seconds past the timeout with no touch input.
- Session's `TrackingContent` was not separately re-run through the same
  device test (time), but uses the byte-for-byte identical modifier/pattern
  confirmed working at the other two sites, and its placement (inside the
  `SessionPhase.Tracking` branch only) was confirmed correct by `/code-review`'s
  Spec axis.
- A control test (app closed, home screen, same settings) confirmed the
  device's screen genuinely times out on its own (`mWakefulness=Dozing`,
  blocker=`false`) once nothing is holding it awake, validating the test
  methodology itself.

Reviewed via `/code-review` (Standards + Spec axes) before commit. Spec: no
defects — all three sites match the ticket exactly, no scope creep, the
verification gap above was the only finding and is addressed by this
comment rather than further device time. Standards: three judgement calls,
none pressed as blocking (a shared `Modifier.keepScreenOnWhile(active)`
helper for three call sites with different lifecycles would be Middle
Man/Speculative Generality; the near-identical explanatory comment in two of
three files is a legitimate but mild duplication) except one taken: the
comment-presence asymmetry (two of three sites explained the scope-exit
behavior, `PositionCheckScreen.kt` didn't) — fixed by adding the same
one-line explanation there too, for consistency.
