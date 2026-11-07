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
| T104-01 | R104-01, R104-02 | Verify exactly three toolbar modes in order and Markdown default; switch Markdown/Split/Preview and tabs, retaining unsaved text, caret, source/preview scroll and split fraction. |
| T104-02 | R104-02, R104-07 | Edit source beyond the former first-page limit, undo/redo, save/reload, close/reopen and exercise lifecycle/conflict/busy paths; historical/non-Draft source remains read-only. |
| T104-03 | R104-03, R104-06 | Load default page setup and save valid combinations of size/orientation/margins; reject invalid margins/geometry and keep a recoverable fallback. |
| T104-04 | R104-04 | Render multipage content and verify repeated header/footer text and automatic page numbering placeholders resolve per page. |
| T104-05 | R104-05 | Change page setup only and verify no canonical document revision/hash changes until explicit content save. |
| T104-06 | R104-07 | Run affected document/scaffold/desktop regressions to confirm compatibility with search, history, diff, and render pathways. |
| T104-07 | R104-08 | Rust/model/HTML fixtures recognize exact top-level markers; LF/CRLF, ordinary newlines/rules, inline/code/nested lookalikes, redundant/leading/trailing markers and source-byte preservation are checked. |
| T104-08 | R104-09 | Source insertion preserves selection text, inserts a standalone marker at caret, updates selection/dirty state, supports undo and is disabled in Preview and read-only/busy states. |
| T104-09 | R104-04, R104-10 | A4/Letter, orientations, margin extremes, exact-fit and overflow fixtures verify measured page content bounds, text fragment ordering/coverage, repeated furniture, cross-page table rows and image aspect ratios. |
| T104-10 | R104-11 | Contents and footnote targets navigate to their pages; rapid edit/tab/width/page-setup changes cannot apply stale layout. Toolbar/divider keyboard and selected accessibility semantics work. |
| T104-11 | R104-05, R104-08 | Mode/page-setting/render operations do not mutate canonical source, saved hash/revision, Git refs or journals; explicit marker insertion persists only with Save. |
| T104-12 | R104-10, R104-11 | Run versioned 50 KiB/1 MiB native paginated-layout fixtures and record warm p95 against existing 150/500 ms targets including coalescing, explicit limit/overflow errors and all exclusions. |

Automated execution and revision-bound outcomes will be recorded separately.
No missing or skipped functional tests count as passing coverage.

## Manual checklist — pending

- [ ] Human visual review of page layout in dark/light themes.
- [ ] Human typing/undo beyond a page and mode-switching checks.
- [ ] Human keyboard/focus and accessibility checks for toolbar, source, split and page setup.
- [ ] Human platform checks for expected page-fit behavior.
