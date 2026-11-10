---
title: Dogfood documentation requirements
status: draft
related: [EQMS-016, UN-001, UN-002, UN-004, UN-005, UN-010, UN-012]
---

# EQMS-016 requirements

| ID | Requirement |
|---|---|
| R016-01 | Import each UTF-8 Markdown source as one Draft document whose body equals the source with only CR/CRLF normalized to LF; the stored body is re-read and compared. |
| R016-02 | Preserve stable identifiers (`UN-nnn`, `EQMS-nnn`, `Rnnn-nn`, `Tnnn-nn`); fail if the source and stored identifier sets differ and report them per document. |
| R016-03 | Derive titles deterministically (front-matter title, first H1, file stem); imported documents stay Draft and no lifecycle transition, approval or signature is performed. |
| R016-04 | Read and check all sources before creating any document; reject missing/unreadable/non-UTF-8 sources with nothing created; identical title+body is reported `already_imported` without a duplicate; creation failures report documents already created. |
| R016-05 | Expose `qms document import <project> <source>...` with human and schema-version-2 JSON (`document.import`) output, usage exit 2 and operation exit 1, never modifying sources; existing commands are unchanged. |
| R016-06 | Document an end-to-end create, validate, history/diff and integrity-verification workflow using only implemented commands. |
| R016-07 | Provide a CI-run check that imports this repository's documents into a fresh workspace, validates every canonical file, requires Draft status and idempotent re-import, and verifies the ledger against a declared genesis checkpoint (`checkpoint_ancestry`); failure fails CI. |
| R016-08 | Import logic lives in the Rust core without CLI/UI dependencies; documentation states the ephemeral trust limits and does not claim review, approval or compliance. |

Design/risk: [EQMS-016](../architecture/EQMS-016.md). These are draft
requirements; persistent trust anchors, approvals and signatures are not
delivered here.
