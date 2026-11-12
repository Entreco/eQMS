---
title: EQMS-123 WYSIWYG editing tests
status: draft
related: [EQMS-123]
---

# EQMS-123 tests

Defined before implementation; requirements in
[EQMS-123 requirements](../requirements/EQMS-123.md).

| Test ID | Requirements | Method and expected result |
|---|---|---|
| T123-01 | R123-02 | Rust: schema 3 ranges for each top-level kind, CRLF/LF, multibyte/emoji (UTF-16), page breaks, ordering/non-overlap, trailing newline excluded. |
| T123-02 | R123-02 | JVM decoder: accepts valid ranges; rejects schema 2, negative/overlapping/out-of-bounds ranges. |
| T123-03 | R123-03 | Block patch: replacing a block changes only its range; following ranges shift; unedited documents round-trip byte-identically (LF and CRLF). |
| T123-04 | R123-04 | Inline model: style toggles, typing inherits style, serialization escaping and parse-serialize round trip for bold/italic/strike/code/link. |
| T123-05 | R123-04 | Structure ops: change block style, split/merge via Enter/Backspace, list items, quote, insert rule/page break, delete block. |
| T123-06 | R123-05 | Unsupported constructs (fenced code, footnotes, HTML, nested lists, image paragraphs) become raw blocks; raw edits patch the exact slice. |
| T123-07 | R123-06 | Table ops: edit cell, add/remove row/column, pipe/newline escaping, alignment preserved, minimum 1 column/row. |
| T123-08 | R123-01, R123-07, R123-11 | Compose: Edit default; mode switching keeps text; typing updates dirty and Save only persists on Save; undo/redo shared with Markdown; historical/non-Draft/busy read-only. |
| T123-09 | R123-08 | Compose: sheet background is white and ink dark in both themes; page size/orientation/margins applied; header/footer shown. |
| T123-10 | R123-09 | Link validation rejects `javascript:` and whitespace targets; HTML shown as raw block. |
| T123-11 | R123-10 | Toolbar labels present, shortcuts work, controls disabled when read-only; stale render discarded. |
| T123-12 | all | Regression: Rust, CLI, desktop, document, search, scaffold suites and Wasm compile. |

Automated outcomes are recorded separately. Missing/skipped tests do not count
as passing coverage.

## Manual checklist - pending

- [ ] Human review of paper look in light and dark themes.
- [ ] Human typing, IME, selection, undo and mode-switching checks across pages.
- [ ] Human keyboard/focus and accessibility checks (toolbar, tables).
- [ ] Human check of table editing and raw-block fallback on real documents.
