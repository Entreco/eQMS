---
title: EQMS-127 native test isolation verification
status: draft
related: [EQMS-127]
---

# EQMS-127 tests

Issue: #107. Defined before implementation. Requirements:
[EQMS-127](../requirements/EQMS-127.md).

| ID | Requirements | Method and expected outcome |
|---|---|---|
| T127-01 | R127-01, R127-02 | Compile unit/integration sources; inspect test/check task graphs and execute local desktop unit tests. No native integration execution or four native classes in fresh unit reports. |
| T127-02 | R127-03 | Invoke integration task locally and exercise class guard without initializing UI. Explicit rejection, no window/pointer activity. |
| T127-03 | R127-04 | Run safe feature scaffold/UI/document/search JVM regressions and observe Kotlin CI check on PR/main configuration. |
| T127-04 | R127-05, R127-06 | macOS CI executes native title-bar click, drag and minimize/restore, real-window theme/layout/close and native menu installation. Supported JBR, isolated session, no unexpected skips. |
| T127-05 | R127-05, R127-07 | macOS CI executes real-workspace preview using built qms and synthetic fixtures; preview files and reports are uploaded even on failure. |
| T127-06 | R127-05, R127-07 | Report verifier rejects absent reports, missing expected IDs, failed/error/skipped native cases; fixture tests cover each rejection and valid reports. |
| T127-07 | R127-08 | Query actual PR check names and effective main rules after updating existing ruleset. All three contexts required, other rules preserved. |
| T127-08 | R127-09 | Review exact tested revision, commands and observed outcomes in evidence; verify draft PR/main target and linked records. |
| T127-09 | R127-01, R127-02, R127-03, R127-05 | Five audited About-window methods run only in desktop/scaffold integration; non-window tests remain local; both task/class guards reject local GUI execution. |
| T127-10 | R127-04, R127-10 | Execute the five failed ScaffoldDocumentTest navigation/editor methods with current Markdown selection; retain dirty/editor/project/menu assertions. Execute both ScaffoldAboutIntegrationTest methods only in macOS CI with the same helper. |
| T127-11 | R127-04, R127-10 | Execute DocumentScreenTest.threeModesKeepLongSourceEditableAndPageBreakInsertionIsUndoable with platform-correct undo, including Linux CI; assert exact original body plus edit after undo. |
| T127-12 | R127-05, R127-07, R127-10 | Execute complete DesktopJewelPreviewTest.realNativeWorkspaceRendersInBothThemes in macOS CI; assert Documents/Review selected before toggling and deselected before collapsed capture; preserve subsequent mode, tab and image checks. |

## Required manual review/testing

- [ ] Review risk/design, requirement/test mappings and exact-revision evidence.
- [ ] Confirm local normal test execution does not interrupt desktop activities.
- [ ] Review native window usability, title-bar actions, keyboard/focus and accessibility.
- [ ] Review CI reports, synthetic screenshots and effective required-check policy.

Missing or skipped tests are not passing functional coverage. Native tests must
not be executed on the developer's active desktop for this verification.

T127-02 direct JUnit guard probe (local only, no windows/input):

```sh
./gradlew -I scripts/verify_local_native_guards.gradle \
  :apps:desktop:verifyLocalNativeClassGuards \
  :feature:scaffold:verifyLocalNativeClassGuards
```

This invokes all nine native cases through JUnit with their compiled integration
runtime classpaths. Expected initialization failures must carry the CI-only
guard message; the probe fails on any other outcome.
