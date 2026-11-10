---
title: EQMS-014 declarative module installation requirements
status: draft
related: [EQMS-010, EQMS-014, EQMS-020, EQMS-101]
---

# EQMS-014 requirements

| ID | Requirement |
|---|---|
| R014-01 | Modules are bundled declarative manifests plus templates; manifests declare schema version, id, version, dependencies, artifact types, templates and validation rules, and contain no executable content. |
| R014-02 | A manifest and its templates are fully validated before any workspace write. |
| R014-03 | `qms module add <module> <path>` creates one deterministic Draft document per artifact. |
| R014-04 | Each artifact's journal event records module id, version, artifact key and template hash; installation appends a `module.installed` event and updates `modules/manifest.json`; `qms verify` and ledger verification pass afterwards. |
| R014-05 | Repeat installation of the same version never modifies existing documents and reports `unchanged`; an interrupted installation is completed by repeating the command. |
| R014-06 | Upgrade (higher version, same major) creates only new artifacts, never overwrites documents, and reports changed-template artifacts as conflicts. |
| R014-07 | Downgrade, different-major upgrade, unmet or too-old dependencies, and unknown modules fail nonzero without workspace changes. |
| R014-08 | Installation cannot create documents outside Draft or alter lifecycle state; edited and transitioned documents are preserved. |
| R014-09 | `qms module list <path>` reports bundled and installed modules; both commands support human and schema-version-2 JSON output. |
| R014-10 | Records and templates state that modules are scaffolding, documents remain Draft, and no compliance or approval is implied. |

Hazards, mitigations and residual limitations: see the
[EQMS-014 design](../architecture/EQMS-014.md).
