# `TrackingStateMachine`'s trackable state becomes exercise-aware, not a second signal

`TrackingStateMachine` already decides whether the current camera frame counts as "trackable" — at least 3 of the 6 generic `Landmark`s tracked, debounced — and that decision already does two jobs: it drives `SessionPhase.Tracking`/`QuickCountPhase.Running`'s `trackable` field (the "Lost track of the person" banner), and it gates whether a frame ever reaches `RepCounter` at all. It has no notion of which Exercise is running, so it can flag "Lost" over a joint the current Exercise doesn't use (e.g. legs occluded mid-Push-Up), or call a frame trackable using joints irrelevant to what `RepCounter` is actually about to measure. This surfaced while designing a live, continuous camera-framing indicator for Position Check and the Session/Quick Count Run screens, which needs exactly this "can we see what this Exercise needs" answer.

We're making `TrackingStateMachine` take the current step's `ExerciseProfile` and check only the joints that Exercise (or Exercise Variant) actually needs, instead of a fixed generic-landmark count. It remains the single source of both the existing `trackable` phase field and the new framing indicator; Position Check's own independent, whole-body `bodyInFrame` check (a separate, MediaPipe-landmark-count implementation) is retired in favor of this same mechanism, so all three camera-active screens agree on one answer to "can we see this Athlete well enough right now."

## Considered options

Keep `TrackingStateMachine` generic and add a second, independent exercise-aware check purely to drive the new indicator. Rejected: two differently-computed "can we see you" signals on screen at once (the existing Lost/Trackable banner and the new indicator) could disagree with each other, and either could disagree with what `RepCounter` is silently doing with the frame underneath — exactly the kind of confusion a single source of truth is meant to prevent.

## Consequences

`TrackingStateMachine` already imports `core-rep-counting` types (`PoseLandmarkFrame`); this deepens that reliance to include `ExerciseProfile`'s per-Exercise joint requirements, not just its generic frame shape. Whatever constructs/drives a `TrackingStateMachine` instance must now supply the current step's `ExerciseProfile` and update it as the Athlete moves between Sets of different Exercises within a Session. Position Check's `bodyInFrame` computation is deleted rather than kept as a second implementation of a similar idea.
