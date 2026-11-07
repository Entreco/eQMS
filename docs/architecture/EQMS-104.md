---
title: EQMS-104 editable paginated canvas
status: draft
related: [EQMS-083, EQMS-092, EQMS-104]
---

# EQMS-104: Editable paginated document canvas

Issue: #67.

## Scope

Add a Word-familiar paginated canvas to the desktop document workspace while
preserving canonical Markdown and existing native document controls. The canvas
is presentation and editing UX only; canonical record authority remains the
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

`DocumentViewMode` now includes `Canvas` and defaults each tab to that mode.
Source and Split remain available. Canvas rendering currently derives page
chunks from the same Markdown body with bounded, deterministic page heuristics.
The first page contains editable title/body fields (same tab-owned state and
save guards as Source); subsequent pages show overflow text for page-flow
feedback.

Project page setup is loaded and saved through `DocumentCapabilities`. The
desktop adapter persists settings in local user preferences keyed by project
path digest, keeping settings project-scoped without mutating canonical
workspace files. Invalid persisted data fails explicitly and falls back to
safe defaults.

## Risk assessment

Risk Management and Security Risk Management both apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Canvas pagination misleads users about authoritative content | Keep Markdown Source/Split available and reuse the same tab-owned content state and explicit save flow | Page layout is still derived presentation, not canonical evidence |
| Invalid settings break rendering or hide content | Validate ranges/geometry, fail explicitly, and keep defaults | Font/host differences can still change visual page-fit |
| Project settings are mistaken for document revision metadata | Store settings outside canonical document/journal history and keep provenance labels unchanged | External sharing of page settings is not covered in this increment |
| Canvas edits silently drop unsupported Markdown constructs | Preserve Source mode, keep canonical body as saved source of truth, and avoid hidden transforms | Rich per-construct WYSIWYG editing remains incomplete |

Manual visual, keyboard, and accessibility verification remains required before
merge.
