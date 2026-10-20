---
title: EQMS-090 desktop project creation and opening
status: draft
related: [EQMS-005, EQMS-010, EQMS-090, EQMS-101]
---

# EQMS-090 desktop project creation and opening

## Scope

The first desktop milestone is a native Compose Desktop landing window with
**Create project** and **Open project** actions. The user chooses a directory;
the app delegates creation to `qms init --json <path>` and opening to
`qms verify --json <path>`. The app reports a project as open only after the
native CLI exits successfully and returns the expected versioned JSON result.

Browsing/search, traceability, release status, templates, project packaging, and
the web client are follow-on work, not part of this milestone.

## Boundary and behavior

Rust `qms` remains the authority for workspace rules. Kotlin owns only UI state
and process adaptation; it does not implement initialization, journal
verification, or Git behavior. The process adapter passes the executable,
`--json`, command, and path as separate process arguments (never through a
shell), then validates the exit status and JSON `schema_version`, `status`, and
command. Nonzero exits, structured CLI errors, malformed output, and executable
launch failures are shown as failures. A cancelled directory chooser performs
no operation. The interface remains responsive while the CLI runs.

The executable is resolved from `EQMS_QMS_PATH` when configured, otherwise
`qms` on `PATH`. Distribution and signing of the native executable are not
covered by this milestone.

## Risk assessment

Risk Management and Security Risk Management are applicable proportionately.
Creating a project writes local files and a Git commit; opening reads and
verifies local data; both depend on invoking the intended CLI with the selected
path.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| User creates a workspace in the wrong directory or existing data is overwritten | Use a directory chooser and delegate to `qms init`; report success only for a validated successful response | The user is responsible for selecting the intended destination |
| Invalid or unrelated directory is presented as an open project | Delegate to `qms verify`; update open-project UI state only on a successful, expected response | Local verification does not prove signer identity or prevent full-history replacement |
| Selected path is interpreted as shell syntax or a different argument | Pass process arguments directly without shell interpolation; test paths containing spaces | The OS process API and trusted executable remain part of the platform boundary |
| Missing or substituted executable, or malformed response, looks like success | Allow an explicit executable path, validate JSON schema/status/command, and surface launch/protocol errors | Packaging, executable provenance, and supply-chain assurance are out of scope |
| Slow CLI operation blocks interaction | Run process calls off the UI thread and expose a busy state | No cancellation of an in-flight CLI process in this milestone |

This design is a draft and does not represent human review, approval, or
compliance evidence.
