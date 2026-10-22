---
title: EQMS-090 desktop create/open requirements
status: draft
related: [EQMS-005, EQMS-010, EQMS-090, EQMS-101]
---

# EQMS-090 requirements

| ID | Requirement |
|---|---|
| R090-01 | Launching the desktop application presents Create project and Open project actions. |
| R090-02 | Create project selects a destination and invokes the native `qms init --json <path>` operation; it reports success only after a successful, expected versioned response. |
| R090-03 | Open project selects a directory and invokes `qms verify --json <path>`; it marks the project open only after verification succeeds. |
| R090-04 | The adapter passes executable arguments without shell interpolation and validates process exit status plus JSON schema version, status, and command. |
| R090-05 | Cancellation, unavailable executable, malformed response, and CLI failure do not produce success-shaped UI state and are reported clearly. |
| R090-06 | CLI work does not block the desktop UI; while it runs, the UI indicates that an operation is in progress and prevents duplicate submissions. |
| R090-07 | The Create project chooser provides an explicit New folder action that prompts for a single child-folder name, creates and selects it under the displayed directory, and reports invalid names, existing paths, and filesystem errors without overwriting data. Open project remains an existing-folder selection flow. Cancelling a prompt creates nothing; cancelling the chooser does not initialize a workspace or delete a folder already created. |
| R090-08 | Superseded in implementation boundary by R092-07/R092-11: Jewel project screens/dialogs live in feature:scaffold/jvmMain with shared feature:ui primitives; common capability contracts/state remain shared. Platform filesystem/workspace operations are injected from apps:desktop; desktop remains Compose/JVM and other targets require their own UI/capability adapters. |

## Risk applicability

Risk Management and Security Risk Management are applicable proportionately.
Creation changes local files and Git history; opening reads and verifies local
data. Directory selection, the authoritative Rust CLI, direct process argument
passing, strict response validation, explicit errors, and a UI busy state are
the controls for the identified hazards. See the [EQMS-090 design and risk
assessment](../architecture/EQMS-090.md). Local hashes do not authenticate a
signer or prevent complete local history replacement; executable packaging and
provenance remain out of scope.
