---
title: EQMS-115 template style engine and preview requirements
status: draft
related: [EQMS-112, EQMS-113, EQMS-115]
---

# EQMS-115 requirements

| ID | Requirement |
|---|---|
| R115-01 | The core resolves a template's `style` and `regions` into one closed style model (fonts, body and h1-h6 sizes, `#RRGGBB` heading colour with `accent` resolved to the project accent, numbering, table style, image width/alignment, TOC, cover, page-break level) carried as an optional `style` field of the render document. Output without a style request is unchanged. |
| R115-02 | A saved revision renders with its pinned template (id, version, hash verified). `--template id[@version]` previews another installed template without changing the document. With no pin and no selection the built-in generic style applies without regions. An unresolvable or changed pin falls back to the generic style with a visible diagnostic. |
| R115-03 | Heading numbers are computed in core deterministically (shallowest level is the root, skipped levels count as 0) and only when numbering is enabled. |
| R115-04 | Header/footer slot text is expanded once in core into text/page/total segments; substituted values are never re-expanded. Unsaved buffers use fixed placeholders values. |
| R115-05 | Region images resolve to the project's uploaded PNG logo or a hash-verified template asset, embedded with pixel size. A generated logo is omitted with a diagnostic. |
| R115-06 | A heading colour with contrast below 4.5:1 on white produces a warning diagnostic. |
| R115-07 | Golden fixtures pin the style model for fixed templates and documents; any drift fails the core tests. |
| R115-08 | CLI: `document render --style` / `--template`, `document render-buffer --project` with optional `template`/`document` input fields; invalid selectors or unknown templates fail without output side effects; existing invocations are unchanged. |
| R115-09 | The desktop Preview applies the style model: fonts, sizes, heading colour and numbers, table style, image width/alignment, cover page, page breaks before headings, table of contents with optional leaders and page numbers, and header/footer regions with images and page/total fields, replacing hard-coded styling. Documents without a template keep their own header/footer text. |
| R115-10 | The Preview shows which style applies (pinned, default or preview only) and lists installed templates for a read-only preview; choosing one never modifies the document and can be reset. Rendering failures show a clear message and leave Markdown available. |
| R115-11 | Existing Rust and Kotlin regressions pass; no new core capability is reachable only through tests or the CLI. |

UI path (non-technical): open a project and a document, choose the **Preview**
view, read the `Style:` line above the pages, open **Preview style**, pick a
template and check the pages; **Use document's style** resets.

Design and limitations: [EQMS-115 design](../architecture/EQMS-115.md). Draft
records, not approved compliance evidence.
