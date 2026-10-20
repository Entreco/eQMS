---
title: EQMS-002 test specification
status: draft
related: [EQMS-002, EQMS-005]
---

# EQMS-002 tests

Defined before review-fix implementation. Requirements are in
[EQMS-002 requirements](../requirements/EQMS-002.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T002-01 | R002-01, R002-05 | Verify ordered migration success, repeat execution, rollback on migration failure, and explicit rejection of a future schema version. |
| T002-02 | R002-02 | On Unix, index both a literal-backslash filename and a nested-path filename; preserve distinct relative-path keys and contents. |
| T002-03 | R002-03 | Verify symlinked document directories/files are not traversed or indexed. |
| T002-04 | R002-03, R002-04 | Force a rebuild failure and verify the prior index contents, journal bytes, and Git `HEAD` remain unchanged. |
| T002-05 | R002-02, R002-04 | Rebuild twice from nested Markdown documents; verify counts and that canonical history remains unchanged. |

## Manual checklist — pending

- [ ] Human reviews index design, risks, requirements, mappings, and evidence.
- [ ] Human builds and rebuilds an index from a representative workspace.
- [ ] Human confirms failure behavior and that canonical history is unchanged.
- [ ] Reviewer records the manual outcome before merge.
