---
title: EQMS-115 template style engine and preview design
status: draft
related: [EQMS-109, EQMS-112, EQMS-113, EQMS-115]
---

# EQMS-115 template style engine and paginated preview

Issue: #87, part of epic #78. Depends on EQMS-112 (#84) and EQMS-109 (#80), both
merged. Delivered end to end under `AGENTS.md`: Rust core + native `qms` CLI +
desktop preview. PDF/DOCX adapters (#35/#33) will consume the same style model
later; they are not part of this issue.

## Scope

In: one resolved **style model** produced by the Rust core from a template and
carried in the render contract; the desktop paginated **Preview** applies it
(fonts, sizes, heading colour and numbering, tables, images, cover page, page
breaks before headings, table of contents with leaders and page numbers,
header/footer regions with images); a read-only **Preview style** picker; golden
render fixtures.

Out: installing/editing templates in the UI (#88), starter packs (#89), the
WYSIWYG *Edit* view (it keeps its editor styling), PDF/DOCX, font bundling,
changing a document's pin from the UI (explicit `set-template` stays CLI-only).
Follow-up for the UI pin change/management: #88.

## Design

- **Style model** (`core/rust/src/style.rs`, no CLI/UI dependency). Added to the
  render document as an optional `style` field, serialised only when requested,
  so existing render output is byte-identical. It contains: template identity
  (`id`, `version`, `hash`, `source`, `origin` = `pinned | selected | default`),
  `body_font`/`heading_font`, `body_size_pt`, `heading_sizes_pt` (h1..h6 =
  round(body x 1.8, 1.5, 1.3, 1.15, 1.05, 1.0)), `heading_color` resolved to
  `#RRGGBB` (the `accent` token becomes the project accent), numbering, table,
  image, TOC, cover, `page_break_before_level`, optional `regions`, embedded
  `images` and `numbers` (anchor -> heading number).
- **Template selection.** `render` of a saved revision uses that revision's pin
  (id, version, `sha256:` hash must match the installed template). `--template
  id[@version]` previews another installed template without touching the
  document. No pin and no selection: the built-in `generic` style **without
  regions**, so the document's own header/footer strings (EQMS-106) are kept. If
  a pin cannot be resolved or its hash differs, rendering still succeeds with
  the generic style and a visible diagnostic; it never silently pretends the
  pinned style applied.
- **Heading numbers** are computed in core: the shallowest heading level present
  is the root; `1`, `1.1`, `1.1.1`; a skipped level counts as `0` (`1.0.1`). Only
  when `heading_numbering` is on. Rendering uses the numbers, never recomputes.
- **Regions.** Slot text is expanded once in core (`{title} {id} {revision}
  {status} {project}`); `{page}`/`{total}` become `page`/`total` segments the
  renderer fills per page. Substituted values are never re-expanded, so a title
  containing `{page}` stays literal text. Unsaved buffers use `{id}` = `(not
  saved)`, `{revision}` = `-`, `{status}` = `Draft`.
- **Images.** `project_logo` resolves to the project's uploaded PNG; a generated
  (shape/initials) logo is not drawn in pages and yields a diagnostic. Template
  asset images are read from `templates/assets/<sha256>.png` and hash-verified.
  PNGs (already size/structure-validated, max 512 KiB, 1024 px) are embedded as
  base64 with their pixel size.
- **CLI.** `qms document render <path> <id> [--revision R] [--template id[@N]]`
  gains `--style` (and `--template`, which implies it) to include `style` in its JSON; `render-buffer` takes optional `--project <path>`
  and JSON fields `template` (`{id, version, hash?}`) and `document`
  (`{id, revision, status}`). Without them the output is unchanged.
- **Desktop.** `RenderedDocument.style` (commonMain) is decoded from the CLI and
  consumed by `DocumentPagination`/`DocumentPresentation`, replacing hard-coded
  fonts, sizes, colours, table borders and image sizing. The Preview header row
  shows `Style: <name> v<N> (pinned | default | preview only)` and a
  **Preview style** list of installed templates (`qms template list`); picking
  one re-renders only the preview and is not saved. `DocumentSnapshot.template`
  carries the pin from `document` JSON.
- **Table of contents** (when enabled) is laid out as pages content: entries at
  their depth with optional dot leaders and page numbers from the same layout. Its
  height uses a fixed-width number reservation, so numbers cannot change
  pagination. A cover page puts the title alone on page 1.
- **Fonts.** The schema's `sans | serif | mono` map to the platform's generic
  families (Compose `SansSerif`/`Serif`/`Monospace`). Fonts are not bundled yet;
  see limitations.
- Wording rule: templates support, never certify, compliance.

## Risk and security rationale

Risk Management applies (preview fidelity of controlled documents); Security Risk
Management applies (new embedded file content and a template-driven render path).
No approvals are claimed.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Preview shows a style the document is not pinned to | Origin always shown in the header row; picker output labelled "preview only"; unresolved/changed pin falls back to generic with a diagnostic | Pin is checked at render, not continuously |
| Placeholder injection (title containing `{page}`, control chars) | Single-pass expansion into typed segments; template placeholders validated in EQMS-112 | none known |
| Malicious or oversized image | PNG-only, structure/size checked at install (EQMS-107/112), hash re-verified on read, embedded size bounded, decoded by Skia with dimension check | PNG is not re-encoded or sanitised |
| Unreadable heading colour (low contrast on white paper) | Core computes contrast with white and adds a warning diagnostic below 4.5:1 | Warning only; the template still applies |
| Template drives unbounded layout work | Closed schema; section/slot/asset limits from EQMS-112; render limits unchanged | Large projects: journal verify runs per render |
| Preview differs from future PDF/DOCX | One style model in core; golden fixtures guard it | PDF/DOCX adapters not yet built |

## Limitations

Generic font families are platform-dependent; widths, line breaks and page counts
can differ between machines until fonts are bundled. `render` verifies the journal
per call (cost grows with journal size; measured only on a small project). The
Edit (WYSIWYG) view does not apply template regions. The picker lists installed
templates; installing one still needs `qms template install` until #88.

## Implementation notes (as built)

- The style template identity includes `name`. Unsaved buffers use `(not saved)` / `-` / `Draft` for `{id}`, `{revision}`, `{status}`.
- Desktop: only the **Preview** view is restyled (Edit/WYSIWYG is unchanged). The preview header shows `Style: <template> v<N> (<origin>)` and a "Preview style" picker (preview only, never saved) with "Use document's style" to reset.
- Contents entries use a fixed-width page-number column and a fixed-length dot leader (not tab-aligned) so page numbers cannot alter pagination. Pagination is two-pass.
- Fonts map to Compose generic families (sans/serif/mono); no fonts are bundled.
- Every render verifies the journal when reading branding and templates (performance limitation).
- Manual testing of non-default styles: `scripts/eqms-115-sample-project.sh <folder>` creates a project with a "Formal SOP" template (asset image, banded tables, cover, contents) and a pinned and an unpinned document.
