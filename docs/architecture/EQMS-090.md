---
title: EQMS-090 desktop project creation and opening
status: draft
related: [EQMS-005, EQMS-010, EQMS-090, EQMS-101]
---

# EQMS-090 desktop project creation and opening

## Scope

The first desktop milestone is a Compose/JVM Desktop landing window with
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
command. It requires the schema-version-2 envelope from
[EQMS-101](EQMS-101.md) and reads the project path from `data.path`. Nonzero
exits, structured CLI errors, malformed output, and executable launch failures
are shown as failures. A cancelled directory chooser performs no operation.
The interface remains responsive while the CLI runs.

The executable is resolved from `EQMS_QMS_PATH` when configured, otherwise
`qms` on `PATH`. Gradle desktop launches (`run` and `hotRun`) supply the absolute
repository `target/release/qms` path (`qms.exe` on Windows) when no nonblank
override is set. Build the CLI first; Gradle does not build it automatically.
Distribution and signing of the native executable are not covered by this
milestone.

The shared Compose directory browser includes an explicit New folder button.
It prompts for a single child name and uses exclusive
directory creation under the displayed directory. Blank names, absolute paths,
separators, and dot/dot-dot names are rejected. Existing paths are never reused
or overwritten; validation and filesystem errors remain visible in the dialog.
After creation the browser navigates into the new folder. Cancelling the
prompt creates nothing; cancelling the chooser does not invoke init or delete a
folder already created. Open has no folder-creation action.

The `core:ui` Kotlin Multiplatform module contains shared UI components: the
screen, directory browser, folder-name prompt, and error rendering in common
Compose code. Its
suspending capability interface uses strings and directory DTOs, not Java paths.
Desktop implements the filesystem and native CLI adapters on JVM, off the UI
thread. JVM and Wasm compilation verify reuse; no browser filesystem or service
adapter is provided here. Compose Desktop still requires JVM; this is not a
Kotlin/Native Windows/macOS/Linux application.

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
| Folder name targets another directory or overwrites data | Accept one child name only and use exclusive directory creation; show errors in the chooser | A created empty directory remains if the user subsequently cancels |

This design is a draft and does not represent human review, approval, or
compliance evidence.
