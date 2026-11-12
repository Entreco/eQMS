---
title: EQMS-123 WYSIWYG document editing
status: draft
related: [EQMS-083, EQMS-104, EQMS-106, EQMS-123]
---

# EQMS-123: WYSIWYG editing on canonical Markdown

Issue: #96. Extends EQMS-104 (#67), which stays open.

## Scope

Edit documents on white paper sheets, Word/Google-Docs style, while the
compact Markdown buffer remains the only saved source. A new **Edit**
(WYSIWYG) mode becomes the default for editable documents; Markdown,
Markdown + Preview and Preview remain.

## Boundaries

- No DOCX/PDF, browser authoring, autosave, signatures or approval changes.
- No second Markdown parser in Kotlin; block structure comes from the Rust
  semantic model.
- No merged table cells, nested-list editing or multi-line table cells.
- The editable sheet is continuous at page width; exact automatic page fit is
  shown in Preview/Split (explicit page breaks are shown in the editor).

## Design

**Source ranges.** Semantic schema 3 / renderer `eqms-markdown-3` add
`start`/`end` to each top-level node: UTF-16 offsets into the submitted body
(trailing line ending excluded). Kotlin never converts byte offsets. The
strict decoder validates ordering, bounds and non-overlap and ignores ranges
that do not match the current buffer digest.

**Block patches.** The editor (`WysiwygModel`) holds blocks with their ranges.
An edit replaces only the touched block's range in the tab buffer through the
tab's existing undo stack (`recordBodyEdit`), then shifts following ranges
locally until the next native render (debounced, digest-guarded) refreshes them.
Untouched bytes, including line endings, are preserved. Save, conflict, busy,
lifecycle and close guards are unchanged; modes never save.

**Block kinds.**

| Kind | Editing |
|---|---|
| paragraph, h1-h6 | Inline rich text (bold, italic, strike, code, link); block style menu |
| unordered/ordered list, quote | Per-item/paragraph rich text when every child is a simple paragraph; Enter adds item |
| table | Cell editing, add/remove row/column; serialized as a pipe table |
| rule, page_break | Insert/delete; page break drawn as a labelled divider |
| everything else (code, footnotes, HTML, nested/complex, images with text) | **Raw block**: the exact source slice edited as monospace text. Never dropped or transformed |

Inline model: `InlineText` stores characters with per-character style bits and
link targets, edited by diffing text-field changes; new text takes the toolbar's
active style or its neighbour's. Serialization escapes Markdown metacharacters
and is covered by round-trip tests. A block whose inline content contains an
unsupported inline (image, footnote reference, raw HTML) is shown as a raw block.
A block is rewritten only when the user edits it, so unedited documents round-trip
byte-for-byte.

**Paper look.** `PaperSheet` draws a white sheet (fixed `#FFFFFF`, dark ink
`#1F2328`) with border and shadow on a neutral desk background in both themes,
at the page-setup aspect ratio and margins with header/footer. Preview/Split
pages use the same sheet.

**Toolbar and shortcuts.** Block style, B/I/S/code/link, lists, quote, table
and row/column controls, rule, page break, undo/redo; Cmd/Ctrl+B/I/K/Z/Shift+Z.
Controls have accessibility labels and are disabled when read-only or busy.

## Risk assessment

Risk Management and Security Risk Management apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Content loss/change on round-trip | Patch only edited blocks; raw blocks for unknown constructs; round-trip tests | Normalization of an edited block (e.g. `*` to `_`, table padding) |
| Wrong ranges after concurrent edits | Digest-guarded ranges, local shifting, stale results ignored | Brief window before refresh |
| Display differs from saved bytes | Source always one click away; unsaved/provenance labels unchanged | Fonts/host differ from print |
| Link/HTML injection | Link targets validated with the Rust allow-list; HTML never executed, shown as raw block | User may type blocked link; it is flagged |
| Caret/IME loss on re-render | Fields keyed by stable block index; refresh does not recreate unchanged blocks | Needs human IME verification |
| Resource pressure | Existing 1 MiB / 100k-node bounds; lazy block rendering | Very large documents are slower |

Manual visual, keyboard, IME and accessibility verification remains required.
