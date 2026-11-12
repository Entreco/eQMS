---
title: EQMS-112 template model design
status: draft
related: [EQMS-107, EQMS-112]
---

# EQMS-112 template model and schema v1

Issue: #84, part of epic #78. Rust core and native `qms` CLI only. Binding to
documents (#85), conformance (#86), the style engine (#87), UI (#88), standards
packs (#89), waivers (#90), references (#91) and reconciliation (#92, #93) are
separate issues.

## Design

- A template is canonical pretty JSON, schema 1, stored immutably at
  `templates/<id>/v<version>.json`. Assets are PNGs at
  `templates/assets/<sha256>.png`. A version is never overwritten; a change is
  a new, higher version, so documents pinned to an older one render identically.
- `structure`: ordered sections (title, level 1-6, required, guidance), allowed
  block types and a Markdown scaffold for new documents.
- `style` (`style_schema_version` 1): a closed declarative schema, deliberately
  not CSS or code, so all renderers agree: bundled font families only
  (sans/serif/mono), body size 8-16 pt, heading numbering and colour
  (`accent` token or `#RRGGBB`), table style, image width/alignment, table of
  contents (depth, leaders, placement), cover page, page-break level.
- `page` (optional defaults) reuses `PageSize`/`Orientation`; margins in tenths
  of a mm. Precedence against EQMS-106 per-document overrides is decided in #85.
- `regions`: header/footer, each with left/centre/right slots holding text with
  placeholders `{title} {page} {total} {id} {revision} {status} {project}` and an
  optional image (`project_logo` or a template asset).
- Install is one `template.installed` journal event with content hashes of the
  template and its assets. `list`/`show` verify the journal and re-check every
  installed template and asset hash; any mismatch is an error. A built-in
  `generic` template is always available; a project cannot silently replace it
  (same id/version is rejected, a higher version is allowed).
- Wording rule: templates support, never certify, compliance.

## Risk and security rationale

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Template executes code or loads remote/system resources | Closed enum/number schema; no CSS, scripts, URLs or system fonts; unknown fields rejected | Renderers must honour the schema (#87) |
| Malicious asset image | PNG only, structure/size checks from EQMS-107, content-addressed, max 8 assets | Not decoded or sanitised in core |
| Silent template change altering past output | Immutable versions, hashed, journaled, verified on read | Document pinning arrives with #85 |
| Oversized/odd input | 1 MiB CLI read cap, bounded section counts and string lengths | Markdown scaffold is length-checked only, not parsed |

Risk Management and Security Risk Management apply; no approvals are claimed.

## Limitations

Write is remove-then-create (not atomic), as in EQMS-107. Templates are not yet
used by documents, rendering or the desktop UI.
