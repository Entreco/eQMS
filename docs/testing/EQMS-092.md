---
title: EQMS-092 Jewel scaffold tests
status: draft
related: [EQMS-092]
---

# EQMS-092 test specification

Issue: #50. Define mappings before implementation. Preserve existing test IDs.

| ID | Requirements | Planned check |
|---|---|---|
| T092-01 | R092-01, R092-07 | Resolve pinned Jewel/Compose artifacts, compile JVM and Wasm, verify no Material UI imports and no Jewel in Wasm dependencies. |
| T092-02 | R092-02, R092-03 | Compose tests: welcome, real create/open callbacks, dark/light toggle retains project, dirty editor and search. |
| T092-03 | R092-04 | Existing ProjectScreenTest and desktop project/process adapter tests: selection, creation, folder errors, cancellation and busy guards. |
| T092-04 | R092-05 | Existing DocumentScreenTest: save/conflict retention, historical read-only, comparison, navigation/close guards; add comparison failure regression. |
| T092-05 | R092-06 | Existing DocumentSearchTest, DocumentScreenTest and desktop search adapter tests: groups, pagination, latest/history selection and exactly-once render timings. Preserve benchmark opt-in; measure real prepared fixture if available. |
| T092-06 | R092-08 | Automated keyboard focus/activation check; human review of focus, readability, status honesty, both themes, scrolling and native dialog integration remains pending. |
| T092-07 | R092-01, R092-09 | Build native CLI, launch real app under JBR against isolated sample drafts, capture dark/light views if available. Record environment and exact limitations; never claim human review from automation. |
| T092-08 | R092-10 | Inspect Gradle projects/dependencies for removed modules/JDBC; build/run Kotlin CLI placeholder without creating SQLite state, compile desktop and web, run affected UI/adapter regressions. Confirm no Rust or database changes. Historical evidence is preserved as historical, not rewritten. |
| T092-09 | R092-07, R092-11 | Verify Gradle feature graph is acyclic and core:ui absent, tracked core/ has no Kotlin, all feature JVM tests and Wasm targets compile, desktop adapters/preview still work. SearchPanelTest covers injected blank-query document content, groups, selected callbacks, history, pagination and layout timing without the editor/scaffold. Re-run real 10k search timing after extracting result layout. Preserve test counts across feature-local suites and native Rust files byte-for-byte. |

Run focused selectors while iterating, then:

```sh
./gradlew :feature:ui:jvmTest :feature:search:jvmTest :feature:document:jvmTest :feature:scaffold:jvmTest :apps:desktop:test
./gradlew :feature:ui:compileKotlinWasmJs :feature:search:compileKotlinWasmJs :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs :apps:web:compileKotlinWasmJs
cargo build --workspace --release --locked
./gradlew :apps:desktop:run
```

Evidence: [EQMS-092 observed results](evidence/EQMS-092.md).
Feature restructuring: [revision-specific results](evidence/EQMS-092-feature-split.md).
Human review/testing must remain unchecked until actually performed.

## Document workspace follow-up mapping

Define before implementation; earlier IDs/evidence are retained.

| ID | Requirements | Planned check |
|---|---|---|
| T092-10 | R092-12, R092-13 | Compose/state tests: independent dirty tabs, deduplication, historical/latest coexistence, panel selection/collapse/bounded resizing, keyboard controls, minimum size and theme retention. |
| T092-11 | R092-14 | Tab/project/menu/quit Save/Discard/Cancel, multi-buffer partial failure, invalid title, busy guards and cancelled/failed project opening retain text. |
| T092-12 | R092-15 | Adapter/process tests cover six states, transition args/expected revision, wrong ID/target/content, unknown/missing status, malformed history/envelope, exit/launch errors and cancellation. |
| T092-13 | R092-13, R092-16 | State/UI tests: confirmed clean-only lifecycle actions, read-only after submit, terminal/historical guards, stale failure, duplicate clicks and source-bound late results. |
| T092-14 | R092-15, R092-16, R092-17 | Real native workspace create/save/submit/approve/publish/reopen, lifecycle history and unchanged content/baseline/index; captures in both themes distinguish internal state from signatures/releases. |
| T092-15 | R092-17, R092-18 | Existing feature/desktop regressions, neutral search/history wording, exact diff including lifecycle-only no-change revisions, all feature/web Wasm compilation; rerun opt-in 10k p95 <=150 ms benchmark if result rendering changes. |
| T092-16 | R092-19 | Real hover/dismissal, name/shortcut formatting, icon-only compact rails, accessible selected/enabled/focus semantics, dark/light previews and minimum-width layout. Manual assistive-technology and visual acceptance remain unchecked. |
| T092-17 | R092-13, R092-18, R092-20 | Both platform policies, all four toggles, extra/wrong modifiers, plain digit input, key-down/up/repeats and focus reset; editor/title/search focus and dirty-buffer/caret retention; busy, lifecycle/unsaved dialogs and scaffold modal guards; existing UI/native regressions and Wasm compilation. |
| T092-18 | R092-13, R092-14, R092-19, R092-21 | Assert left/right tooltip geometry at different pointer heights; real split drags change tool/editor widths, obey bounds/disabled state and show native resize cursor. Verify tab selected/disabled/focus, integrated close and dirty guards, and active-tab visibility. Review generated dark/light rounded-panel/tab captures; manual visual acceptance remains unchecked. |

Icon-rail follow-up evidence belongs in
`evidence/EQMS-092-tool-rail-icons.md`, identifying its tested source revision
separately from the earlier workspace evidence. Policy tests do not establish
Windows/Linux native runtime behavior. The icon-only increment does not change
search results/timing; the earlier missing-fixture benchmark is not a new pass.

Evidence belongs in `evidence/EQMS-092-document-workspace.md` with the exact
tested source revision, environment, commands, observed outcomes/exclusions
and unchecked manual review/testing. Skipped opt-in tests are not coverage.
