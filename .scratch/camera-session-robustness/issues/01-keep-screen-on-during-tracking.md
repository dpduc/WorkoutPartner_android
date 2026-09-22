# 01: Keep the screen on during camera-active screens

**What to build:** Apply `Modifier.keepScreenOn` to every screen that has a
live `PoseTracker` running against the real camera, so the display doesn't
time out mid-Session/Quick Count/Position Check the way it would on any other
screen. See `../spec.md` for why this doesn't need any manual flag
bookkeeping (it's scoped to composition automatically) and why the app
backgrounding is already handled for free.

**Blocked by:** None.

**Status:** ready-for-agent

- [ ] The Position Check screen (`BeforeYouStartScreen.kt`'s
      `PositionCheckScreen`, used by both a Session's own Before You Start
      flow and Quick Count's ticket-14 wrapper) keeps the screen on for as
      long as it's shown.
- [ ] A Session's Set-tracking screen(s) in `SessionScreens.kt` keep the
      screen on for as long as a Set is actively tracking.
- [ ] `QuickCountScreens.kt`'s `QuickCountRunScreen` keeps the screen on for
      as long as it's actively tracking.
- [ ] Any screen that stops actively tracking (Set finished/paused to rest,
      Quick Count stopped, navigating away entirely) lets the screen return
      to its normal timeout behavior — verify this is automatic once the
      modifier's composable leaves composition/is no longer part of the
      active screen, rather than something that needs an explicit "turn it
      back off" call.
- [ ] `VideoPoseTracker`-backed debug screens are **not** required to keep
      the screen on (no real camera at risk there) — fine either way, just
      not a requirement to add or avoid.
- [ ] Typecheck and the full test suite pass. This is a thin Compose
      modifier addition with no new business logic — no new unit tests are
      expected; note in Comments if a Compose UI test seam already exists
      for these screens and is extended, otherwise this stays
      manually/on-device verified like the rest of this module's UI layer.
- [ ] Verified on the real device: screen does not dim/lock during an
      idle-handed Position Check/Set/Quick Count run past the device's
      normal timeout, and does return to normal timeout behavior once
      tracking stops.

## Comments
