---
title: EQMS-005 test specification
status: draft
related: [EQMS-005, EQMS-010, EQMS-101]
---

# EQMS-005 tests

Defined before review-fix implementation. Requirements are in
[EQMS-005 requirements](../requirements/EQMS-005.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T005-01 | R005-01, R005-02 | Initialize a workspace with explicit identity; verify genesis and initial commit. Verify identity inference alone is rejected. |
| T005-02 | R005-03 | Race two initializers against the same directory; exactly one succeeds and the other leaves the committed workspace intact. |
| T005-03 | R005-04 | Reject symlinked canonical files and parent directories; verify attempted reads/writes do not alter an external target. |
| T005-04 | R005-05, R005-06 | Record a valid manifest update and verify its content hash against both the working file and committed blob. |
| T005-05 | R005-05, R005-07 | With line-ending conversion enabled, refuse content whose Git blob differs from the hashed working bytes without advancing `HEAD`; exercise equivalent recovery refusal. |
| T005-06 | R005-07 | Recover a valid pending event and an interrupted initial commit through CLI; verify commit and workspace afterward. |
| T005-07 | R005-07 | Give recovery an invalid partial initialization and a committed journal missing only its final newline; verify it rejects unsupported state without modifying files or committing. |
| T005-08 | R005-08 | Record a file whose path contains wildcard characters and verify unrelated matching files are not staged or committed. |
| T005-09 | R005-09 | Exercise CLI success/error JSON contracts and ensure failure exits are nonzero with structured errors. |
| T005-10 | R005-10 | Verify tampering and unsupported recovery errors are explicit; review documentation for trust limitations. |

## Manual checklist — pending

- [ ] Human reviews workspace design, risk assessment, requirements, mappings, and evidence.
- [ ] Human initializes and verifies a workspace using the documented CLI commands.
- [ ] Human checks recovery behavior, configured Git identity, and error reporting.
- [ ] Reviewer records the manual outcome before merge.
