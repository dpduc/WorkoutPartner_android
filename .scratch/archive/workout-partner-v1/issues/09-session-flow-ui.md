Status: done

# 09 — Self-tracking Session flow UI

## Scope

Covers user stories 10, 14, 15, 17-21, 23. Implements the flow from spec's "Specific interactions":

manual start → 5s countdown → live tracking (rep counter + per-Rep beep) → Set complete → per-Set summary → skippable rest timer → next Set → Session summary (reps vs. target, form note, Weekly Target progress).

- Routine picker (bundled Routines only, per Out of Scope — no custom Routine authoring).
- 5-second countdown after tapping start, before counting begins.
- Live tracking screen: on-screen rep counter, beep per counted Rep, warning banner + auto-resume driven by the Pose Tracking Engine's lost-tracking signal (ticket 03).
- Per-Set summary: reps vs. target, form note, Good Set determination (per `CONTEXT.md` — both rep target AND Form Score threshold required).
- Skippable rest timer between Sets, per Routine's configured rest interval.
- Session summary: overall performance + Weekly Target progress (reads from ticket 04's Streak Calculator output).
- Must work fully offline during a Session (story 23) — no network calls on the critical path; sync happens after, via ticket 06's queue.

## Depends on

Ticket 02 (Rep Counting Engine), ticket 03 (Pose Tracking Engine), ticket 04 (Streak Calculator, for Session summary), ticket 06 (repository layer, to persist Sets/Sessions).

## Out of scope

Streak/Personal Best/trend detail screens (ticket 10), safety disclaimer and Guest-conversion prompt (ticket 13).

## Comments

Implemented in `app`: a pure, tested `SessionEngine` (manual start -> 5s
countdown -> Tracking -> SetSummary -> skippable Resting -> next
step's countdown -> ... -> SessionComplete), driving the same
`RepCounter`/`PoseTrackingSignal` pipeline tickets 02/03 built — no partial
Rep across a lost-tracking gap, since the same `RepCounter` instance keeps
running across `Lost` -> `Trackable`. `SessionViewModel` is the impure glue
(real 1s ticker, `PoseTracker` camera stream, `ToneGenerator` beep per Rep,
persistence via `SetRepository`) — not unit-tested, the same "impure shell
around a tested pure engine" split as `CameraPoseTracker` (ticket 03).
Compose screens (`RoutinePickerScreen`, `SessionScreen` + per-phase content)
are real but simple. Added `AppContainer`/`WorkoutPartnerApplication` (manual
DI; Firebase-touching properties stay `by lazy` so the app can launch
without a `google-services.json`) and `BundledRoutines` (3 seeded Routines
— exact content unspecified by spec beyond "a small set," picked here).

Story 23 (fully offline): `recordSet` persistence runs off the tracking
critical path (`viewModelScope.launch`, not blocking the UI thread or the
camera loop), touches only Room, and never calls the network directly —
sync happens later via ticket 06's queue.

13 tests passing (`./gradlew :app:test`, all in `SessionEngineTest`):
countdown ticking, rep counting via fixture pose frames, Lost/Trackable
auto-resume mid-rep, Good Set requiring both conditions, rest
timer/skip/auto-advance, multi-step Sessions, and (added after review) form
notes for the no-reps/short-of-target/poor-form/good-form cases. Full
project build/tests also green.

Reviewed via `/code-review` against this ticket (Spec: one real gap found
and fixed — the per-Set/Session summary showed only a raw Form Score
number, not the textual "form note" spec.md story 20 asks for
("so that I know what to improve"); added `CompletedSet.formNote`, a short
actionable message distinguishing no-reps/short-of-target/poor-form/
good-form) and the repo's ADRs/CONTEXT.md/spec.md (Standards: no hard
violations; fixed a real dependency-duplication issue — `core-pose-tracking`
now exposes `camera-core` as `api` since `PoseTracker.start(...)`'s own
signature needs it, instead of `app` redeclaring the same dependency
itself; also changed `data`'s Room/coroutines/core-rep-counting dependencies
to `api` for the same reason, since `app` needs `WorkoutPartnerDatabase` on
its own compile classpath. Two judgement calls left as-is, per the
reviewers' own framing: `GOOD_SET_FORM_SCORE_THRESHOLD` being one uniform
value rather than per-Exercise — CONTEXT.md's "the Exercise's threshold"
wording is genuinely ambiguous, and this is disclosed as a placeholder like
ticket 02/04's; and `SessionEngine`'s "call a mutator, then separately
re-read `.phase`" API shape, justified by the UI needing continuous phase
observation rather than one-shot events like `RepCounter`/
`TrackingStateMachine`).

Known gaps carried forward, not fixed by this ticket: `MainActivity`
hardcodes `accountId = null` (Guest) until ticket 13 wires in real
Guest-vs-Account state from `AuthRepository.authState`; `CameraPoseTracker`
still needs the MediaPipe model asset (ticket 03's disclosed gap) and
Firebase still needs `google-services.json` (ticket 01's) before this
actually runs on a device — no emulator was available this session to
verify beyond compilation and the pure-engine tests.

Tickets 10 (Progress/Streaks UI) and 13 (onboarding/Guest flow) can now
build on top of a real Session flow instead of the empty shell.
