---
title: Dogfood documentation test mappings
status: draft
related: [EQMS-016]
---

# EQMS-016 tests

Requirements: [EQMS-016](../requirements/EQMS-016.md).

| ID | Mapping | Expected automated behavior |
|---|---|---|
| T016-01 | R016-01, R016-02 | Core: import of a document with front matter, CRLF and `UN-`/`EQMS-`/`R`/`T` identifiers stores body == LF-normalized source and identical identifier sets; Draft status; source hash is SHA-256 of original bytes. |
| T016-02 | R016-03 | Core: title from front matter (plain/quoted), first H1 (not inside front matter), file-stem fallback incl. blank/control-character titles; no lifecycle events beyond creation. |
| T016-03 | R016-04 | Core: a batch with one invalid-UTF-8 source creates nothing; repeat import returns `already_imported` with the same ID and no new revision/document; changed content creates a new document. |
| T016-04 | R016-05, R016-01 | CLI: human and JSON `document.import` output, multiple sources, usage error (no sources) exit 2, missing file/non-workspace exit 1 with empty stdout; source files unchanged; existing document commands pass regressions. |
| T016-05 | R016-07 | `scripts/dogfood-check.sh` passes against the release binary on this repository (import, canonical validation, idempotency, `qms verify`, `verify-ledger` with `checkpoint_ancestry`) and fails when a source is made invalid. |
| T016-06 | R016-06, R016-08 | Operations workflow commands are copy-exercised by the script/tests; documentation review confirms Draft status and trust limits are stated. |

Run focused core/CLI import tests and the dogfood script, then Rust CI:
formatting, Clippy with denied warnings, locked workspace tests and release
build. Evidence records the exact committed source revision and actual results.

## Manual checks (pending)

- [ ] Review design/risk, requirements, code, tests and evidence.
- [ ] Import the repository documents into a persistent workspace and compare
      a few bodies and identifiers by eye.
- [ ] Confirm imported documents show as Draft and the docs claim no approval.
- [ ] Confirm the CI job fails for a deliberately broken source document.
