---
title: EQMS-094 desktop responsiveness requirements
status: draft
related: [EQMS-094, EQMS-092, EQMS-102]
---

# EQMS-094 requirements

Issue: #65.

| ID | Requirement |
|---|---|
| R094-01 | Project-selection-to-first-document-list and History-toggle-to-first-usable-history shall be measured end-to-end, with verification, native/list/history work, process counts, decoding, and UI readiness reported separately where observable. |
| R094-02 | Measurements shall identify versioned synthetic dataset shape, document/revision counts, cold/warm condition, release binary/revision, platform, hardware, sample count, p50/p95, and excluded work. Explicit p95 budgets and regression thresholds shall be fixed from the baseline before optimization. |
| R094-03 | Project verification, document listing, and History shall retain native authority and existing integrity, audit, revision, lifecycle, and provenance validation. |
| R094-04 | History revisions and lifecycle events shall be decoded from one consistent native verified snapshot, without duplicate full-chain verification in the normal desktop path. |
| R094-05 | Project workspace, Project list, and History shall provide immediate panel-scoped progress and explicit errors; search preparation shall not delay first document-list content. Mutations and destructive navigation remain guarded. |
| R094-06 | Async results shall be scoped to their originating project, tab, and source revision; cancellation or stale responses shall not replace another selection or unsaved editor buffers. |
| R094-07 | Existing save/conflict/close, caret/scroll, independent-tab, read-only lifecycle, and exact provenance behavior shall remain unchanged. No cache may label stale data as newly verified. |
| R094-08 | Focused latency measurements and affected Rust, JVM, native CLI, and Wasm regressions shall be reproducible. Generated evidence shall identify tested revision/environment/commands/outcomes/limitations; manual review/testing remains pending until human completion. |

Risk Management and Security Risk Management apply proportionately; hazards,
controls and residual limitations are documented in the
[design/risk assessment](../architecture/EQMS-094.md).
