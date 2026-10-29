# EQMS-083 test specification

Status: draft. Define before implementation; do not treat this plan as evidence.

| Test | Requirements | Checks |
|---|---|---|
| T083-01 | R083-01/02 | Semantic/HTML fixtures for all supported syntax, duplicate/Unicode headings, references, footnotes, raw HTML and deterministic output. |
| T083-02 | R083-03 | Exact saved/historical identity/hash; unsaved buffer labels/digest; no changed bytes, Git refs or journal. |
| T083-03 | R083-04 | Mode defaults/switching, independent tabs/caret/scroll, dirty/save/close guards and lifecycle read-only. |
| T083-04 | R083-05 | Both themes, TOC/footnote navigation, keyboard/focus, local overflow, split resize and minimum window size. Tables fill the available content width, retain equal column widths and align wrapped cells within each row. |
| T083-05 | R083-06 | Unsafe URI, raw HTML, missing/remote/oversized assets, traversal/symlinks and historical placeholders; explicit diagnostics. |
| T083-06 | R083-07 | Size/depth/node limits, malformed protocol, async races/cancellation/error fallback; buffers remain recoverable. |
| T083-07 | R083-08 | Versioned 50 KiB/1 MiB fixtures, warm last-edit-to-layout p95 targets 150/500 ms with 100 ms coalescing included; separate cold timing and honest exclusions. |
| T083-08 | R083-04/08 | Rust fmt/Clippy/tests, affected JVM workflow regressions, Wasm, native host captures and separate revision-bound evidence. |

Manual visual/physical-input/accessibility tests remain unchecked until performed.

Implementation mapping: core `render.rs` and CLI `commands/render.rs` unit tests,
CLI document saved-render/asset regression, desktop strict decoder test and
`DesktopDocumentRenderingTest`, document mode/race/historical-image/external-link
regressions in `DocumentScreenTest`, and the extended production-window
`DesktopJewelPreviewTest`. Latency uses three warmups and thirty retained edits
for each exact-size version-1 fixture; report the 100 ms real-clock coalescing
delay, native invocation, decoding and actual preview LazyColumn layout. Image
decode and print/PDF are not part of this text-layout performance measurement.

Production `DocumentScreen` panel captures use the offscreen Compose test root
with real native capabilities; these exclude native window chrome. Separate
production-window Robot captures require the target native window to be active
and temporarily always-on-top, preventing accidental capture of another app.
Inspect all images before retaining evidence; no screenshot alone proves manual
visual acceptance or physical-input/accessibility coverage.
