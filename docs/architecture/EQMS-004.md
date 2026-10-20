---
title: Rust foundation design and risk assessment
status: draft
related: [EQMS-004, UN-009, UN-010]
---

# EQMS-004 — Native foundation

Issue: [#5](https://github.com/Entreco/eQMS/issues/5).

This document records the initial EQMS-004 foundation boundary. Subsequent
workspace and index behavior is specified in
[EQMS-005 / EQMS-002](EQMS-005.md); the initial statements below about a
standard-library-only core and no filesystem mutations predate those tickets.

## Design

The root Cargo workspace contains `core/rust` (`eqms-core`) and `apps/cli`
(`eqms-cli`, binary `qms`). The core exposes only a version API initially and
depends solely on the standard library. Artifact models belong to EQMS-003.
The CLI depends on the core, clap, serde and serde_json. Kotlin paths remain
unchanged. Rust 2024 is used; the actual tested compiler is recorded in evidence,
not claimed as a minimum supported compiler.

No arguments display help successfully. `--help`/`-h` show human-readable help;
`--version`/`-V` show the core version. Global `--json` makes version success and
usage errors structured, regardless of flag order. Help remains human-readable.
`--json` alone is a usage error: request version or help explicitly.

JSON schema version 1:

```json
{"schema_version":1,"status":"ok","version":"0.1.0"}
```

```json
{"schema_version":1,"status":"error","error":{"code":"INVALID_ARGUMENT","message":"..."}}
```

Successful help/version use stdout and exit 0. Usage errors use stderr and exit
2 with empty stdout. Output-write failures exit 1; stderr diagnostics are
best-effort if the error stream itself is unavailable. No command creates files.
`init`, storage, signatures, approvals, MCP and module installation are excluded.

## Risk and security-risk applicability

Both assessments apply proportionately to the foundation. This feature does not
make clinical decisions or modify controlled records. It does not establish
product compliance or replace a formal product risk-management process.

| Concern | Control / verification | Remaining limitation |
|---|---|---|
| CLI rules leak into core | Standard-library-only core; review Cargo dependency tree | Future changes need review |
| Automation mistakes errors for success | Stable code, schema, stream and exit tests | Consumers must validate schema |
| Unexpected mutation | Isolated-directory CLI tests; no storage APIs | Future commands require new assessment |
| Dependency compromise | Cargo.lock, minimal direct dependencies, review updates | Locking does not prove dependency safety |
| Misleading performance claims | Release-build measurements with raw samples and environment | No universal latency guarantee |
| False assurance from documentation | Draft records, pending manual checklist | Human review/testing still required |

No cryptography, identity assurance, network service, document control or audit
journal is implemented by this ticket. Subsequent tickets must assess those risks.
