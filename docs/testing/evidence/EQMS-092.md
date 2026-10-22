---
title: EQMS-092 observed automated results
status: draft
related: [EQMS-092, EQMS-090, EQMS-023, EQMS-050]
---

# Jewel scaffold and legacy-module cleanup evidence

Issue: #50. Implementation revisions:

- Jewel scaffold: `f4aac4b6c0f003c5dd33be6c906ae3885b94db1b`.
- User-requested pre-PR cleanup:
  `1c8e4def17ec0b3abbef6042d6f70f9a49cc97ef`.

This evidence is committed separately after the tested revisions. Human review
and testing remain pending. PR creation is paused at the user's request;
the issue/project item remains open/In progress, not delivered or approved.

## Environment

macOS 26.6.2, arm64, Apple M5 Pro, 24 GiB RAM; Gradle 9.6.1,
Kotlin 2.4.10, Compose 1.12.0, Jewel 0.41.0-262.10968.63,
JetBrains Runtime 25.0.3+-15898627-b508.16.
Rust/cargo 1.99.1. Release `qms` SHA-256:
`1ad0174d1d1dad770ca4a553c19014e320272cb9f8bb59684159011d9c467e2f`.

## Observed commands and outcomes

Commands run from repository root. `<output>` and `<fixture>` are isolated
session directories outside the repository; no existing user workspace was used.

| Revision | Command | Observed outcome |
|---|---|---|
| Jewel | `EQMS_JEWEL_PREVIEW_DIR=<output> EQMS_QMS_PATH="$PWD/target/release/qms" ./gradlew :core:ui:jvmTest :apps:desktop:test :core:ui:compileKotlinWasmJs :apps:web:compileKotlinWasmJs --rerun-tasks --quiet --console=plain` | Passed: 26 UI tests; 25 desktop tests passed and benchmark explicitly skipped; both Wasm targets compiled. Real-native preview test ran, not skipped. |
| Jewel | `cargo build --workspace --release --locked --quiet` | Passed; executable hash above. Rust code unchanged. |
| Jewel | `./gradlew :apps:desktop:run --console=plain --quiet` | JBR launched the actual app. OS window enumeration confirmed its process owned a visible 1100 x 800 native window. This is not human interaction evidence. |
| Jewel | `EQMS_SEARCH_BENCHMARK_WORKSPACE=<fixture> EQMS_QMS_PATH="$PWD/target/release/qms" ./gradlew :apps:desktop:test --tests '*DesktopSearchBenchmarkTest' --rerun-tasks --quiet --console=plain` | Passed all four warm p95 <=150 ms assertions with 100 retained samples/query. |
| Cleanup | `./gradlew :apps:cmd:build :apps:cmd:run :core:ui:jvmTest :apps:desktop:test :core:ui:compileKotlinWasmJs :apps:web:wasmJsBrowserDevelopmentWebpack --rerun-tasks --quiet --console=plain` | Passed. Kotlin CLI prints bootstrap/prototype-only notice; 26 UI tests passed; 24 desktop tests passed, 2 opt-in tests skipped without fixture/capture environment; web development webpack built successfully. |
| Cleanup | `./gradlew projects :apps:cmd:dependencies --configuration runtimeClasspath :apps:desktop:dependencyInsight --dependency sqlite-jdbc --configuration runtimeClasspath --console=plain --quiet` | Only core:ui remains under Gradle core; removed ledger/search modules absent. Kotlin CLI runtime contains Kotlin stdlib/annotations only; desktop has no sqlite-jdbc match. |
| Cleanup | Source search for removed module references, JDBC alias, and bootstrap symbol in active Kotlin/build sources | No matches. Historical evidence remains historical and is not rewritten. |
| Cleanup | `git diff f4aac4b..1c8e4de -- core/rust apps/cli Cargo.toml Cargo.lock` | Empty; native implementations and Cargo workspace unchanged. No database files were modified/deleted by the cleanup. |
| Both | `git diff --check` | Passed. |

Mappings: [T092-01 through T092-08](../EQMS-092.md).

## Preview captures

The opt-in `DesktopJewelPreviewTest` creates an isolated Git workspace with
synthetic repository-local identity and sample drafts, using real native
init/verify/document/history/diff adapters. It opens the workspace through the
actual project chooser, selects a saved document, toggles themes, and captures
Compose content. These are not OS-chrome screenshots or human approvals.

- [Dark editor](EQMS-092-dark.png)
- [Light editor](EQMS-092-light.png)
- [History overview](EQMS-092-history.png)
- [Revision comparison](EQMS-092-diff.png)

Regenerate using the command in [BuildAndRun](../../operations/BuildAndRun.md).
The test retains the synthetic workspace under `<output>` for manual opening.

## Search timing regression

[Sanitized raw report](EQMS-092-search-ui.json) identifies the exact Jewel
revision, binary hash, fixture, environment and every retained timing sample.
The working tree was clean when that report was generated. This report predates
the module cleanup; no post-cleanup performance claim is made.

Real fixture: 10,000 current drafts, 104,860,000 current body bytes and 100
historical revisions. Generated/prepared with `scripts/benchmark_search.py`;
observed initial verification/index preparation was 586,426.34 ms, not a
performance improvement claim. The UI benchmark's later warm no-op preparation
was 138.76 ms. It uses real native queries with an empty initial-list override,
20 warmups/query and 100 retained samples/query. Input-to-layout includes the
10 ms debounce, native invocation, response decoding and Compose layout;
project opening/full-list load is not measured.

| Query | p50 ms | p95 ms |
|---|---:|---:|
| And | 103.94 | 111.63 |
| risk | 100.96 | 106.44 |
| managment | 101.49 | 107.70 |
| trace | 98.61 | 106.71 |

No outliers were removed. Synthetic repetitive text, warm caches and this one
machine are not a general production performance guarantee.

## Failed attempts and limitations

- SSH fetch failed; HTTPS fetch succeeded without changing the configured remote.
- Initial compile checks exposed an invalid test import and missing divider
  orientation; both were corrected before the passing runs.
- A dirty-status string duplicated the discard-dialog title, causing an
  ambiguous test selector. Distinct status wording resolved it.
- Initial real-native preview attempted comparison before async history loaded;
  explicit readiness waits fixed it.
- Capture inspection exposed missing built-in combo-box glyphs and poor panel
  action placement. Matching platform icon resources and fixed panel-header
  actions resolved them before final captures.
- Native window screenshot capture was unavailable (`could not create image
  from window`); Compose captures above succeeded. No whole-screen capture used.
- JBR native-access warnings, test-rule deprecation warnings, Node URL
  deprecation/webpack warnings and an app SLF4J no-provider warning were observed;
  they did not fail validation.
- Rust suites were not rerun for UI/Gradle-only changes. No Windows/Linux
  execution, packaging/signing, browser functional workflows, screen-reader
  assessment, or human testing/review is claimed.
- The cleanup deliberately removes prototype SQLite initialization, not the
  Rust-derived SQLite index. Existing database files are left untouched.
- `core:ui` remains Kotlin under core. Rust-only core and feature-scoped UI
  directories are a reviewed follow-on direction, not implemented by this cleanup.

## Manual review/testing pending

- [ ] Review dependency/runtime and module boundaries.
- [ ] Exercise representative projects, saves/conflicts, search and history.
- [ ] Confirm keyboard focus, accessibility and both themes on target platforms.
- [ ] Review the intended removal of prototype SQLite behavior.
- [ ] Agree the Rust crate and Kotlin UI feature split before resuming PR work.
