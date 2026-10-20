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

## Risk applicability

Risk Management and Security Risk Management are applicable proportionately.
Creation changes local files and Git history; opening reads and verifies local
data. Directory selection, the authoritative Rust CLI, direct process argument
passing, strict response validation, explicit errors, and a UI busy state are
the controls for the identified hazards. See the [EQMS-090 design and risk
assessment](../architecture/EQMS-090.md). Local hashes do not authenticate a
signer or prevent complete local history replacement; executable packaging and
provenance remain out of scope.
