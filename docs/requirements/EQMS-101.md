---
title: EQMS-101 CLI JSON output requirements
status: draft
related: [EQMS-004, EQMS-005, EQMS-090, EQMS-101]
---

# EQMS-101 requirements

| ID | Requirement |
|---|---|
| R101-01 | Every native `qms --json` response, including version, success, usage error, and operational failure, uses the documented schema-version-2 envelope with `status`, `command`, `timestamp_unix_ms`, `findings`, `references`, `data`, and `error`. |
| R101-02 | Successful command-specific values are preserved under `data`; version output uses a `data.version` string. Failure responses set `data` to null and provide a structured `error.code` and `error.message`. |
| R101-03 | `findings` and `references` are arrays in every JSON response; commands that do not populate them return empty arrays. The timestamp is signed Unix milliseconds from the local system clock and is not represented as trusted time. |
| R101-04 | JSON usage errors remain nonzero with exit code 2; operational failures remain nonzero with exit code 1. Success is written to stdout, errors to stderr, and failed commands do not emit success output. |
| R101-05 | Human-readable command output and help behavior remain unchanged when `--json` is not requested. |
| R101-06 | The desktop workspace and document CLI adapters accept only schema version 2, validate the shared envelope, and decode command-specific values from `data`; malformed, mismatched, or unsupported responses fail explicitly. |
| R101-07 | The JSON contract and its compatibility break are documented; downstream schema-version-1 consumers are not silently treated as version 2. |

The applicable hazards, mitigations, and residual limitations are recorded in
the [EQMS-101 design](../architecture/EQMS-101.md).
