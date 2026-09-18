# 13: Countdown + SessionAnnouncer + TTS

**What to build:** Complete `BeforeYouStartEngine`'s final phase with a 10-second countdown into the Session, and add spoken announcements throughout the Session itself via a new `SessionAnnouncer` + TTS wrapper.

**Blocked by:** 12.

**Status:** ready-for-agent

- [ ] A 10-second countdown in huge digits plays once the Position Check passes (or "Start anyway" is used).
- [ ] The first Exercise and its rep target are announced aloud at the start of the countdown (e.g. "Squat. 12 reps.").
- [ ] The countdown leads straight into the first Set's tracking with no extra tap.
- [ ] A new pure `SessionAnnouncer` maps a `SessionPhase` transition (previous → next) plus the Routine steps to zero or more announcements, each with a priority (Critical, High, Normal, Low): Set start ("Squat. 12 reps." — using the Variant name and difficulty-adjusted target when applicable, e.g. "Step Jack. 20 reps."), Set end + rest ("Rest 30 seconds."), 5s before rest ends ("Get ready. Next: Push-up."), tracking lost ("Tracking lost. Step back into frame."), Session complete ("Workout complete.").
- [ ] A thin Android TTS wrapper plays announcements; Critical interrupts the queue, Low is dropped if the queue is busy.
- [ ] A Settings toggle turns spoken prompts off entirely (default on); the wrapper respects it.
- [ ] All spoken lines and Form Guide text (ticket 10) are in English for this version, sourced from string resources so they're translatable later with no code changes.
- [ ] The existing 5s pre-Set countdown inside `SessionEngine` is unchanged; this 10s countdown precedes the Session as a whole, not each Set.
- [ ] `SessionAnnouncerTest` (styled like `ReminderPolicyTest`) covers the correct lines and priorities for each phase transition, including Variant names and difficulty-adjusted targets.
- [ ] `BeforeYouStartEngineTest` covers the countdown reaching Ready after 10 ticks.
- [ ] Not tested (thin framework adapters): the TTS wrapper itself, Compose layouts, camera preview.
