# EQMS-092 document workspace: observed automated results

Issue: #50. Backend dependency: #16 / draft PR #60.

Tested source revision: `57b65202943226c99b6413be4eb90cb82164a407`.
The source commit precedes this separate evidence/capture commit. The user
explicitly approved stacking the UI branch on lifecycle tip
`4be3b76054d283fb885f91b0ce79da2f5afd0d0f`, instead of main.

Mappings: [T092-10 through T092-15](../EQMS-092.md).
Design/risk: [EQMS-092](../../architecture/EQMS-092.md).
Requirements: [R092-12 through R092-18](../../requirements/EQMS-092.md).

## Environment and reproducible commands

macOS 26.6.2 arm64, JBR `25.0.3+-15898627-b508.16`, Gradle 9.6.1.
Pinned project Kotlin/Compose/Jewel versions were unchanged. UI/test tasks ran
under JBR. Paths below are placeholders; the output directory contains only
generated synthetic fixtures and captures, not existing user workspaces.

```sh
cargo build --workspace --release --locked
EQMS_JEWEL_PREVIEW_DIR=<absolute-isolated-output-directory> \
EQMS_QMS_PATH="$PWD/target/release/qms" \
./gradlew :feature:ui:jvmTest :feature:search:jvmTest \
 :feature:document:jvmTest :feature:scaffold:jvmTest :apps:desktop:test \
 :feature:ui:compileKotlinWasmJs :feature:search:compileKotlinWasmJs \
 :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs \
 :apps:web:compileKotlinWasmJs --rerun-tasks --console=plain --quiet
git diff --check
git diff 4be3b76054d283fb885f91b0ce79da2f5afd0d0f -- \
 core/rust apps/cli Cargo.toml Cargo.lock
```

Final execution at the source revision completed successfully with a clean
worktree. The native/Cargo diff against the parent was empty.

## Observed outcomes

| Target/check | Outcome |
|---|---|
| feature:ui JVM tests | 1 passed |
| feature:search JVM tests | 8 passed |
| feature:document JVM tests | 19 passed |
| feature:scaffold JVM tests | 18 passed |
| apps:desktop tests | 43 passed, 1 explicitly skipped |
| Combined | 89 passed, 0 failed, 1 skipped |
| All four feature Wasm compilations and web Wasm compilation | Passed |
| Native release build | Passed |
| Whitespace / native-boundary diff | Passed / no native source changes |

Coverage includes independent dirty buffers, latest/history tab deduplication,
caret retention, source-bound late responses, cancelled/failed project opening,
Save/Discard/Cancel, partial Save all failure, invalid title, native menu/quit
guards, theme/search retention, bounded panel controls and keyboard operation at
960x720, strict status/transition/event protocol validation, confirmed lifecycle
actions, failed/stale transitions, read-only and terminal states, history/diff
and existing grouped/paginated search behavior.

`DesktopLifecycleProcessTest` executed against real native processes in an
isolated synthetic Git workspace. It created/saved a document, submitted,
approved, made it Effective, reopened through a new adapter, read the durable
history, exercised both terminal states, queried search and ran ledger
verification. Content hashes/body, baseline HEAD, staged index bytes and
baseline journal bytes remained unchanged by lifecycle actions. A
lifecycle-only content diff was empty.

`DesktopJewelPreviewTest` executed (not skipped), using real workspace and
document adapters. It rendered two tabs with an independent unsaved buffer,
switched themes, compared revisions, drove the lifecycle through Effective,
collapsed both side panels and verified that the other tab's edits remained.

## Captures

These are generated Compose-content captures at the tested revision, not OS
chrome captures or evidence of human usability review.

- [Dark workspace](EQMS-092-workspace-dark.png)
- [Light workspace](EQMS-092-workspace-light.png)
- [History](EQMS-092-workspace-history.png)
- [Exact diff](EQMS-092-workspace-diff.png)
- [In Review](EQMS-092-workspace-in-review.png)
- [Approved](EQMS-092-workspace-approved.png)
- [Effective](EQMS-092-workspace-effective.png)
- [Collapsed panels](EQMS-092-workspace-collapsed.png)

## Earlier failures and resolutions

Initial regression attempts failed on obsolete Draft/search/navigation
selectors and old single-buffer/discard behavior. Tests were migrated to
neutral saved-document wording, retained multi-tab buffers and the approved
Save/Discard/Cancel policy; their save/conflict/search/history assertions were
retained. A dirty-state callback was initially evaluated outside composition
tracking; it is now read during composition and covered by save/close tests.
A misplaced reload method caused an intermediate compile failure and was fixed.

Real-process preview attempts exposed an asynchronous-open wait and an
exact-versus-substring assertion error; explicit enabled/state waits and the
correct substring assertion now pass. An attempted range-selection assertion
observed toolkit focus-loss collapse to its end caret; the regression checks
retained caret position across tab changes instead of claiming persistent
selection highlighting. Final commands above had no failures. JBR/Skiko
restricted-native-access warnings were non-failing.

## Exclusions and remaining gates

- The opt-in `DesktopSearchBenchmarkTest` was skipped because
  `EQMS_SEARCH_BENCHMARK_WORKSPACE` was not configured and no prepared reference
  fixture was located in the repository. No new 10k-document p95 result is
  claimed. Re-run the existing 20-warmup/100-sample, p95 <=150 ms benchmark when
  that fixture is available; the threshold and measurement remain unchanged.
- The Rust unit suite was not repeated for this UI-only diff. Native sources
  and Cargo metadata are unchanged; the native release build and new real
  process integration executed.
- No Windows/Linux runtime, full browser authoring, installer/package run,
  screen-reader assessment, exhaustive interruption test or human acceptance
  testing is claimed. Web remains a compilable bootstrap.
- Layout/open tabs/unsaved buffers are session-only. This is not crash recovery.
- Effective is internal lifecycle status only. No controlled-baseline merge,
  immutable release record, external distribution, signing, authenticated
  reviewer authority or trusted time is implemented or implied.

This is draft automated evidence, not compliance approval. Keep the issue
open/In progress and the stacked PR draft pending normal review/testing.

- [ ] Manually review architecture, risks, contracts and implementation.
- [ ] Manually exercise multiple dirty tabs, save conflicts, close/project/quit guards.
- [ ] Review both themes, panel sizing, keyboard/focus and native menu integration.
- [ ] Manually exercise submit/approve/Publish and confirm the internal-only limits.
- [ ] Run the opt-in prepared-reference performance regression when its fixture is available.
