---
title: Dogfood project documentation import and integrity check
status: draft
related: [EQMS-010, EQMS-016, EQMS-020, EQMS-023, EQMS-031]
---

# EQMS-016: Dogfood project documentation

## Refined scope

Use the native `qms` CLI to manage this repository's own Markdown: User Needs,
design decisions, requirements, test specifications, operations notes, the
roadmap (`PLANNING.md`) and the delivery process (`AGENTS.md`).

In scope:

1. `qms document import <project> <source>...`: a lossless, idempotent import of
   existing Markdown files as Draft documents (Rust core `eqms_core::import`,
   native CLI adapter).
2. A documented create / validate / history / diff / integrity workflow:
   [operations/EQMS-016](../operations/EQMS-016.md).
3. A CI check, `scripts/dogfood-check.sh`, that imports the repository's
   documents into a fresh workspace, validates every canonical file and runs
   ledger verification against a declared checkpoint.

Out of scope: lifecycle transitions of imported documents (EQMS-021 mechanics
exist but no real review happens here), signatures, a persistent shared
documentation workspace, metadata beyond Draft v2, and replacing the Markdown
files in this repository. The repository files remain the editing source.

## Import contract

`eqms_core::import` has no CLI/UI dependency. Per source file:

- Bytes must be valid UTF-8. Line endings normalize to LF (the existing
  canonical body rule); nothing else is changed, so original front matter,
  headings and stable identifiers remain verbatim in the body.
- Title: front-matter `title:` (optionally quoted), else first `# ` heading
  outside front matter, else file stem. A candidate that is blank or has
  control characters is skipped.
- The document is created through `Documents::create` (Draft, isolated draft
  ref, journal event). No transition is applied.
- Stable identifiers (`UN-nnn`, `EQMS-nnn`, `Rnnn-nn`, `Tnnn-nn`) are extracted
  from the source and from the stored body; the sets must be equal and the body
  must equal the normalized source, otherwise the import fails explicitly.
- Idempotency: an existing document with identical title and body is reported
  `already_imported` with its ID instead of creating a duplicate.
- All sources are read and checked before any document is created. A failure
  during creation reports the documents already created (they are not rolled
  back; drafts are append-only history).

`document.import` JSON `data.imported[]` entries: `source`, `source_hash`
(SHA-256 of the original bytes), `id`, `title`, `status`, `revision`,
`content_hash`, `identifiers`, `line_endings_normalized`, `already_imported`.
Usage errors exit 2; operation failures exit 1. Source files are only read.

## CI check

`scripts/dogfood-check.sh` (run after the release build) creates a throwaway
workspace with explicit Git identity, pins the genesis commit as the declared
trust baseline, imports the document set, re-imports to prove idempotency,
validates each saved canonical file with `qms document validate -`, requires
every status to be Draft, then runs `qms verify` and
`qms audit verify-ledger --checkpoint <genesis>` and requires
`trust: checkpoint_ancestry`. Any failure fails CI.

The workspace is regenerated per run, so this proves that the repository's
documents stay importable and that the shipped integrity tooling verifies the
result. It is not a persistent or independently held audit trail; a long-lived
documentation workspace with an externally stored checkpoint is future work.
Git-backed history and the hash-chained journal are the implemented EQMS-023/031
mechanisms; nothing planned beyond them is claimed.

## Risk Management and Security Risk Management

Both apply: imported text becomes quality-record content and reads local files.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Import silently alters or drops content or IDs | Body/identifier equality checked against source and re-read stored document; tests | Only line-ending normalization is applied and reported |
| Imported text mistaken for reviewed/approved | Draft status only; no transition; docs and output say so | Real review and approval remain manual and future |
| Duplicate documents on re-run | Title+body idempotency check | Edited sources import as a new document; use `save` for revisions |
| Partial batch leaves some drafts | Pre-validate all sources; report created documents on failure | Created drafts persist (append-only) |
| Untrusted file content or path abuse | Files are read only; content never executed; paths passed as arguments, not through a shell | Caller chooses which files to import |
| CI result overstates trust | Checkpoint is self-declared and ephemeral; documented | No identity, trusted time or persistent history |

Manual review and testing are required. This design is a draft, not approved
compliance evidence.
