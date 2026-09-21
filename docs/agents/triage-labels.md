# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

## Repo-local label

`done` is not one of the five canonical roles: it marks a spec or ticket whose work has shipped, so a finished ticket no longer reads as `ready-for-agent`. A `done` ticket still keeps its acceptance checklist and `## Comments`; only tickets whose checklist is fully ticked (or that a later spec records as shipped) get it.

| Label | Meaning |
| ----- | ------- |
| `done` | Shipped; nothing left for an agent or a human to pick up |
