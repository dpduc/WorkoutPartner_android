# Session record — v3 tickets 10–13, video-driven device testing, doc reconciliation

A record of one long working session (Claude Code, 2026-09-21), kept so the reasoning behind the commits is not lost. Not a spec: the code, `CONTEXT.md` and the ADRs are the source of truth.

## 1. Built: `workout-partner-v3` tickets 10–13

| Ticket | What shipped |
|---|---|
| 10 Form Guides | `BeforeYouStartEngine(unseenGuides)`, a swipeable `FormGuidesScreen`, six bundled guides (five Exercises + Step Jack) in string resources, seen-state in `FormGuidePrefs` (device-local, not Room). |
| 11 Step Jack switch | Overview switch shown only when the Routine has Jumping Jack; default Step Jack at BMI ≥ 30; choice not persisted; threaded through `AppScreen.Session` to the tracked Exercise. |
| 12 Position Check | `RawPoseFrame` + `PoseTracker.rawFrames`; pure `PositionCheckEvaluator` (28/33 landmarks ≥ 0.5, skeleton height 40–80 %, 2 s stillness); "Start anyway" after 15 s; spoken cues. |
| 13 Countdown + announcer | 10 s countdown; pure `SessionAnnouncer` with priorities; TTS wrapper; Settings "Spoken prompts" switch. |

Ticket 14 (Quick Count Position Check) is not started. Tickets 09, 11, 12, 13 are `ready-for-human` for on-device checks (spoken audio, the Settings toggle, Step Jack default at BMI ≥ 30, "Step Jack" in history).

## 2. Video-driven device testing (no one in front of the camera)

The user could not exercise in front of the phone, so recorded clips are fed into the real pose pipeline.

- `VideoPoseTracker` (debug builds only) decodes `filesDir/debug_video.mp4` every 66 ms, runs MediaPipe in `VIDEO` mode, and writes `<video>.landmarks.csv` when finished. `AppContainer.createPoseTracker()` picks it when the build is debuggable and the file exists.
- `SessionViewModel` logs under tag `SessionTrace` (phases, each rep, set results). Each Session now gets its own ViewModel and releases the tracker on exit — fixes an orphaned Position Check tracker that kept decoding, and a reused Session.
- A debug-only landmark overlay (`PoseOverlay`) draws the 33 landmarks (green ≥ 0.5 confidence, red below). Exact for video; only approximate on the live preview (its crop is not accounted for).
- `ClipReplayTest` replays committed landmark CSVs through the real counter on the PC, so counting can be tuned without the phone. `docs/test-clips.md` lists the clips still to record and how to run them.

**Findings**

- Raw landmarks made rep counting swing wildly (jumping jacks 7 for a true 4; push-ups 28–32 for 7). Fixed with a median-of-5 smoothing window plus hysteresis (`RELEASE_MARGIN_DEGREES = 20`) in `RepCounter`: jumping jacks now 4/4, push-ups 6 for a true 7 on a very noisy clip (push-up precision is unproven).
- Jumping Jack form scored 0 because smoothed arm peaks were 130–137° against a 150° bar. Thresholds are now Jumping Jack rep 90° / form 125°, Step Jack rep 75° / form 110°. All still placeholders, tuned on one 4-rep clip.

## 3. Documentation audit and decisions

Audited every doc, spec and ticket for duplication, conflict and completion; fixed stale facts and recorded three decisions as ADRs.

| ADR | Decision |
|---|---|
| 0008 | Adopt the 10-Routine catalogue as the bundled set; AMRAP is its own timed mode (rounds, no programmed rest, scored `rounds × reps + extra`), not a Routine kind. Routines stay flat; one threshold pair per Exercise; Step Jack comes from the Overview switch. |
| 0009 | Streak stays computed on-device by `StreakCalculator`; Cloud Functions become the Account authority only after pull-sync exists. Two implementations then need shared test fixtures; Cloud Functions need a paid Firebase plan. |
| 0010 | The owner may write `sessions`, `trackedProfiles` and a limited set of `accounts` fields; Streak fields are client-writable until the Cloud Function ships. ADR-0006's "Web is read-only" is a scope rule, not something the rules can enforce. Rules change ships with the push path. |

Other changes: `CONTEXT.md` gains **AMRAP**; `firestore-schema`, `auth-roadmap` and `data-architecture-roadmap` corrected; `before-you-start-system` marked partly superseded by the v3 spec; the v1 spec notes that "Account holder" stories now apply to every Athlete; a repo-local `done` triage label was added and applied to shipped v1, v2 and v3 tickets 02, 03, 05, 06, 07, 08, 10.

## 4. Still open

- **Unpushed:** commits `568f042` and `ab1656d`. Everything before them is already on `origin/master` (this session's own push attempt was denied by the auto-mode permission check, so it was pushed some other way).
- **Bug:** `SetEntity.toFirestoreMap()` and `TallyEntity.toFirestoreMap()` do not send `exerciseVariant`, so a synced Step Jack Set arrives as a plain Jumping Jack. Nothing calls `SyncEngine` in production yet, so nothing is lost today. Fix with the push path (ADR-0010).
- **Unverified:** joint angles are computed on normalised (non-aspect-corrected) coordinates, which may distort them; the landscape push-up clip suggests it. Needs a portrait-recorded clip.
- **Known rough edges:** Position Check's distance rule misjudges horizontal exercises (push-up reads "too far"); "Session complete" title sits under the status bar; the Done button is near the gesture bar; Quick Count's ViewModel may have the same un-keyed `viewModel()` pattern (`QuickCountScreens.kt:81`).
- **Ticket boxes:** v3 ticket 01 has 7 unticked boxes although the code shows phone sign-in removed; tickets 04, 09, 11 have one each; 14 has four.
- **Not built (docs describe them):** the 9 further Routines and all of AMRAP (ADR-0008), pull-sync, reactive DAOs and domain models, WorkManager sync, Cloud Functions, sync badge, guided warm-up, gesture start, and the onboarding script's random-name and slider screens (onboarding still uses plain text fields).
- **Recording:** clips listed in `docs/test-clips.md` still need recording by the user.
