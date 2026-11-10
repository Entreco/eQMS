---
title: EQMS-014 declarative module installation
status: draft
related: [EQMS-004, EQMS-005, EQMS-010, EQMS-020, EQMS-023, EQMS-101]
---

# EQMS-014 declarative module installation

Issue: [#11](https://github.com/Entreco/eQMS/issues/11). Requirements:
[EQMS-014](../requirements/EQMS-014.md). Tests: [EQMS-014](../testing/EQMS-014.md).

## Boundary and behavior

`qms module add <module> <path>` delegates to `eqms_core::module`. The core has
no CLI or UI dependency. Modules are **bundled declarative data** compiled into
the binary: a JSON manifest plus Markdown templates. No module contains
executable code, commands, or externally loaded paths; plugins are out of scope
([EQMS-070](https://github.com/Entreco/eQMS/issues/29)). `qms module list <path>`
reports bundled modules and installed versions.

### Manifest (schema version 1)

`schema_version`, `id` (lowercase `[a-z0-9-]`, max 64), `version`
(`MAJOR.MINOR.PATCH`), `title`, `dependencies` (`{id, min_version}`; same
major required), `artifacts` (`{key, type, title, template}`) and `validation`
(`{artifact, required_headings}`). Templates are referenced by bundled name.
Manifests and templates are validated before any workspace write: unique keys,
known references, every template present, controlled-Markdown validity, and
required headings.

### Installation

1. Under the workspace lock, verify the workspace and read `modules/manifest.json`.
2. Installed version absent → install; equal → complete missing artifacts
   only; higher bundled with the same major → upgrade; lower or different
   major → explicit error. Missing or older dependencies → explicit error
   (nothing is auto-installed).
3. For each artifact, create a **Draft** document through the existing
   document path (`document.created` event on its own draft ref) with a
   deterministic ID `DOC-` + first 32 hex of
   `sha256("eqms-module-artifact\0<module>\0<key>")`. The journal payload
   records `module_id`, `module_version`, `artifact_key`, `template_hash`.
4. After all artifacts exist, update `modules/manifest.json` and append a
   `module.installed` journal event (module, version, previous version,
   artifact document IDs) in one baseline commit using the existing journal
   mechanism, so `qms verify`, `qms recover` and ledger verification cover it.

Existing artifact documents are never modified, so user edits are never
overwritten. On upgrade, an existing artifact whose recorded `template_hash`
differs from the new template is reported in `conflicts` (not applied); new
artifacts are created. Installation only creates Draft documents and never
transitions them.

Drafts are created before the manifest update, so the manifest declares a
module installed only once all its drafts exist. A failure midway leaves valid
drafts; repeating the command completes the remainder and reports `completed`.

### Output

Schema-version-2 JSON `data`: `module`, `version`, `previous_version`,
`status` (`installed|upgraded|completed|unchanged`), `created`, `preserved`,
`conflicts` (each: `key`, `type`, `document_id`). Human output lists the same.

### Bundled content

One starter module, `qms-starter` 1.0.0, supplies generic policy and procedure
scaffolds to exercise the mechanism. It is scaffolding, not an implemented
process, not standards-derived and not compliance evidence. Standards modules
are separate issues (EQMS-015).

## Risk assessment

Risk Management and Security Risk Management apply proportionately: the
feature writes controlled documents and audit records from bundled content.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Installation overwrites user edits | Existing documents are never written; repeat/upgrade only creates missing drafts; template drift is a reported conflict | Users merge template changes manually |
| Upgrade/downgrade silently breaks content | Downgrade, different major, unmet dependencies are explicit errors | Minimal version scheme; no migrations |
| Installation bypasses lifecycle/audit | Reuses document creation: Draft only, journal event per artifact, `module.installed` event, covered by verify/recover/ledger verification | Local journal is not trusted time or authenticated identity |
| Hostile manifest/template (traversal, injection) | Bundled-only data; strict id/key/template allow-lists; templates validated as controlled Markdown; no paths/commands from manifests | Third-party/signed module distribution not provided |
| Partial failure leaves inconsistent state | Drafts first, manifest last; retry completes; interrupted manifest update recoverable with `qms recover` | Orphan valid drafts may remain after failure |
| Scaffold mistaken for compliance | Template text and docs state scaffolds are unreviewed; documents stay Draft | Human review remains required |
| Concurrent installs | Workspace lock around each step; duplicate artifact IDs fail on the existing ref | Callers may see an explicit error and retry |

No approvals are claimed. This design is a draft pending human review.
