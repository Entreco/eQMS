---
title: EQMS-070 test specification
status: draft
related: [EQMS-070]
---

# EQMS-070 tests

Requirements are in [EQMS-070 requirements](../requirements/EQMS-070.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T070-01 | R070-01 | Core unit tests: valid v1 manifest parses; missing, 0 and 2 `api_version` rejected. |
| T070-02 | R070-02 | A manifest with all five contribution kinds parses; unknown fields and oversized input rejected. |
| T070-03 | R070-03 | Invalid IDs/names/versions, reserved plugin IDs, foreign namespaces, duplicate names, undefined artifact type references and non-increasing migrations are rejected. |
| T070-04 | R070-04 | Each reserved capability and an unknown capability reject the manifest. |
| T070-05 | R070-05 | Each contribution kind without its required capability is rejected. |
| T070-06 | R070-06 | `authorize` denies every core-control operation even with all grantable capabilities; allows/denies others by capability. |
| T070-07 | R070-07 | Registry load with valid, invalid, duplicate and unsupported inputs: valid plugins load in order, failures reported with source. Panic capture is defensive and not exercised by an automated test. |
| T070-08 | R070-08 | Code review: no CLI/UI imports in `plugin.rs`; docs state limitations. |
| T070-09 | all | Regression: `cargo test --workspace`, clippy, fmt. |

## Manual checklist — pending

- [ ] Human reviews design, risk assessment, requirements and mappings.
- [ ] Human confirms the reserved capability list covers all core controls.
- [ ] Human confirms the docs do not imply a plugin runtime exists.
- [ ] Reviewer records manual results before merge.
