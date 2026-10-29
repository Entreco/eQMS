# EQMS-083 automated evidence

Issue: [#36](https://github.com/Entreco/eQMS/issues/36).
Status: observed automated results, pending manual review/testing; not approved
compliance evidence. The issue remains open and In progress.

## Revision and environment

- Tested source: `ed2b7f07cbdc3f4fa2256961b611c33f3b696808`.
- Branch: `feature/eqms-083-document-rendering`, based on main
  `4047327a8d6d2268e2fa5cd5f8c0a1ec75b49da2`.
- Final run completed: `2026-10-29T21:09:34Z`.
- Host: macOS 26.6.2, aarch64; JetBrains OpenJDK
  `25.0.3+-15898627-b508.16`; Gradle 9.6.1.
- Rust/Cargo 1.99.1; locked dependencies; locally built release `qms`.
- Native-window captures: production `DesktopWindow`/`ProjectScreen`, 1100 x 800.
  Latency fixture: production `DocumentScreen` in the Compose test root,
  1000 x 720, side panels collapsed, dark theme and Split mode.

This evidence is committed separately after the tested source. The subsequent
evidence-only commit does not change application or test behavior.

## Reproduction

Run from the repository root at the tested revision with the above toolchains
and an interactive graphical desktop. The native-window fixture requests app
foreground access and temporarily makes its window always-on-top. Do not
interact with another application while native physical-input checks run.

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked --quiet -- -D warnings
cargo test --workspace --locked --quiet
cargo build --workspace --release --locked --quiet

export EQMS_QMS_PATH="$PWD/target/release/qms"
export EQMS_JEWEL_PREVIEW_DIR="$(mktemp -d)"
export EQMS_RENDER_REPORT_DIR="$(mktemp -d)"
./gradlew :feature:ui:jvmTest :feature:search:jvmTest \
  :feature:document:jvmTest :feature:scaffold:jvmTest :apps:desktop:test \
  --rerun-tasks --console=plain --quiet
./gradlew :feature:ui:compileKotlinWasmJs \
  :feature:search:compileKotlinWasmJs :feature:document:compileKotlinWasmJs \
  :feature:scaffold:compileKotlinWasmJs :apps:web:compileKotlinWasmJs \
  --console=plain --quiet
git diff --check
```

Generated captures include synthetic temporary projects; retain only inspected
images, never whole output directories or unrelated desktop captures.

## Observed results

| Check | Result |
|---|---|
| Rust formatting / Clippy with warnings denied | Passed |
| Rust workspace tests | 105 passed, 0 failed |
| Release native workspace build | Passed |
| Shared UI JVM | 6 passed |
| Search JVM | 8 passed |
| Document JVM | 34 passed |
| Scaffold JVM | 19 passed |
| Desktop JVM | 45 passed, 1 skipped, 0 failed |
| JVM total | 112 passed, 1 skipped, 0 failed |
| All five affected Wasm compilations | Passed |
| Whitespace check | Passed |

Counts were read from the final runner output and Gradle JUnit XML. The skipped
test is `DesktopSearchBenchmarkTest.preparedReferenceWorkspaceFinalInputToLayout`,
which requires the separate opt-in EQMS-050 reference-workspace setup. This is
not functional rendering coverage and is not a new search performance result.
EQMS-083's native rendering benchmark did execute with all 60 retained edits.

The [T083 mappings](../EQMS-083.md) cover shared Markdown semantics, deterministic
escaped HTML, saved/historical provenance and non-mutation, strict decoding,
safe/oversized/symlink assets, input limits, cancellation/stale completion,
mode/tab/caret/scroll and workflow guards, actual TOC navigation, external-link
confirmation, split bounds/disabled behavior, and responsive table geometry.
Existing lifecycle, history, search and native-window regressions ran with the
new default Preview mode.

## Rendering latency

Raw samples: [render-latency.json](EQMS-083/render-latency.json).
Version-1 mixed-Markdown bodies are exactly 51,200 and 1,048,576 UTF-8 bytes.
Each size has three warmups followed by thirty retained edits. Samples include
the real 100 ms edit coalescing delay, native invocation, semantic decoding and
the matching preview LazyColumn's layout callback, not just parser time.

| Body | p50 | p95 | Maximum | p95 limit |
|---|---|---|---|---|
| 50 KiB | 114.122333 ms | 115.628000 ms | 118.984250 ms | 150 ms |
| 1 MiB | 181.302125 ms | 193.391500 ms | 196.515458 ms | 500 ms |

Cold click-to-observed-layout: **668.708125 ms**, measured separately and
including test-harness observation. These are local text-preview measurements,
not universal hardware guarantees. They exclude image decoding, print/PDF,
whole-document pagination and the unrelated search benchmark.

## Inspected synthetic native-window captures

These seven images come from the final source-bound production-window Robot
fixture, not an IDE, browser mockup or offscreen-only composition. The fixture
requires the target window to be active before capture. The decoded image is
repositioned after loading and its *unclipped* bounds must fit the viewport.
All retained images were inspected for correct application/content and absence
of unrelated desktop content or personal paths.

| Mode | Dark | Light |
|---|---|---|
| Preview | [Image](EQMS-083/native-document-preview-dark.png) | [Image](EQMS-083/native-document-preview-light.png) |
| Source | [Image](EQMS-083/native-document-source-dark.png) | [Image](EQMS-083/native-document-source-light.png) |
| Split | [Image](EQMS-083/native-document-split-dark.png) | [Image](EQMS-083/native-document-split-light.png) |

[Local image, references and footnote](EQMS-083/native-document-image-dark.png).
The sample's Effective state results from automated local lifecycle actions;
it is not a human approval or authenticated signature. The other tab's unsaved
sample is retained. The local-image integrity limitation is visible in the UI.
Inspecting these artifacts does not replace human visual acceptance.

## Development failures and retained limitations

- An earlier full desktop run timed out in the existing native title-bar
  Robot focus check. Its isolated rerun and subsequent full runs passed. The
  final run above also passed it; this does not establish freedom from OS
  focus-related test flakiness.
- Earlier screen captures could contain the foreground IDE; direct
  `captureToImage` against the real native window returned black images.
  Neither kind is retained. The final fixture explicitly activates the app,
  requires its window focus and uses Robot for native-window evidence.
  Offscreen production-screen captures are generated separately but are not
  presented as native-window evidence here.
- An oversized offscreen fixture put a rail click outside the test-root bounds;
  its size was corrected. Native image/scroll development runs also exposed
  stale-node races and a clipped image. The final fixture avoids overlapping
  animated TOC/imperative scrolling, scrolls again after decoding and checks
  unclipped image bounds. The previously failing `304e396` fixture is not the
  tested revision reported above.
- Skiko restricted-native-access warnings and Cocoa AWT AppKit-thread teardown
  warnings remain in successful runs. They have not been fixed or hidden.
- Secured local PNG/JPEG reads are Unix-only. Other platforms explicitly report
  unsupported asset access; they do not fall back to weaker filesystem checks.
  Mutable local assets are not versioned or covered by the document hash;
  historical previews intentionally use placeholders.
- Rendering is derived presentation, not an approved document, a signature or
  reference-integrity validation. HTML uses local-image placeholders and no
  executable raw HTML or automatic remote resources. PDF pagination, page
  numbers and repeating headers/footers remain EQMS-082/#35; DOCX is #33.
- Windows/Linux execution, comprehensive assistive-technology/WCAG acceptance,
  human visual/physical-input review and new print/export behavior are not
  established by this run.

## Required human gates

- [ ] Review risk/design, parser/model boundaries, provenance and residual risks.
- [ ] Visually inspect both themes, minimum size, tables, long content and images.
- [ ] Physically test Preview/Source/Split, split resizing, keyboard/focus,
  TOC/references/footnotes and external-link confirmation.
- [ ] Manually exercise dirty tabs, save/conflict/close guards and lifecycle/history.
- [ ] Assess accessibility and intended deployment-host limitations.
- [ ] Approve the PR only after manual review and testing; keep #36 open until merge.
