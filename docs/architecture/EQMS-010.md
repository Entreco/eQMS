---
title: EQMS-010 native workspace initialization
status: draft
related: [EQMS-004, EQMS-005, EQMS-010, EQMS-101]
---

# EQMS-010 native workspace initialization

## Boundary and behavior

`qms init <path>` delegates filesystem and Git behavior to
`eqms_core::workspace::Workspace::initialize`. The CLI passes the selected path
as a process argument, not through a shell. Initialization is allowed only for
an empty directory or an existing unborn Git repository. It requires explicit
Git author and committer configuration, writes versioned workspace config,
document/module directories and manifest, a genesis journal event, and an
initial Git commit. It does not initialize SQLite, templates, signatures,
approvals, or UI state.

The output uses the schema-version-2 envelope from [EQMS-101](EQMS-101.md).
`data.path` remains the canonical workspace path. `data.created_resources` is
an array of relative paths identifying `.git/`, `.gitignore`, `.eqms/`,
`.eqms/config.json`, `.eqms/journal.jsonl`, `documents/`, `modules/`, and
`modules/manifest.json`. Hidden `.gitkeep` files support empty directories;
the derived SQLite index is not created by initialization.

Human-readable output reports the canonical workspace path and the same
relative resource list. The list is a stable CLI summary of logical resources,
not a recursive inventory of Git internals.

Failures remain nonzero and report their cause. The initializer rejects
existing data without overwriting it. Before the initial commit is attempted,
the initializer tracks exclusively created workspace files and directories and
removes those resources if initialization fails. The workspace root and unborn
`.git` repository are retained so a retry is possible; pre-existing Git state
is never removed. If the commit command fails or its outcome is uncertain,
generated workspace files are preserved rather than rolled back. Run
`qms verify <path>` to inspect the result, then correct the cause and use
`qms recover <path>` if no initial commit exists. Cleanup failures report both
the original failure and resources that may require manual repair.

## Risk assessment

Risk Management and Security Risk Management apply proportionately because
initialization creates local filesystem and Git state and relies on configured
author identity.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Existing user data is overwritten | Restrict initialization to empty directories or unborn Git repositories; test rejection and preservation | The user chooses the target path |
| Failure appears successful or removes unrelated files | Preserve nonzero errors, roll back only attempt-created resources before commit, and preserve state when commit outcome may be uncertain | Rollback cleanup can fail; use `qms verify` and supported `qms recover`, or repair manually |
| Git-inferred identity is mistaken for accountability | Require explicit author and committer configuration | Configured identity is not authenticated |
| Path is interpreted as shell syntax | Pass the path as a separate process argument | Process and executable trust remain platform assumptions |
| Created resources are omitted or misrepresented | Report a stable relative resource list in both output modes | The list is not a complete inventory of `.git` internals |
| Local commit is mistaken for approval or trusted time | Document the local consistency scope and keep signature/approval work out of init | No trusted identity, time, or compliance assurance |

This design is a draft pending human review.
