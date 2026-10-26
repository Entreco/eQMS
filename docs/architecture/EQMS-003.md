---
title: EQMS-003 canonical Rust domain model
status: draft
related: [EQMS-003, EQMS-020, EQMS-022, EQMS-030, EQMS-070]
---

# EQMS-003 canonical Rust domain model

## Boundary and contract

The model lives in `eqms_core::model`; it does not depend on the CLI, UI, Git,
SQLite, or filesystem adapters. A `ModelContract<T>` wraps one model value with
`schema_version: 1`. Deserialization rejects unsupported versions. Entity
types are versioned together by this envelope so a contract change is explicit
to external consumers.

All entity IDs use UUID values with lowercase, hyphenated serialization.
Timestamps use RFC 3339, normalized to UTC with a `Z` suffix. They remain
claims from the process clock or caller; UTC normalization does not establish
trusted time.

## Domain types

| Type | Contract fields |
|---|---|
| `Artifact` | UUID `id`, namespaced `artifact_type`, opaque JSON `extension`, `created_at` |
| `ArtifactVersion` | UUID `id`, UUID `artifact_id`, textual `version`, optional `content_hash`, `created_at` |
| `TraceLink` | UUID `id`, UUID source/target artifact IDs, textual `relationship`, `created_at` |
| `LedgerEvent` | UUID `id`, textual `event_type`, `occurred_at`, opaque JSON `payload` |
| `Release` | UUID `id`, UUID artifact-version references, `released_at` |
| `SignatureRecord` | UUID `id`, UUID `subject_id` and `signer_id`, textual algorithm and signature value, `signed_at` |

Artifact type IDs have the form `namespace:type`, with lowercase ASCII letters,
digits, dots, hyphens, and underscores in each nonempty component; both
components begin and end with a lowercase letter or digit. The type ID is open
rather than a Rust enum. `extension` is inert JSON; this core neither loads
plugin code nor interprets plugin schemas.

`LedgerEvent` is a data value, not an event store or hash-chain implementation.
`SignatureRecord` is a declaration of signature-related metadata, not a
signature operation, identity proof, or approval. Persistence, document
lifecycle, plugin runtime/API, signature verification, and release policy are
outside this issue.

## Risk assessment

Risk Management and Security Risk Management are applicable proportionately:
these public contracts can influence downstream interpretation of artifact
identity, time, traceability, release, and signature metadata.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| A consumer accepts data from an incompatible model contract | Include an explicit schema version and reject unsupported versions | Consumers must upgrade when the contract changes |
| A normalized UTC timestamp is mistaken for trusted time | Normalize wire values to UTC and document their local/caller-claim semantics | No trusted clock, timestamp authority, or clock-rollback protection |
| Plugin data is executed or treated as validated domain data | Preserve namespaced type IDs and opaque JSON without loading plugins | Consumers own plugin-specific validation |
| Signature metadata is mistaken for verified approval | Keep records declarative and exclude signing, verification, and authorization | No cryptographic or identity assurance |
| The model is mistaken for a compliance implementation | State that these are data contracts only | No process validation or regulatory compliance claim |

This design is a draft pending human review.
