# EQMS-104 automated evidence

Issue: [#67](https://github.com/Entreco/eQMS/issues/67).  
Status: observed automated results for the three-mode follow-up, not approved
compliance evidence. Human review/testing and broader rich-table authoring
acceptance work remain pending. The initial Canvas evidence is superseded by
this revision-bound record.

## Revision and environment

- Tested implementation commit: `ee0589628852f653eb031a381fe0f7eef97cf62e`.
- Branch: `feature/eqms-104-editable-paginated-canvas`, based on latest fetched
  `origin/main` `95999cb54f265248251aaadd1029c603192ee5ef`.
- Environment: macOS/aarch64; JetBrains Runtime 25.0.4; Gradle wrapper 9.6.1;
  Rust 1.99.1; Kotlin/Compose versions from the committed dependency catalog.
- Final commands ran after the implementation commit, before this separate
  evidence commit. An unrelated root README working-tree edit was preserved and
  excluded from the issue commits; implementation/test sources matched the
  identified commit.

## Reproduction

Run from repository root at the tested implementation revision. `output` below
is a caller-selected temporary artifact directory, not a repository requirement:

```sh
cargo build --workspace --release --locked --quiet
cargo test --workspace --locked --quiet
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked --quiet -- -D warnings

output="$(mktemp -d)"
EQMS_QMS_PATH="$PWD/target/release/qms" \
EQMS_RENDER_REPORT_DIR="$output/report" \
EQMS_JEWEL_PREVIEW_DIR="$output/captures" \
./gradlew \
  :feature:document:jvmTest :feature:scaffold:jvmTest \
  :feature:search:jvmTest :feature:ui:jvmTest \
  :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs \
  :apps:web:compileKotlinWasmJs \
  :apps:desktop:test \
  --tests '*DesktopDocumentRenderingTest' \
  --tests '*DocumentRenderDecoderTest' \
  --tests '*DesktopDocumentCapabilitiesTest' \
  --rerun-tasks --no-daemon --console=plain --quiet
```

## Observed results

| Check | Result |
|---|---|
| Native release build, rustfmt, Clippy with denied warnings | Passed |
| Rust workspace tests | 110 passed; 0 failed or ignored |
| `:feature:document:jvmTest` | 49 passed; 0 failed/skipped |
| `:feature:scaffold:jvmTest` | 19 passed; 0 failed/skipped |
| `:feature:search:jvmTest` | 8 passed; 0 failed/skipped |
| `:feature:ui:jvmTest` | 6 passed; 0 failed/skipped |
| Selected desktop capabilities/decoder/real-native rendering tests | 11 passed; 0 failed/skipped |
| Document/scaffold/web Wasm compilation | Passed |
| IDE compilation of affected common/JVM source files | Passed |

The real-native test was enabled, not skipped: it exercises saved/unsaved
provenance, explicit marker save/reload, unchanged Git HEAD/source during
render-only operations, strict decoding and actual paginated Compose layout.
It also exposed a zero-width asynchronous document list; the list now explicitly
fills its panel width and the final real-native test passes.

## Measured latency

[Raw observations and environment](EQMS-104/render-latency.json), fixture version
1, exact UTF-8 sizes, three warmups plus thirty retained edits per size. Timings
include the real 100 ms coalescing delay, native invocation, decoding, measured
pagination and the preview LazyColumn's layout callback. They do not measure
every offscreen page or image decode. Cold open includes switching to Preview.

| Body size | Warm p50 | Warm p95 | p95 limit | Maximum |
|---|---:|---:|---:|---:|
| 50 KiB | 115.464 ms | 120.784 ms | 150 ms | 121.929 ms |
| 1 MiB | 197.683 ms | 214.720 ms | 500 ms | 214.822 ms |

Cold click-to-observed-layout: 452.327 ms. Results are observations on this
environment, not a cross-platform performance guarantee.

## Coverage summary against EQMS-104 tests

- T104-01/02/06: three modes, Markdown default, long-source typing, discrete
  insertion undo, retained caret/text/tabs, source/split edits, busy/lifecycle
  guards and affected workspace regressions.
- T104-03/04/09: measured A4/Letter portrait/landscape geometry at 5/25.4/50 mm
  margins, exact-fit/oversized fragments, complete text order, long code,
  paginated table rows, image-ratio invalidation and header/footer substitution.
  Pixel-level page furniture and physical scrolling remain human checks.
- T104-05/07/08/11: exact native top-level marker recognition and false positives,
  strict schema/HTML output, source-preserving caret insertion, undo/redo,
  read-only/busy restrictions and real-native explicit save/reload/non-mutation.
- T104-10: contents navigation, long-paragraph footnote-reference page mapping,
  stale render/tab guards and keyboard/pointer split controls. Physical keyboard
  and accessibility acceptance are not implied by semantic automation.
- T104-12: real-native 50 KiB/1 MiB text-layout observations above; image decode
  and every offscreen page are excluded.

## Inspected automated captures

Synthetic test content only. Agent inspection confirms no personal paths or
confidential document content in the retained images. These are offscreen
production-panel captures, not native window chrome or human visual approval.

- [Preview, light theme](EQMS-104/preview-light.png)
- [Editable source and paginated preview, dark theme](EQMS-104/split-dark.png)
- [Local image and footnote in a page, light theme](EQMS-104/image-light.png)

The saved revision label in the images belongs to the synthetic fixture's Git
history, not to the implementation commit.

| Artifact | SHA-256 |
|---|---|
| `render-latency.json` | `f7209a9d608fb04d62397c23993cbaa1c278aa2ee5e9f0706bc6980bc64eecac` |
| `preview-light.png` | `5af12bad1138a4a20b2afc6d686f5905a3556ee4383cae669c098e1740e7c5be` |
| `split-dark.png` | `a4ff4d944c9b738fdd38b35f6c777b1ea0a263787787367ca40045b9257ed685` |
| `image-light.png` | `2d4970dd53024916c00d5ceef38c96952bb7865b2aef76d10ac844aac83c910e` |

## Failures and exclusions

- The exploratory full desktop suite reported 50 tests, one failure and four
  skipped opt-in tests. The untouched macOS `DesktopWindowNativeTest` Robot
  title-bar check timed out waiting for a physical click; it is not claimed
  passing. Final affected desktop tests deliberately select document adapters,
  decoder and real-native rendering, avoiding unrelated foreground-window
  control in this shared environment.
- Broad IDE/web packaging failed resolving
  `com.github.webassembly:binaryen:125` from configured repositories. Affected
  source compilation and Wasm Kotlin compilation pass; optimized web packaging
  is not verified. No repository/dependency workaround was added.
- Early tests exposed the discrete-undo requirement, table row grouping and the
  zero-width native document list. Those failures were corrected; final mapped
  suites above pass. A concurrent IDE/Gradle compilation probe also raced over
  class outputs; the final IDE compile was run separately and passed.
- Native-window Robot capture, cross-platform rendering, assistive technology,
  intentional empty pages, rich table editing and controlled PDF/DOCX output
  are not covered by these results. Missing functional coverage is not passing.

## Manual checks still required

- [ ] Human visual review of pagination, headers/footers, and page numbering.
- [ ] Human source typing/undo/scroll and mode-switching checks beyond one page.
- [ ] Human keyboard/focus/accessibility checks in the three modes and Page setup.
- [ ] Human cross-platform visual checks for page-fit consistency.
- [ ] Human code review before merge.
