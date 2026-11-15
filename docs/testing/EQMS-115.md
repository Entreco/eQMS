---
title: EQMS-115 template style engine and preview tests
status: draft
related: [EQMS-115]
---

# EQMS-115 test specification

Issue: #87. Defined before implementation.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T115-01 | R115-01, R115-03 | Core unit tests (`style`): resolved sizes/colour/fonts; `accent` resolves to project accent; numbering with h2 root, nested levels, skipped level, numbering off. |
| T115-02 | R115-04 | Core unit tests: all placeholders expand; `{page}`/`{total}` become segments; a title containing `{page}` or `{id}` stays literal; unsaved buffer values. |
| T115-03 | R115-02, R115-05, R115-06 | Core tests on a temp project: pinned template used; changed/missing pin falls back with diagnostic; no pin = generic without regions; template asset image embedded and hash-checked; generated logo omitted with diagnostic; low-contrast colour warns. |
| T115-04 | R115-07 | Golden fixtures `core/rust/tests/golden/style_*.json` compared byte-for-byte for the generic and a formal template (regenerate with `EQMS_UPDATE_GOLDEN=1`). |
| T115-05 | R115-08 | CLI integration test: `render --style` pinned/`--template`/unknown template; `render-buffer --project` with template and document context; legacy invocation output has no `style`. |
| T115-06 | R115-09 | Kotlin tests (pagination): sizes/fonts follow the style; numbering prefix; cover page alone on page 1; page break before h1; TOC entries with page numbers matching heading pages; region segments per page; unpinned document keeps own header/footer. |
| T115-07 | R115-09, R115-10 | Kotlin decoder tests for `style` and `template` pin; desktop capability test with a fake `qms` for template list and styled render; error on malformed style. |
| T115-08 | R115-10 | Compose UI test: Style line shows origin; picker lists templates; choosing one re-renders as "preview only"; reset restores. |
| T115-09 | R115-11 | `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test --workspace --locked`; Gradle `:apps:desktop:test :feature:document:jvmTest` and the other feature modules. |

## Manual review/testing checklist (left unchecked in the PR)

One-time setup (technical, until #88): `scripts/eqms-115-sample-project.sh <folder>`
creates a sample project with a `Formal SOP` template (serif, numbered headings,
cover page, table of contents, header/footer with logo) and two documents.

1. Launch the desktop app, open the sample project folder, open **Formal SOP example**
   and choose **Preview**. Expected: the `Style:` line says `Formal SOP v1 (pinned)`;
   page 1 is a cover with the title; a table of contents with dotted leaders and
   page numbers; numbered headings (1, 1.1); each page has the header and footer
   with the logo and `Page N of M`.
2. Click an entry in **Table of contents**. Expected: the preview scrolls to that heading.
3. Open **Plain example** (no template). Expected: `Style: Generic controlled
   document v1 (default)`; the document's own header/footer text is shown.
4. In **Plain example** open **Preview style**, pick `Formal SOP`. Expected: pages change
   style, the line says `(preview only)`, the document is not marked as changed.
   Click **Use document's style**; the original look returns.
5. Edit the text in Markdown + Preview. Expected: the preview updates quickly (record
   your impression; no timing is claimed) and an error, if any, is readable while
   Markdown stays editable.
6. Check readability: heading colour, table style, images; note anything awkward.
