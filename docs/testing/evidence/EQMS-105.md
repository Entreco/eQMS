---
title: EQMS-105 test evidence
status: draft
related: [EQMS-105]
---

# EQMS-105 test evidence

Issue: #69. Draft automated evidence; not approved compliance evidence.

- Tested revision: `78b92ea` (branch `issue/69-eqms-105-project-lifecycle`).
- Environment: macOS, JDK 25 (JetBrains Runtime), Gradle wrapper, local machine.
- Command: `./gradlew --continue :feature:scaffold:jvmTest :feature:ui:jvmTest :feature:document:jvmTest :apps:desktop:test`

## Results

| Test ID | Coverage | Outcome |
|---|---|---|
| T105-01 | `DesktopPreferencesTest` (4 tests) | Passed |
| T105-02 | `DesktopNativeMenuTest` | Passed |
| T105-03..07 | `ProjectLifecycleTest` (9 tests), `ProjectScreenTest`, `DesktopChromeTest` | Passed |
| T105-08 | Migrated suites in `feature:scaffold`, `feature:ui`, `feature:document`, `apps:desktop` | Passed except below |

Totals: 145 tests in the collected reports, 1 failure, 4 skipped (skips are
environment `assume` guards in desktop tests).

## Failure and limitations

- `DesktopWindowNativeTest.nativeMouseClickReachesTheUnifiedTitleBarAction`
  fails with a 5000 ms timeout. It also fails on unmodified `origin/main` in
  this environment (verified by stashing changes), so it is not attributed to
  this change. It uses `java.awt.Robot` real mouse input; the click offset was
  changed to the header-actions centre because the Create/Open buttons were
  removed, but the test remains unverified here. Needs a manual or
  permissioned-desktop run.
- No Rust code changed; Rust tests were not re-run.
- Manual review/testing (remembered project on relaunch, close and relaunch,
  moved folder, native macOS menu, keyboard/accessibility) is not performed.
