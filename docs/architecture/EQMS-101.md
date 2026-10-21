---
title: Native CLI JSON response contract
status: draft
related: [EQMS-004, EQMS-005, EQMS-090, EQMS-101]
---

# EQMS-101 — Native CLI JSON response contract

## Scope and compatibility

Every native `qms --json` result uses JSON schema version 2, including
`--version`, command successes, usage errors, and operational failures. Version
2 is required because the existing schema-version-1 success payloads are not
consistent and the shared envelope moves command-specific data under `data`.
Human-readable output and help remain unchanged. The native CLI is
authoritative; consumers must reject unsupported schema versions.

## Envelope

Every JSON response contains these fields:

| Field | Meaning |
|---|---|
| `schema_version` | Integer `2`. |
| `status` | `ok` for success, `error` for failure. |
| `command` | Canonical command name (for example, `version`, `init`, `index.rebuild`, or `document.create`); `null` when argument parsing cannot identify a command. |
| `timestamp_unix_ms` | Signed integer milliseconds since the Unix epoch, recorded when the response is constructed. This is a local system-clock claim, not trusted time. |
| `findings` | Array reserved for command findings; currently empty where no command emits findings. |
| `references` | Array reserved for trace references; currently empty where no command emits references. |
| `data` | Command-specific success payload, or `null` on failure. Version output uses `{"version":"..."}`. |
| `error` | Structured `{ "code": "...", "message": "..." }` on failure, or `null` on success. |

Example success:

```json
{
  "schema_version": 2,
  "status": "ok",
  "command": "init",
  "timestamp_unix_ms": 1792600000000,
  "findings": [],
  "references": [],
  "data": { "path": "/workspace" },
  "error": null
}
```

Example failure:

```json
{
  "schema_version": 2,
  "status": "error",
  "command": null,
  "timestamp_unix_ms": 1792600000000,
  "findings": [],
  "references": [],
  "data": null,
  "error": { "code": "INVALID_ARGUMENT", "message": "..." }
}
```

Successful JSON is written to stdout. JSON errors are written to stderr; usage
errors exit 2 and operational failures exit 1. Non-JSON response streams and
exit behavior are unchanged. Timestamps may be skewed or changed and must not
be used as evidence of trusted ordering or identity. Empty findings and
references mean that the command did not provide any; they do not imply that a
compliance assessment was performed.

## Risk assessment

Risk Management and Security Risk Management apply proportionately because
automation and the desktop shell consume command results and may make workflow
decisions from them.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Consumers misread a result after the envelope changes | Bump to schema version 2, document the exact shape, and reject unsupported versions in CLI consumers | Older external consumers must be updated before using this contract |
| A failure is interpreted as success or silently discarded | Keep explicit status/error fields, nonzero exit codes, and stderr routing; test both usage and operation failures | A consumer that ignores exit status and payload validation remains unsafe |
| Timestamps are treated as trusted time | Use an explicitly named Unix-millisecond local timestamp and document its limits | Clock skew, rollback, and host control are not addressed |
| Empty findings/references are mistaken for evidence of completeness | Document that these arrays are empty until a command explicitly populates them | This contract does not assess product or process compliance |
| Error text reveals local paths or implementation details | Serialize errors as data rather than interpolating them into JSON; keep current CLI error messages and limit exposed details to those messages | Error-message confidentiality is command-specific and requires separate review |

## Consumers and traceability

The native desktop workspace and document adapters consume `qms --json`; they
must validate schema version 2 and read command payloads from `data`. EQMS-005
and EQMS-090 retain the workspace behavior while adopting the new envelope.
EQMS-101 does not establish trusted timestamps, identity, or compliance
evidence.
