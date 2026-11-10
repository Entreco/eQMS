---
title: Role model and approval matrix
status: draft
related: [EQMS-013, EQMS-021, EQMS-022]
---

# EQMS-013: Role model and approval matrix

## Scope

Issue #10 adds a fixed role vocabulary and a config-driven approval matrix.
The Rust core evaluates whether a set of **asserted** roles satisfies the
matrix for an artifact type or a release. The native CLI exposes matrix
validation and an approval check. Out of scope: user/identity management,
authentication, signatures (EQMS-022), automatic enforcement inside
`qms document transition` and persistent project-level matrix storage. These
remain future issues, so this slice does not by itself prevent an unauthorised
approval in a project workflow.

## Roles

`author`, `reviewer`, `approver`, `qa`, `regulatory`, `security_officer`,
`release_authority`, `administrator`. Names are stable snake_case identifiers;
unknown names are rejected.

## Matrix configuration

A JSON file, schema version 1, unknown fields rejected:

```json
{
  "schema_version": 1,
  "artifacts": { "policy": ["reviewer", "approver"] },
  "release": ["qa", "release_authority"]
}
```

- `artifacts` maps an artifact type (`[a-z0-9][a-z0-9_-]*`) to required roles.
- `release` lists roles required to approve a release.
- Each role list must be non-empty and contain no duplicates.
- Matrix size is bounded (file at most 64 KiB, at most 256 artifact types).
- Requiring all listed roles is the only rule form (AND semantics); "any of"
  and minimum-count rules are not provided.

No default matrix is shipped as a compliance claim; teams supply their own.

## Evaluation

`roles::check_approval(matrix, subject, held_roles)` returns the required,
present and missing roles (in canonical role order) and `permitted` only when
nothing is missing. An artifact type absent from the matrix is **denied**
(fail closed). Empty role lists are rejected at load time, so a rule can never
be satisfied vacuously. Core code has no CLI/UI/filesystem dependency; file reading lives in the CLI.

## CLI

- `qms approval validate-matrix <file> [--json]`
- `qms approval check <file> (--artifact-type <type> | --release) --role <role>... [--json]`

`check` exits 0 only when approval is permitted; missing roles, an unknown
artifact type or an invalid matrix exit 1 with a message naming the missing
roles. JSON uses the existing schema-versioned envelope.

## Risk Management and Security Risk Management

Both apply: the feature gates quality-record approval and reads local input.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Approval proceeds without a required role | Check lists missing roles and fails nonzero; unknown artifact types denied | Not yet wired into document/release transitions |
| Misspelt role or malformed matrix silently weakens rules | Strict schema, closed role set, empty/duplicate lists rejected | Matrix content correctness is a human review responsibility |
| Caller claims roles it does not hold | Roles are documented as asserted input | No identity, authentication, or signature binding until EQMS-022 |
| Oversized or hostile config exhausts resources | Size and entry limits, no path interpretation beyond reading the given file | Local file trust is the user's |
| Same person fills several roles | Not addressed | Separation of duties is not enforced |

## Dependencies

No dependencies on open issues. EQMS-021 lifecycle and EQMS-022 signatures
will consume this model in later work.
