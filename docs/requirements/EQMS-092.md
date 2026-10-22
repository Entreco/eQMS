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
| R092-07 | JVM presentation lives in feature:scaffold/document/search/ui; common contracts/state remain reusable; native adapters remain apps:desktop; all feature Wasm targets and existing web bootstrap compile without Jewel. This supersedes R090-08's common-screen/module-location requirement. |
| R092-08 | Status and history never imply implemented approval, trusted identity/time, signatures or publication; keyboard focus and errors remain visible in both themes. |
| R092-09 | A real-adapter desktop launch and documented commands allow preview; evidence accurately distinguishes automated results, GUI capture limitations and pending human review. |
| R092-10 | Remove obsolete Kotlin core:search/core:ledger, app dependencies, unused JDBC catalog entries and the prototype SQLite initializer; retain the Kotlin CLI as an explicit placeholder. Preserve Rust implementations and existing databases. Defer Rust crate/UI feature splitting and pause PR creation as requested. |
| R092-11 | Superseding R092-10's deferred UI split, move core:ui to feature:scaffold, feature:document, feature:search and shared feature:ui primitives. Move search presentation with state, maintain acyclic scaffold -> document -> search -> ui dependencies and feature-local tests, and leave tracked core/ Rust-only without changing Rust crates or runtime behavior. |

Risk applicability and limitations: [design](../architecture/EQMS-092.md).
