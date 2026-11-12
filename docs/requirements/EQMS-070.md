---
title: EQMS-070 plugin API v1 requirements
status: draft
related: [EQMS-003, EQMS-014, EQMS-070]
---

# EQMS-070 requirements

| ID | Requirement |
|---|---|
| R070-01 | The plugin manifest declares `api_version`; version 1 is accepted and any other or missing version is rejected. |
| R070-02 | The API defines extension contracts for artifact types, commands, validators, reports and migrations, with strict schema validation and size limits. |
| R070-03 | Plugin IDs, contribution names and artifact type IDs are validated; artifact type namespaces must equal the plugin ID; reserved core names are rejected; names are unique per kind. |
| R070-04 | Reserved core-only capabilities (ledger append/rewrite, signature creation, release publication, lifecycle transition) and unknown capabilities cause manifest rejection. |
| R070-05 | Each contribution requires its matching declared capability. |
| R070-06 | `authorize` always denies core-control operations and permits other operations only with the matching capability. |
| R070-07 | Plugin load failures (invalid, unsupported, duplicate, panicking) are isolated: other plugins still load and each failure is reported with its source and reason. |
| R070-08 | The contract has no CLI/UI dependency, loads no code and states that execution/runtime and call-site enforcement are not implemented. |

Hazards, mitigations and residual limitations: see the
[EQMS-070 design](../architecture/EQMS-070.md).
