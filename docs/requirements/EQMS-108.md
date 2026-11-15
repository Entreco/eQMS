---
title: EQMS-108 product identity requirements
status: draft
related: [EQMS-107, EQMS-108]
---

# EQMS-108 requirements

| ID | Requirement |
|---|---|
| R108-01 | Product identity (name, attribution, author, licence notice) is defined once in constants that accept no project branding input. |
| R108-02 | The About dialog shows the DutchDoxx wordmark, version, author, licence notice and the text "Powered by DutchDoxx", and makes no signature/audit capability claims. |
| R108-03 | "Powered by DutchDoxx" is always visible in the main window footer, with or without a project and in either theme. |
| R108-04 | The window title always contains "DutchDoxx"; a project name can only be an addition. |
| R108-05 | Project white-labelling cannot hide or replace the attribution (tested). |
| R108-06 | Naming rules (DutchDoxx product; eQMS/`qms` technical) are documented in README and BuildAndRun; trademark/licence caveats are recorded. |
| R108-07 | Existing Kotlin desktop/scaffold regressions pass. |

Design, hazards and limitations: [EQMS-108 design](../architecture/EQMS-108.md).
Draft records, not approved compliance evidence.
