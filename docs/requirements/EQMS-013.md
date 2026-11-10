# EQMS-013: Role model and approval matrix requirements

These requirements refine issue #10. Roles are asserted inputs; this slice
does not authenticate users or enforce approvals inside document workflows.

| ID | Requirement |
|---|---|
| REQ-013-001 | The core shall define exactly the roles Author, Reviewer, Approver, QA, Regulatory, Security Officer, Release Authority and Administrator with stable identifiers, and reject unknown role names. |
| REQ-013-002 | Approval rules shall be loaded from a schema-versioned configuration mapping artifact types and releases to required roles, rejecting unknown fields, unsupported versions, empty or duplicate role lists, invalid artifact type names and oversized input. |
| REQ-013-003 | Approval evaluation shall report required, present and missing roles and permit approval only when no required role is missing. |
| REQ-013-004 | Approval for an artifact type absent from the matrix shall be denied. |
| REQ-013-005 | The native `qms` CLI shall provide matrix validation and an approval check with human-readable and versioned JSON output; missing roles and invalid configuration shall return nonzero errors identifying the cause. |
| REQ-013-006 | Core role logic shall not depend on CLI, UI, or filesystem code. |
| REQ-013-007 | User-visible output and documentation shall not claim authenticated identity, signatures, separation of duties, or workflow enforcement. |
