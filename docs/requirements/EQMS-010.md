---
title: EQMS-010 qms init requirements
status: draft
related: [EQMS-005, EQMS-010, EQMS-101]
---

# EQMS-010 requirements

| ID | Requirement |
|---|---|
| R010-01 | `qms init <path>` creates versioned workspace config, document/module directories and manifest, genesis journal event, and initial Git commit. |
| R010-02 | Initialization requires explicitly configured Git author and committer identity and does not claim that the identity is verified. |
| R010-03 | Paths containing spaces work; existing non-empty directories and initialized workspaces fail nonzero without overwriting existing data. |
| R010-04 | Human-readable output identifies the canonical workspace path and lists created logical resources. |
| R010-05 | Schema-version-2 JSON output preserves `data.path` and reports the relative logical resource list in `data.created_resources`. |
| R010-06 | Pre-commit failures report their cause and remove only resources created by this attempt; if commit execution has started or rollback fails, preserve the unborn `.git` state and provide explicit verification/recovery guidance. |
| R010-07 | Integration tests cover creation, reopen/genesis verification, repeated init, existing data, paths with spaces, resource output, and I/O failure. |
| R010-08 | Initialization does not require or create a database, standards templates, signatures, approvals, or UI state. |

The applicable hazards, mitigations, and residual limitations are recorded in
the [EQMS-010 design](../architecture/EQMS-010.md).
