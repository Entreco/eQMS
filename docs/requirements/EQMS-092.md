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

## Approved document workspace increment

These extend the existing IDs and supersede single-tab/draft-only clauses in
R092-02, R092-05 and R092-08 only where specified. EQMS-093 chrome refinements
and historical evidence remain unchanged.

| ID | Requirement |
|---|---|
| R092-12 | Left Project/Search and right Review/History tools shall select, collapse and resize around central document tabs, with bounded widths and keyboard-accessible controls. Layout/tabs are session-only, initially Project/Review open. |
| R092-13 | Independent editor buffers shall survive tab/panel/theme changes. Latest tabs deduplicate by ID, historical tabs by ID/revision; historical content remains read-only. Async results shall not overwrite another tab or dirty buffer. |
| R092-14 | Dirty tab closing shall offer Save/Discard/Cancel; project closing/switching and quit shall coordinate all dirty buffers. Failed/invalid saves prevent navigation and retain text; cancelled/failed project opening preserves buffers. |
| R092-15 | Native adaptation shall strictly decode all lifecycle states/events and validate response envelopes/identity. Expected-revision transitions and errors shall use native authority without success-shaped fallbacks. |
| R092-16 | Confirmed Submit for review, Approve and Publish (Effective) shall require clean saved/latest content. Content becomes read-only after submission; Effective can become Obsolete/Superseded; historical/terminal tabs cannot transition. |
| R092-17 | Status/history/search presentation shall reflect observed data, never hardcode Draft for non-Draft records, or imply signatures, authenticated authority, trusted time, external publication or controlled release baselines. |
| R092-18 | Existing creation/open verification, native chrome, grouped search/pagination/timing, explicit saves/conflicts, exact history/diff, error/busy behavior and Wasm compilation shall remain compatible. |
| R092-19 | Project/Search/Review/History rails shall use compact themed icons only, descriptive hover tooltips with platform shortcuts, accessible names and visible selected/focus/disabled states. Panel headings/content remain labelled. |
| R092-20 | Command+1..4 on macOS and Alt+1..4 on Windows/Linux shall toggle the corresponding tool identically to clicking, including from text fields. Match exact modifiers, suppress held repeats, obey busy/modal guards and preserve buffers, caret, tabs, search and widths; unrelated keys remain available. |
| R092-21 | Tool and central editor panels shall have rounded clipped surfaces; both horizontal split boundaries shall support bounded dragging and a resize pointer when enabled. Hover hints shall align vertically with their icon and open inward. Document tabs shall use compact IntelliJ-style selected/inactive presentation, a document icon and integrated accessible close control, preserving dirty/historical indicators, focus, disabled guards and unsaved buffers. |
