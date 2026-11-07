---
title: EQMS-104 Markdown editing and paginated preview
status: draft
related: [EQMS-083, EQMS-092, EQMS-104]
---

# EQMS-104: Markdown editing and paginated preview

Issue: #67.

## Scope

Provide editable Markdown and Word-familiar paginated reading in the desktop
document workspace while preserving canonical source and existing native
document controls. Pages are presentation-only; record authority remains the
same Rust Markdown/document model and explicit save flow.

Page setup is project-scoped presentation configuration:

- page size: A4 or US Letter
- orientation: portrait or landscape
- margins: independent top/right/bottom/left values
- header/footer templates with `{title}`, `{page}`, `{total}` placeholders

Defaults are A4 portrait with 25.4 mm margins.

## Boundaries

- No DOCX/PDF generation or import.
- No approval/signature/reviewer-identity expansion.
- No second Kotlin Markdown parser; continue using native rendered semantics.
- No claim that page layout settings or rendered pages are controlled evidence.

## Design

The toolbar provides Markdown (default), Markdown + Preview, and Preview.
Markdown and Split use one continuous tab-owned source buffer and its existing
selection, explicit save and lifecycle guards. Preview is read-only. Historical
and non-Draft records remain read-only in every mode. Switching modes never
rewrites source or saves a record.

Split and Preview share semantic pagination. Page geometry comes from page setup
and measured rendered content, not character-count source chunks. Repeated
headers/footers reserve their own space. Long text is fragmented by measured
lines, tables by rows, and images retain their aspect ratio. An indivisible
oversized fragment has explicit local overflow rather than silently lost content.
Anchor navigation maps semantic anchors to page indices. Rendering and layout
results are tied to the current buffer/revision and page geometry.

A standalone top-level `<!-- pagebreak -->` line is an eQMS Markdown extension,
recognized by the Rust parser. Newlines and horizontal rules are unchanged;
markers in inline content, code, quotes or lists are literal. Leading/trailing
and consecutive markers do not create empty pages. Source insertion is an
ordinary undoable edit; only explicit Save persists it. Other Markdown tools may
hide this comment without honoring its pagination meaning.

The semantic model/renderer contract is versioned for the page-break node;
the outer native CLI envelope is unchanged. The safe HTML serializer emits a
fixed print-break element, never executes source HTML. Rich table-cell, row and
column authoring remains outstanding acceptance work for #67; tables in this
increment are edited as Markdown source. This supersedes the initial Canvas
default and automatic-flow-only design, without claiming the whole issue done.

Project page setup is loaded and saved through `DocumentCapabilities`. The
desktop adapter persists settings in local user preferences keyed by project
path digest, keeping settings project-scoped without mutating canonical
workspace files. Invalid persisted data fails explicitly and falls back to
safe defaults.

## Risk assessment

Risk Management and Security Risk Management both apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Pagination misleads users about authoritative content | Keep Markdown/Split available and reuse the same tab-owned content state and explicit save flow | Page layout is still derived presentation, not canonical evidence |
| Invalid settings break rendering or hide content | Validate ranges/geometry, fail explicitly, and keep defaults | Font/host differences can still change visual page-fit |
| Project settings are mistaken for document revision metadata | Store settings outside canonical document/journal history and keep provenance labels unchanged | External sharing of page settings is not covered in this increment |
| Edits silently drop unsupported Markdown constructs | Preserve Markdown source as the saved source of truth and avoid hidden transforms | Rich per-construct WYSIWYG editing remains incomplete |
| Page-break lookalikes alter content flow or execute HTML | Recognize only exact top-level Rust parser events; preserve raw source and strict decoder validation | The comment is an extension, not portable standard Markdown |
| Large or indivisible content overflows a page | Bounded native inputs, measured layout and visible local overflow | Font/host variation and oversized table rows can limit page fidelity |

Manual visual, keyboard, and accessibility verification remains required before
merge.
