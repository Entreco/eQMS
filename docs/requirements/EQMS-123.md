---
title: EQMS-123 WYSIWYG editing requirements
status: draft
related: [EQMS-123]
---

# EQMS-123 requirements

| ID | Requirement |
|---|---|
| R123-01 | The toolbar offers Edit (WYSIWYG), Markdown, Markdown + Preview and Preview; new editable tabs default to Edit. Per-tab mode, text, selection and scroll are retained and switching never saves. |
| R123-02 | The native semantic model (schema 3, renderer `eqms-markdown-3`) reports UTF-16 source ranges for top-level nodes; the decoder strictly validates them. Kotlin has no second Markdown parser. |
| R123-03 | WYSIWYG edits are minimal patches to the touched block of the one tab buffer; untouched bytes and line endings are preserved and an unedited document round-trips byte-for-byte. |
| R123-04 | Edit supports paragraphs, headings 1-6, bold, italic, strike, inline code, links, bullet/numbered lists, quotes, horizontal rules and page breaks via toolbar and Cmd/Ctrl shortcuts. |
| R123-05 | Constructs outside R123-04 appear as editable raw Markdown blocks and are never dropped or rewritten unless edited. |
| R123-06 | Tables support cell editing, adding/removing rows and columns and serialize to valid pipe tables; merged cells are unsupported. |
| R123-07 | Edit shares undo/redo and dirty state with Markdown mode; historical and non-Draft documents, busy state and conflicts keep Edit read-only and existing guards authoritative. |
| R123-08 | Pages (Edit, Split, Preview) render as white sheets with dark text and a neutral desk in light and dark themes, using page setup size, orientation, margins, headers and footers. |
| R123-09 | Links typed in Edit are validated with the native allow-list rules; blocked links are flagged; raw HTML is never executed. |
| R123-10 | Controls have accessibility labels, are keyboard operable and disabled when read-only; stale render results never overwrite newer buffers. |
| R123-11 | Rendering and mode switching do not mutate canonical documents, refs or journals; changes persist only through explicit Save. |
| R123-12 | Edit mode shows A4-proportioned sheets with millimetre rulers along all four desk edges (text area between margins highlighted), a Jewel-styled icon toolbar (Material icons, tooltips), shows no transient status text while typing, shows a placeholder only in the focused empty field, and arrow keys move between blocks at field edges. |

Risks, mitigations and limitations are in the
[EQMS-123 design](../architecture/EQMS-123.md). Draft records, not approved
compliance evidence.
