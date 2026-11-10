---
title: EQMS-106 document metadata panel and schema v2
status: draft
related: [EQMS-020, EQMS-023, EQMS-083, EQMS-092, EQMS-104, EQMS-106]
---

# EQMS-106: Document metadata panel with tracked, hashed metadata

Issue: #70.

## Scope

Document metadata (title and page setup) becomes part of the controlled
document, so it is covered by the content hash, recorded in history/journal and
visible in revision diffs. A right-side **Metadata** panel edits it for Draft
documents and shows computed identity read-only.

## Approved decisions and boundaries

- No backwards compatibility before the first public release: controlled
  Markdown **schema v2** replaces v1. v1 files are not read, migrated or kept
  verifiable; existing v1 workspaces must be recreated. This is an explicit
  limitation to revisit before the first public release.
- Page setup moves from per-machine Java Preferences into the document. The
  project-scoped preference and its dialog are removed.
- No DOCX/PDF export, signatures/approvals, custom metadata fields or rich
  editor. The journal remains as implemented; planned audit controls are not
  claimed.

## Schema v2 (Rust, `eqms_core::markdown`)

One compact JSON line in fixed field order, between `---` delimiters:

```text
---
{"schema_version":2,"id":"DOC-...","title":"T","page_size":"A4","orientation":"portrait","margin_top_mm":25.4,"margin_right_mm":25.4,"margin_bottom_mm":25.4,"margin_left_mm":25.4,"header":"{title}","footer":"Page {page} of {total}","status":"Draft"}
---
Markdown body
```

Defaults for new documents: A4, portrait, 25.4 mm margins, header `{title}`,
footer `Page {page} of {total}` (identical to the EQMS-104 defaults).

Validation (`PageSetup::validated`): `page_size` is `A4` or `Letter`; `orientation` is
`portrait` or `landscape`; each margin is finite, within 5.0-50.0 mm and a
multiple of 0.1 mm (so the encoding is exact and stable); horizontal margins
leave at least 40 mm and vertical margins at least 60 mm of content area;
header/footer are at most 120 characters, contain no control characters and the
only braces allowed are the placeholders `{title}`, `{page}` and `{total}`.
Title rules are unchanged. Unknown, missing or duplicate fields and any other
schema version are rejected. Strict validation re-encodes and requires exact
byte equality, as in EQMS-020; history verification always uses the strict path.

Computed values are never stored in editable fields: the document ID is stored
(unchanged), revision and content hash come from history, and `status` remains
the fixed `Draft` of controlled Markdown; lifecycle status is shown from the
verified lifecycle chain. SHA-256 covers the whole canonical document, so a
metadata-only change yields a new hash and a normal `document.saved` journal
event and Git diff of the metadata line.

`Document` (JSON from `qms`) gains a `page` object with the same validated
values. `Documents::create/save` take a `PageSetup`; `qms document create|save`
accept optional `--page-size`, `--orientation`, `--margin-top|right|bottom|left`,
`--header`, `--footer`. On `save`, omitted options keep the previous revision's
values (never silently reset); on `create`, omitted options use the defaults.
Saving unchanged title, body and metadata still returns the existing revision.

## Desktop (Kotlin)

`DocumentSnapshot` carries `DocumentPageSetup`; `DocumentCapabilities` loses
`loadPageSetup/savePageSetup`; `saveDocument`/`createDocument` pass the page
setup. Each tab buffer owns title and page setup alongside the body; the
Metadata panel edits the buffer (draft values with inline validation using the
same rules as Rust, which remains authoritative) and persistence happens only
through the existing explicit Save with its expected-revision conflict, dirty
and close guards. Dirty state includes metadata. Pagination and preview use the
tab buffer's page setup. Metadata is the third tool on the right rail (after
Review and History; shortcut 5); the panel is read-only for historical and
non-Draft records and shows ID, revision, content hash and status exactly as
saved. Rendering and viewing the panel never write documents, refs or journals.

## Risk assessment

Risk Management and Security Risk Management apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Metadata edits silently lost or unhashed | Metadata lives in hashed canonical bytes; edits dirty the tab and persist only via explicit Save | Unsaved buffer edits are lost if the app is killed |
| Misleading revision/hash/status display | Read-only fields rendered from the saved snapshot, never from buffer state | A hash is not proof of identity or approval |
| Invalid values corrupt layout | Strict bounded validation in Rust; mirrored inline validation in UI | Font/host differences can still change visual page fit |
| Schema change breaks verification | Single canonical codec; strict decode in history; v1 rejected explicitly | v1 documents unsupported (accepted pre-release) |
| Template injection | Only `{title}`, `{page}`, `{total}` placeholders accepted; templates rendered as plain text, never HTML/Markdown | Template text is user content within 120 chars |
| Metadata-only save skipped as "no change" | Unchanged check compares full canonical hash | None known |

Manual visual, keyboard and accessibility review remains required before merge.
These records are draft, not approved compliance evidence.
