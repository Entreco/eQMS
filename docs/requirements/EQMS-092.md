---
title: EQMS-092 Jewel desktop requirements
status: draft
related: [EQMS-092, EQMS-090, EQMS-023, EQMS-050]
---

# EQMS-092 requirements

Issue: #50.

| ID | Requirement |
|---|---|
| R092-01 | Desktop uses genuine Jewel standalone components with a verified published Compose combination and JBR, not restyled Material components. |
| R092-02 | The dark-first shell provides a light/dark toggle, native title bar, project toolbar, document/search sidebar, single active editor tab, history/diff presentation and truthful status. |
| R092-03 | Theme changes preserve the open project, editor contents, dirty status and search state. |
| R092-04 | Create/open, directory browsing/new folders, cancellation, busy/error handling and native response validation retain R090 behavior. |
| R092-05 | Draft authoring, failed-save retention/conflicts, unsaved navigation/closing, read-only history and comparison retain R023 behavior. |
| R092-06 | Grouped saved-draft search, snapshot pagination, current/latest opening, historical read-only fallback and timing measurement retain R050 behavior. |
| R092-07 | JVM presentation remains in core:ui; common contracts/state remain reusable; native adapters remain apps:desktop; Wasm and existing web bootstrap compile without Jewel. This supersedes R090-08's common-screen requirement only. |
| R092-08 | Status and history never imply implemented approval, trusted identity/time, signatures or publication; keyboard focus and errors remain visible in both themes. |
| R092-09 | A real-adapter desktop launch and documented commands allow preview; evidence accurately distinguishes automated results, GUI capture limitations and pending human review. |

Risk applicability and limitations: [design](../architecture/EQMS-092.md).
