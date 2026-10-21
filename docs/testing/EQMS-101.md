---
title: EQMS-101 CLI JSON output tests
status: draft
related: [EQMS-101]
---

# EQMS-101 tests

Defined before implementation. Requirements are in
[EQMS-101 requirements](../requirements/EQMS-101.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T101-01 | R101-01, R101-02, R101-03 | Invoke `qms --json --version`; assert a valid schema-version-2 envelope, `version` command, integer timestamp, empty arrays, `data.version`, and null error. |
| T101-02 | R101-01, R101-02, R101-03 | Exercise representative workspace and document successes; assert canonical command names, all shared fields, empty default arrays, and that each existing command payload is preserved under `data`. |
| T101-03 | R101-01, R101-02, R101-04 | Exercise usage and operational errors; assert nonzero exit codes 2 and 1 respectively, empty stdout, schema-version-2 envelope on stderr, null data, structured error, and shared fields. |
| T101-04 | R101-01, R101-05 | Exercise human-readable version, help, and representative command output; assert no JSON envelope is introduced. |
| T101-05 | R101-06 | Exercise desktop create/open success, structured failure, malformed envelopes, wrong command, invalid `data.path`, and unsupported schema version; only a valid schema-version-2 response succeeds. |
| T101-06 | R101-06 | Exercise desktop document list/create/save/history/diff success and operation failure; assert data is decoded only from a valid version-2 envelope. |
| T101-07 | R101-07 | Review the published contract and downstream adapter expectations for matching version-2 field names and compatibility statement. |

## Manual checklist — pending

- [ ] Human reviews the schema-v2 compatibility change and risk assessment.
- [ ] Human exercises native CLI JSON success and failure cases.
- [ ] Human reviews desktop create/open and document operations against the native CLI.
- [ ] Reviewer records manual outcome before merge.
