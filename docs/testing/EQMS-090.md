---
title: EQMS-090 desktop project creation and opening tests
status: draft
related: [EQMS-090]
---

# EQMS-090 test specification

| Test ID | Requirement mapping | Test |
|---|---|---|
| T090-01 | R090-02, R090-04 | Verify create sends `init`, `--json`, and the selected path as distinct arguments, including a path containing spaces; accept only a successful schema-version-1 `init` response. |
| T090-02 | R090-03, R090-04 | Verify open sends `verify`, `--json`, and the selected path as distinct arguments; accept only a successful schema-version-1 `verify` response. |
| T090-03 | R090-02, R090-03, R090-05 | Verify nonzero exits, structured CLI error output, and launch failure return actionable failures and never success. |
| T090-04 | R090-04, R090-05 | Verify malformed JSON, unsupported schema versions, wrong response status, and mismatched command are rejected. |
| T090-05 | R090-01, R090-05, R090-06 | Manual GUI test: confirm the landing actions, chooser cancellation, busy-state behavior, success state, and visible error state. |
| T090-06 | R090-02, R090-03, R090-05 | Manual integration test against built native `qms`: create a workspace, open/verify it, reject an invalid directory, and confirm non-empty destination errors without being overwritten. |
| T090-07 | R090-07 | Automated tests: create normal and spaced child names; reject blank, absolute, separator-containing, dot and dot-dot names; refuse existing paths without altering their content; surface filesystem failures. Verify the explicit New folder accessory is installed for Create only. |
| T090-08 | R090-02, R090-05, R090-07 | Manual GUI test in hotRun: create a named folder, confirm it is selected, initialize it, and reopen it. Verify prompt cancellation, visible filesystem/name errors, and that chooser cancellation preserves the created empty folder without invoking init. |

Automated adapter tests are expected to use an injected process executor and
must not depend on a developer's Git identity or a globally installed `qms`.
Manual GUI and native CLI integration checks remain separate from automated
results and must be recorded as pending until performed by a human.
