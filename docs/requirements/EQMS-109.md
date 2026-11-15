---
title: EQMS-109 brand token requirements
status: draft
related: [EQMS-107, EQMS-109]
---

# EQMS-109 requirements

| ID | Requirement |
|---|---|
| R109-01 | `BrandTokens` accepts only `#RRGGBB` accent, an `on_accent` of `#000000`/`#FFFFFF`, a non-blank name and a known logo kind; anything else is rejected. |
| R109-02 | Core contrast warnings are carried verbatim and never block rendering. |
| R109-03 | `BrandTheme` exposes accent and on-accent to descendants in light and dark themes; without tokens the accent derives from the active Jewel theme, not a hard-coded colour. |
| R109-04 | No hard-coded brand/accent colour remains in chrome code; the selected editor tab uses `Brand.accent`. |
| R109-05 | `ProjectCapabilities.brandTokens` defaults to no tokens; the desktop adapter reads `qms project branding show --json`, validating the v2 envelope and command name. |
| R109-06 | `ProjectScreen` loads tokens on project open, clears them on close, and surfaces core warnings or load failures non-blockingly, including in a narrow window. |
| R109-07 | Existing Rust, Kotlin and Wasm builds and regressions pass. |

Design, hazards and limitations: [EQMS-109 design](../architecture/EQMS-109.md).
Draft records, not approved compliance evidence.
