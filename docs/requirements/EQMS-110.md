---
title: EQMS-110 project creation with branding requirements
status: draft
related: [EQMS-107, EQMS-109, EQMS-110]
---

# EQMS-110 requirements

| ID | Requirement |
|---|---|
| R110-01 | `qms project branding preview` returns the branding core would record (name, accent, on-accent, logo incl. generated initials/colour/shape, contrast warnings) without a workspace and without writing anything; it applies the same validation as `init`/`set`. |
| R110-02 | `--name` is required for preview; invalid name, colour, seed, or logo file yields an error envelope and a non-zero exit code. |
| R110-03 | After choosing a directory for New project, the desktop app shows a Project identity step; cancelling it creates no project. |
| R110-04 | The step previews logo (generated or PNG), accent and core contrast warnings live from core output; warnings do not block creation. |
| R110-05 | The default logo and accent derive from the name; Shuffle offers another generated logo; the user may choose an accent swatch or enter `#RRGGBB`. |
| R110-06 | A PNG logo can be chosen by path or Browse; core validates type, size and dimensions and its error is shown. |
| R110-07 | Create is disabled while there is no valid preview or while creating; success records branding via `qms init` and opens the project; failure is shown with a hint that the folder may exist. |
| R110-08 | Branding recorded at creation is the branding shown by `qms project branding show` for the new project. |
| R110-09 | The step is keyboard operable: labelled fields, focusable buttons, Enter/Escape semantics of dialogs. |
| R110-10 | Existing Rust, Kotlin and Wasm builds and regressions pass; existing `ProjectCapabilities` implementations keep working. |

Design, hazards and limitations: [EQMS-110 design](../architecture/EQMS-110.md).
Draft records, not approved compliance evidence.
