---
title: EQMS-104 paginated canvas tests
status: draft
related: [EQMS-104]
---

# EQMS-104 tests

Defined before implementation. Requirements are in
[EQMS-104 requirements](../requirements/EQMS-104.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T104-01 | R104-01, R104-02 | Open a document tab and verify Canvas is selected by default; switch between Canvas/Preview/Source/Split and confirm per-tab state and edit retention. |
| T104-02 | R104-02, R104-07 | Edit in Canvas, save, reload, close/reopen, and run lifecycle transitions/conflict paths; confirm existing guards/messages remain accurate. |
| T104-03 | R104-03, R104-06 | Load default page setup and save valid combinations of size/orientation/margins; reject invalid margins/geometry and keep a recoverable fallback. |
| T104-04 | R104-04 | Render multipage content and verify repeated header/footer text and automatic page numbering placeholders resolve per page. |
| T104-05 | R104-05 | Change page setup only and verify no canonical document revision/hash changes until explicit content save. |
| T104-06 | R104-07 | Run affected document/scaffold/desktop regressions to confirm compatibility with search, history, diff, and render pathways. |

## Manual checklist — pending

- [ ] Human visual review of page layout in dark/light themes.
- [ ] Human keyboard/focus and accessibility checks for page setup and canvas.
- [ ] Human platform checks for expected page-fit behavior.
