---
title: Desktop display of CLI usage errors
status: draft
related: [EQMS-090, EQMS-101, EQMS-107]
---

# EQMS-107: Desktop display of CLI usage errors

## Problem

Native `qms` usage errors (exit 2, code `INVALID_ARGUMENT`) are emitted before
a command is resolved, so the v2 JSON envelope has `command: null`. The desktop
checked the envelope command before status, so such errors (for example an
outdated `qms` rejecting `--page-size`) were shown as "qms returned a response
for an unexpected command." and hid the cause.

## Design

`JsonObject.isCliUsageError(exitCode)` (desktop `CliJsonResponse.kt`) is true
only when the exit code is nonzero, status is `error`, command is null and the
error code is `INVALID_ARGUMENT`. Document operations and workspace commands
skip the command-mismatch rejection for that case only, so the existing
nonzero-exit path reports the CLI message. All other mismatches (any non-null
different command, success responses, other error codes, exit code 0) are still
rejected. Envelope validation is unchanged. No CLI change.

## Risk Management and Security Risk Management

Applicable: the desktop displays text from an external process.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| A wrong-command response accepted as success | Relaxation limited to nonzero-exit error envelopes with null command and `INVALID_ARGUMENT`; success path unchanged | A malicious executable can still send arbitrary error text; qms is a trusted local executable |
| Misleading error hides stale binary | CLI message now shown | Desktop does not detect a stale `qms` version |
