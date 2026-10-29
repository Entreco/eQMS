# EQMS-083 requirements

Status: draft; issue #36; design/risk: ../architecture/EQMS-083.md.

| ID | Requirement |
|---|---|
| R083-01 | Parse supported Markdown once in Rust into a versioned semantic tree, preserving canonical bytes. |
| R083-02 | Generate deterministic escaped HTML and identical heading/footnote anchors from that tree. |
| R083-03 | Preserve exact saved ID/revision/hash and distinguish unsaved buffer input digests and lifecycle metadata. |
| R083-04 | Offer per-tab Preview/Source/Split, default Preview, preserving caret, buffers, scroll, guards and read-only state. |
| R083-05 | Render headings, TOC, lists, tables, links/references, footnotes, quotes, code and safe local images accessibly in both themes. |
| R083-06 | Restrict links and bounded raster assets; no automatic networking, execution, path escape or historical mutable-asset claims. |
| R083-07 | Enforce documented resource limits, off-thread coalesced rendering, stale-result rejection and explicit Source fallback. |
| R083-08 | Record measured latency, affected regressions, exact-revision evidence and synthetic previews; manual review remains pending. |
