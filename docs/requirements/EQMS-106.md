---
title: EQMS-106 document metadata requirements
status: draft
related: [EQMS-020, EQMS-104, EQMS-106]
---

# EQMS-106 requirements

| ID | Requirement |
|---|---|
| R106-01 | The Rust core encodes controlled Markdown schema v2 with fixed field order, compact JSON and LF delimiters; strict validation rejects noncanonical bytes, unknown/missing/duplicate fields, schema versions other than 2 (including v1) and invalid values. Encoding is deterministic and round-trips. |
| R106-02 | Page metadata is bounded and validated: size A4/Letter, orientation portrait/landscape, margins 5.0-50.0 mm in 0.1 mm steps with minimum content area, header/footer at most 120 characters with only `{title}`, `{page}`, `{total}` placeholders and no control characters. Title rules are unchanged. New documents use the documented defaults. |
| R106-03 | A metadata-only change creates a new revision with a different content hash, a `document.saved` journal record and a visible revision diff; saving identical title, body and metadata creates no revision. Omitted CLI metadata options keep the previous values. |
| R106-04 | Rendering, viewing the Metadata panel and reading history never mutate documents, refs or journals. |
| R106-05 | A right-rail Metadata panel edits title, page size, orientation, margins and header/footer templates with inline validation, is keyboard accessible, works in both themes and narrow windows, and edits the tab buffer only. Changes persist only through explicit Save; existing Save, conflict, dirty and close guards cover metadata edits. |
| R106-06 | The panel shows document ID, revision, content hash and status read-only exactly as saved. Historical and non-Draft records show all metadata read-only. |
| R106-07 | The project-scoped page-setup preference, its capability methods and its dialog are removed; pagination and preview use the open tab's document metadata. |
| R106-08 | Existing EQMS-104 records and tests are updated to match this behavior; Rust, JVM, native and Wasm regressions pass or exclusions are recorded. |

Design, hazards and limitations (including unsupported v1 documents) are in the
[EQMS-106 design](../architecture/EQMS-106.md). These records are draft, not
approved compliance evidence.
