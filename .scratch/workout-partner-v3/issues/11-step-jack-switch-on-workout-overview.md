# 11: Step Jack switch on Workout Overview

**What to build:** Wire ticket 08's Step Jack engine into the Workout Overview (ticket 03). When the chosen Routine contains Jumping Jack, the Overview shows a Jumping Jack/Step Jack toggle, defaulted to Step Jack when the Athlete's BMI is ≥ 30, switchable either way for that Session only.

**Blocked by:** 08, 03.

**Status:** ready-for-agent

- [ ] The toggle appears on the Workout Overview only when the selected Routine contains a Jumping Jack step — absent otherwise.
- [ ] Defaults to Step Jack when BMI ≥ 30, Jumping Jack otherwise.
- [ ] The Athlete can switch either way before starting; the choice applies to the whole Session's Jumping Jack steps and is not persisted between Sessions.
- [ ] Switching correctly threads the chosen Variant into the Session's steps so ticket 08's Good Set / Personal Best / persistence behavior applies.
- [ ] Manually verified: starting a Routine containing Jumping Jack at a BMI ≥ 30 profile shows Step Jack pre-selected; completing a Set records it as "Step Jack" in history.
