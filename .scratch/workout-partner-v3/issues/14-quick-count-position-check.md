# 14: Quick Count Position Check

**What to build:** Run `BeforeYouStartEngine` for Quick Count too, but configured to start directly at the Position Check phase and skip Overview, Form Guides, and Countdown — keeping Quick Count quick while still confirming the tracked person is properly framed.

**Blocked by:** 12.

**Status:** ready-for-agent

- [ ] Starting Quick Count runs the Position Check (ticket 12's same checks and spoken guidance) before counting begins.
- [ ] No Workout Overview, Form Guides, or Countdown are shown for Quick Count.
- [ ] "Start anyway" is available in Quick Count's Position Check exactly as it is in a Session's.
- [ ] `BeforeYouStartEngineTest` covers the Quick Count configuration: starts at Position Check and ends without a countdown.
