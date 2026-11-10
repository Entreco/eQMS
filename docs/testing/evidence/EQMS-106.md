# EQMS-106 automated evidence

Issue: [#70](https://github.com/Entreco/eQMS/issues/70).  
Status: observed automated results, not approved compliance evidence. Manual
review and manual testing are pending.

## Revision and environment

- Tested implementation commit: `cc315961fde901e80d8fae0bf44132d8398d1d9a`
  (Rust/CLI commit `abbfcbe`, then the Kotlin metadata panel). The later
  documentation-only commit `61b0653` changed no sources or tests.
- Branch: `issue/70-eqms-106-document-metadata`, based on `origin/main`
  `3af3eb7`.
- Environment: macOS 26.6.2/aarch64; OpenJDK 25.0.4; Rust 1.99.1; Gradle wrapper
  from the repository.

## Reproduction

From the repository root at the tested commit:

```sh
cargo build --workspace --release --locked --quiet
cargo test --workspace --locked --quiet
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked --quiet -- -D warnings

./gradlew :feature:document:jvmTest :feature:scaffold:jvmTest \
  :feature:search:jvmTest :feature:ui:jvmTest \
  :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs \
  :apps:web:compileKotlinWasmJs --rerun-tasks --console=plain --quiet
EQMS_QMS_PATH="$PWD/target/release/qms" ./gradlew :apps:desktop:test \
  --rerun-tasks --console=plain --quiet
```

## Observed results

| Check | Result |
|---|---|
| rustfmt, Clippy with denied warnings | Passed |
| Rust workspace tests | core 81 passed; CLI 15 + 5 + 12 + 3 + 1 passed; 0 failed |
| `:feature:document:jvmTest` | 55 passed; 0 failed/skipped |
| `:feature:scaffold:jvmTest` | 19 passed; 0 failed/skipped |
| `:feature:search:jvmTest` | 8 passed; 0 failed/skipped |
| `:feature:ui:jvmTest` | 6 passed; 0 failed/skipped |
| Document/scaffold/web Wasm compilation | Passed |
| `:apps:desktop:test` | 52 run; 3 skipped; **1 failed** |

## Requirement coverage

| Tests | Evidence |
|---|---|
| T106-01..04 (R106-01..04) | Rust unit/proptest tests in `markdown.rs`/`document.rs` (golden hash, validation, rejected v1, metadata-only revision with journal and diff) and CLI tests (`page_options_are_validated_…`, v2 golden CLI tests). |
| T106-05..07 | `DocumentWorkspaceStateTest` metadata tests and `DocumentScreenTest` `metadataPanel…` (light and dark; inline validation; Save only persists; read-only for Approved). |
| T106-08 | Page-setup preference/dialog removed; fakes and `DesktopDocumentCapabilitiesTest` cover `page` decoding and explicit CLI options. |
| T106-09 | Table above. |

## Failures, exclusions and limitations

- `DesktopWindowNativeTest.nativeMouseClickReachesTheUnifiedTitleBarAction`
  fails with a 5 s timeout. It fails identically on unmodified `origin/main`
  (`3af3eb7`) in this environment, so it is a pre-existing, unrelated failure
  and is **not** counted as a pass.
- No native (non-JVM) Compose test task exists beyond the above; Wasm was only
  compiled, not run. A narrow-window layout test (T106-06) uses a 720x640 dp
  window; no true small-screen device testing was done.
- Pre-release limitation: schema v1 workspaces are rejected (no migration).
- Manual review/testing, keyboard/accessibility checks and platform visual
  checks have not been performed.
