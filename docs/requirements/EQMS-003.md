---
title: EQMS-003 canonical artifact model requirements
status: draft
related: [EQMS-003, EQMS-020, EQMS-022, EQMS-030, EQMS-070]
---

# EQMS-003 requirements

| ID | Requirement |
|---|---|
| R003-01 | The Rust core exposes artifact, artifact-version, trace-link, ledger-event, release, and signature-record types independent of CLI, UI, and storage adapters. |
| R003-02 | A shared external contract emits schema version 1 and rejects unsupported schema versions during deserialization. |
| R003-03 | Entity identifiers serialize as lowercase, hyphenated UUID strings. |
| R003-04 | Timestamps serialize as RFC 3339 UTC values with a `Z` suffix; equivalent instants normalize to the same wire representation. |
| R003-05 | Plugin artifact types use validated namespaced `namespace:type` identifiers and round-trip opaque JSON extension data without loading or validating plugin code/schemas. |
| R003-06 | Ledger events and signature records are data models only; this issue does not add event storage/hash-chain behavior, signature cryptography/verification, identity proof, or approval. |
| R003-07 | Model documentation states that timestamps are not trusted time and model presence does not establish compliance. |

Applicable risks and residual limitations are recorded in the
[EQMS-003 design](../architecture/EQMS-003.md).
