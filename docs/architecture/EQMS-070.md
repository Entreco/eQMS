---
title: EQMS-070 plugin API v1
status: draft
related: [EQMS-003, EQMS-014, EQMS-070]
---

# EQMS-070 plugin API v1

Issue: [#29](https://github.com/Entreco/eQMS/issues/29). Requirements:
[EQMS-070](../requirements/EQMS-070.md). Tests: [EQMS-070](../testing/EQMS-070.md).

## Scope

Defines the **contract** (`eqms_core::plugin`): a versioned plugin manifest,
a capability model, and an isolated load/validation registry for five
extension points: artifact types, commands, validators, reports and migrations.
The core has no CLI/UI dependency.

Out of scope (not implemented): executing plugin code, a runtime/sandbox, a
`qms plugin` command, plugin distribution/signing, and wiring contributions into
document, ledger or CLI flows. The API declares what a plugin *may* do; the
core does not yet invoke any plugin. Bundled declarative modules
([EQMS-014](EQMS-014.md)) are unchanged.

## Manifest (`api_version` 1)

JSON, strict (`deny_unknown_fields`), max 64 KiB: `api_version` (must equal
`1`; other values are rejected), `id` (`[a-z0-9-]`, max 64, not a reserved
core name), `version` (`MAJOR.MINOR.PATCH`), `title`, `capabilities`, and
`contributions`:

| Extension point | Entry fields | Required capability |
|---|---|---|
| `artifact_types` | `id` (`<plugin-id>:<type>`), `title` | `artifact-types.define` |
| `commands` | `name`, `title` | `commands.register` |
| `validators` | `name`, `artifact_type`, `title` | `artifacts.read` |
| `reports` | `name`, `title` | `artifacts.read` |
| `migrations` | `name`, `artifact_type`, `from_version`, `to_version` (`to > from`) | `artifacts.migrate-drafts` |

Artifact type IDs reuse the EQMS-003 `namespace:type` form; the namespace must
equal the plugin ID so plugins cannot claim another plugin's or the core's
types. Names are lowercase `[a-z0-9-]`; they are unique per kind within a
plugin and are qualified as `<plugin-id>/<name>`. A contribution is accepted
only if its required capability is declared (least privilege). Validators and
migrations that name an artifact type must target one the plugin defines.

## Capability model

Grantable: `artifact-types.define`, `commands.register`, `artifacts.read`,
`artifacts.migrate-drafts`. **Reserved core-only controls** that no plugin can
request or be granted: `ledger.append`, `ledger.rewrite`, `signature.create`,
`release.publish`, `lifecycle.transition`. A manifest that lists a reserved or
unknown capability is rejected as a whole. `authorize(manifest, operation)` is
the single gate: operations on core controls (ledger, signature, release,
lifecycle) are always denied regardless of declared capabilities; other
operations require the matching capability. Declared capability is necessary,
not sufficient: enforcement at call sites is future work and must route through
`authorize`.

## Load isolation and reporting

`PluginRegistry::load` takes `(source, manifest text)` pairs. Each is parsed
and validated independently (including under `catch_unwind`); a failure yields
a `LoadFailure {source, error}` and never prevents other plugins from loading
or changes registry state. Duplicate plugin IDs fail the later source.
Results are returned in input order as a `LoadReport` (`loaded`, `failures`).
Source labels are caller-supplied and not interpreted as paths.

## Risk assessment

Risk Management and Security Risk Management apply proportionately: the
contract governs what untrusted extension code could later be allowed to do.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Plugin bypasses ledger/signature/release controls | Reserved capabilities cannot be declared; `authorize` always denies core-control operations | Call-site enforcement is not yet wired; no runtime exists |
| Plugin over-claims privileges | Contributions require matching declared capability; unknown capabilities rejected | Capability set is coarse |
| Namespace squatting/impersonation | Type namespace must equal plugin ID; core names reserved; duplicate IDs rejected | No publisher authentication or signing |
| Malformed/hostile manifest crashes or poisons loading | Size cap, strict schema, per-plugin isolation incl. panic capture, no state change on failure | Panic isolation is not a security sandbox |
| API drift breaks plugins silently | `api_version` required; unsupported versions rejected | Only v1 exists; no compatibility policy for v2 |
| Plugin treated as compliance evidence | Docs state this is a contract only | Human review remains required |

No approvals are claimed. This design is a draft pending human review.
