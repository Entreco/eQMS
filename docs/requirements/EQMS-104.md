---
title: EQMS-104 paginated canvas requirements
status: draft
related: [EQMS-104]
---

# EQMS-104 requirements

> Superseded in part by EQMS-106: page setup (R104-03, R104-05, R104-06) is now
> hashed per-document metadata edited in the Metadata panel, not a project-scoped
> preference. Rendering and pagination behaviour is unchanged.

| ID | Requirement |
|---|---|
| R104-01 | The toolbar provides exactly Markdown, Markdown + Preview, and Preview in that order; each newly opened tab defaults to editable Markdown, retaining per-tab mode, source selection/scroll and split state. |
| R104-02 | Markdown and Split edit one Markdown-backed tab buffer with explicit Save; historical/non-Draft records remain read-only and lifecycle, dirty-close, and conflict guards remain unchanged. |
| R104-03 | Project page setup supports page size (A4/US Letter), orientation (portrait/landscape), and independent margins with validated bounds and documented defaults. |
| R104-04 | Split and Preview share paginated rendered Markdown with repeated headers/footers and `{title}`, `{page}`, and `{total}` placeholders, excluding page furniture from content flow. |
| R104-05 | Page setup is project-scoped presentation configuration and does not by itself alter canonical document revisions, hashes, or lifecycle state. |
| R104-06 | Invalid stored page-setup data fails explicitly, falls back safely, and does not block opening/editing documents. |
| R104-07 | Existing preview rendering, source editing, save/history/lifecycle operations, and provenance/status messaging remain compatible. |
| R104-08 | A standalone top-level `<!-- pagebreak -->` line creates a preview page boundary without changing canonical source; code/inline/nested lookalikes remain literal and redundant boundaries create no empty pages. |
| R104-09 | Insert page break is an undoable source edit at the caret in Markdown/Split, disabled for read-only/busy records; it does not replace selected content or save implicitly. |
| R104-10 | Page layout uses measured rendered geometry and configured physical page proportions/margins; paragraphs/code can span pages, tables paginate by rows and images fit with aspect ratio retained. Indivisible overflow is visible and diagnosed, never silently discarded. |
| R104-11 | Page-aware contents/footnote navigation, async stale-result protection, bounded rendering and accessible keyboard-operated mode controls work in both themes and narrow windows. |

Rich table-cell/row/column authoring from #67 is not fulfilled by these source
editing requirements and remains outstanding. These records are draft, not
approved compliance evidence.

Hazards, mitigations, and limitations are defined in the
[EQMS-104 design](../architecture/EQMS-104.md).
