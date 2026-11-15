---
title: EQMS-111 project chrome and settings requirements
status: draft
related: [EQMS-110, EQMS-111]
---

# EQMS-111 requirements

| ID | Requirement |
|---|---|
| R111-01 | With a project open the header shows `DutchDoxx`, the project logo, then the project name, in that order. |
| R111-02 | The logo is the core-described generated logo or the stored PNG; without a project the header shows no logo. |
| R111-03 | The Light/Dark theme button is replaced by a gear button labelled "Settings" that opens the Project Settings window dialog; File > Settings opens the same dialog. |
| R111-04 | Project Settings toggles the theme (applies immediately; persistence failures are shown). |
| R111-05 | With a project open, Project Settings edits name, logo (Shuffle/PNG) and accent with a core preview; Save is explicit and runs `qms project branding set` (journaled). |
| R111-06 | Save is disabled when nothing changed or no valid preview exists; save failures are shown and keep the edits. |
| R111-07 | Closing with unsaved branding changes requires explicit Discard; a save is refused if the stored branding changed since the dialog loaded. |
| R111-08 | Without a project Project Settings offers only the theme toggle. |
| R111-09 | Primary (default) buttons use the project accent and core's on-accent colour; without branding the theme default applies. |
| R111-10 | Existing Rust, Kotlin and Wasm builds and regressions pass. |

Design: [EQMS-111](../architecture/EQMS-111.md). Draft records, not approved compliance evidence.
