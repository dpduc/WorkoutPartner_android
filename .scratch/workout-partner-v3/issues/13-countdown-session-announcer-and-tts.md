# 13: Countdown + SessionAnnouncer + TTS

**What to build:** Complete `BeforeYouStartEngine`'s final phase with a 10-second countdown into the Session, and add spoken announcements throughout the Session itself via a new `SessionAnnouncer` + TTS wrapper.

**Blocked by:** 12.

**Status:** ready-for-human

- [x] A 10-second countdown in huge digits plays once the Position Check passes (or "Start anyway" is used).
- [x] The first Exercise and its rep target are announced aloud at the start of the countdown (e.g. "Squat. 12 reps.").
- [x] The countdown leads straight into the first Set's tracking with no extra tap.
- [x] A new pure `SessionAnnouncer` maps a `SessionPhase` transition (previous → next) plus the Routine steps to zero or more announcements, each with a priority (Critical, High, Normal, Low): Set start ("Squat. 12 reps." — using the Variant name and difficulty-adjusted target when applicable, e.g. "Step Jack. 20 reps."), Set end + rest ("Rest 30 seconds."), 5s before rest ends ("Get ready. Next: Push-up."), tracking lost ("Tracking lost. Step back into frame."), Session complete ("Workout complete.").
- [x] A thin Android TTS wrapper plays announcements; Critical interrupts the queue, Low is dropped if the queue is busy.
- [x] A Settings toggle turns spoken prompts off entirely (default on); the wrapper respects it.
- [x] All spoken lines and Form Guide text (ticket 10) are in English for this version, sourced from string resources so they're translatable later with no code changes.
- [x] The existing 5s pre-Set countdown inside `SessionEngine` is unchanged; this 10s countdown precedes the Session as a whole, not each Set.
- [x] `SessionAnnouncerTest` (styled like `ReminderPolicyTest`) covers the correct lines and priorities for each phase transition, including Variant names and difficulty-adjusted targets.
- [x] `BeforeYouStartEngineTest` covers the countdown reaching Ready after 10 ticks.
- [x] Not tested (thin framework adapters): the TTS wrapper itself, Compose layouts, camera preview.

## Comments

Implemented as spec'd. `BeforeYouStartPhase.Countdown` now carries `secondsRemaining`; the engine enters it at 10 and reaches `Ready` on the 10th tick (`BeforeYouStartEngineTest`), and `BeforeYouStartScreen` shows a huge-digit `CountdownScreen`, then calls `onReadyForSession` — straight into the first Set, no tap. The Position Check's speaker is now owned by `BeforeYouStartScreen` for the whole flow so the first-Set line ("Squat. 12 reps.") spoken as the countdown starts isn't cut off when the Position Check leaves composition.

`SessionAnnouncer` (pure, `SessionAnnouncerTest` styled like `ReminderPolicyTest`) maps `previous -> next` `SessionPhase` to `Announcement(text, priority)`: Set start on entering a Countdown (HIGH; Variant name and the already difficulty-adjusted target, via a new shared `RoutineWithSteps.toRoutineSteps` that `SessionViewModel` also uses), "Rest N seconds." on entering Resting (NORMAL), "Get ready. Next: X." when a rest ticks 6 -> 5 with a next step (LOW), "Tracking lost. Step back into frame." once per loss (CRITICAL), "Workout complete." (HIGH). The words come through an `AnnouncerPhrases` interface so the announcer stays Context-free; `ResourceAnnouncerPhrases` reads plurals/strings from `strings.xml` and reuses the Form Guides' exercise names. The first Set's line isn't a transition (SessionEngine's first Countdown is its initial phase), so it is never spoken twice. `PromptSpeaker` gained priorities (CRITICAL flushes the queue, LOW is dropped while speaking) and checks the new device-local `SpeechPrefs` "Spoken prompts" toggle (Settings, default on) on every line, including ones held before TTS was ready. `SessionEngine`'s own 5s pre-Set countdown is untouched.

Caught in review and fixed: `SessionViewModel`'s new required parameters came after defaulted ones; `(text, priority)` travelled as a bare pair — now one shared `Announcement` type in `speech`; queued pre-init lines ignored the setting being switched off; two untested paths (a step with no rest going straight to the next Set start / Session complete) now have tests.

Judgement calls left as-is, for the record: (1) "Set end + rest" is spoken as "Rest N seconds." when the rest actually starts, which needs the Athlete to tap through the Set summary — nothing is spoken at the moment the Set ends; (2) `session` and `beforeyoustart` now import each other (`resolveVariant`/`FormGuides` one way, `toRoutineSteps`/`SessionAnnouncer` the other); (3) "Spoken prompts" state lives in `SettingsScreen`/`SpeechPrefs` rather than `SettingsViewModel`; (4) "Tracking lost" isn't debounced, so a flapping tracker re-flushes the queue; (5) a CRITICAL line requested before TTS finishes initializing doesn't flush earlier pending lines; (6) the spoken "Workout complete." follows the ticket's wording even though CONTEXT.md lists "Workout" among terms to avoid — it's one string resource if that should change; (7) on-screen text ("Get ready", "Spoken prompts") is inline like the rest of the app, only spoken lines are resources; (8) `EnglishPhrases` in the test mirrors `strings.xml` by hand.

**Not verified on a device** (`adb devices` is empty): TTS output and priority behaviour, the countdown/announcement timing, and the Settings toggle end to end. The ticket lists the TTS wrapper, layouts and camera as untested by design, but the whole spoken flow is unexercised, so status is `ready-for-human`.
