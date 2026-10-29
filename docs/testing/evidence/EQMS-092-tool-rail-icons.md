# EQMS-092 icon-only tool rail evidence

Issue: #50. Parent: #61, `feature/eqms-092-document-workspace` at
`c80db99374821535702be29b455dd05119c6aab5`. The user explicitly approved this
stacked base instead of main. Parent PR readiness was not changed.

Tested source revision: **`7ba30b3275dd3b261a56250bda08e480847ccfb3`**.
This evidence and its captures are committed separately from that revision.
Observed automated run: 2026-10-29. These are generated development records,
not approved compliance evidence or human acceptance.

## Environment and reproduction

macOS 26.6.2 arm64; JetBrains Runtime 25.0.3+-15898627-b508.16;
Gradle 9.6.1; Kotlin 2.4.10; Compose 1.12.0;
Jewel 0.41.0-262.10968.63; Cargo 1.99.1.
Use JBR 25 as JAVA_HOME and an interactive graphical desktop for native tests.
Set the output directory to a fresh absolute path outside the repository.
The preview creates isolated synthetic Git workspaces; no existing quality
workspace or global Git configuration is changed.

```sh
git checkout 7ba30b3275dd3b261a56250bda08e480847ccfb3
cargo build --workspace --release --locked
EQMS_QMS_PATH="$PWD/target/release/qms" \
EQMS_JEWEL_PREVIEW_DIR="<fresh-absolute-output-directory>" \
./gradlew :feature:ui:jvmTest :feature:search:jvmTest \
 :feature:document:jvmTest :feature:scaffold:jvmTest :apps:desktop:test \
 --rerun-tasks --console=plain --quiet
./gradlew :feature:ui:compileKotlinWasmJs \
 :feature:search:compileKotlinWasmJs :feature:document:compileKotlinWasmJs \
 :feature:scaffold:compileKotlinWasmJs :apps:web:compileKotlinWasmJs \
 --console=plain --quiet
git diff --check
```

Use an isolated checkout/worktree for reproduction; do not replace unrelated
local work. JUnit XML results live under each module's `build/test-results/`.
The preview output includes isolated repositories as well as images: retain
only the reviewed synthetic PNGs as evidence, not raw logs or workspace paths.

## Final observed results

| Surface | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| feature:ui JVM | 3 | 0 | 0 |
| feature:search JVM | 8 | 0 | 0 |
| feature:document JVM | 24 | 0 | 0 |
| feature:scaffold JVM | 19 | 0 | 0 |
| apps:desktop | 43 | 0 | 1 |
| Total | **97** | **0** | **1** |

Native release build, four feature Wasm compilations, web Wasm compilation
and whitespace check succeeded at the source revision above.

T092-16 / R092-19: themed compact 36dp icon buttons, no permanent rail text,
selected/disabled/focus semantics, keyboard activation, real pointer enter/
outside-exit tooltip visibility and both platform shortcut labels.

T092-17 / R092-20: both modifier policies and all four mappings, wrong/extra
modifiers and unrelated keys, key-up handling, repeat suppression, blocked
presses not deferred, focus-reset behavior. Compose tests exercise shortcuts
from title, body and search fields; verify dirty text/caret and editor focus,
panel select/collapse/reopen and independent opposite-side state. Busy save,
workspace busy, lifecycle confirmation, unsaved close, settings, directory
chooser and About-window isolation are covered. Existing minimum-size,
resizing, tabs/buffers, close, search and lifecycle regressions passed.

The real-adapter preview now uses the production DesktopWindow/menus with
isolated native documents. It dispatches AWT key/pointer events on the EDT to
the production canvas, verifies Project/Search toggling from an editor focus
target, captures actual rendered tooltips in both themes, and completes
Submit/Approve/Publish through native adapters while retaining a separate
dirty tab. It uses semantic setup/actions and explicit asynchronous window/
dialog waits. This verifies native-host event propagation, **not physical
keyboard/OS shortcut interoperability on every platform**.

## Captures

The final run rendered these synthetic captures at 960x720, also exercising
the production minimum window size. Captures contain no real quality records.
Historical workspace captures remain unchanged.

- [Dark compact rails and Project tooltip](EQMS-092-tool-rail-tooltip-dark.png)
- [Light compact rails and Project tooltip](EQMS-092-tool-rail-tooltip-light.png)
- [Native internal Effective state](EQMS-092-tool-rail-effective.png)
- [Collapsed panels with central tabs](EQMS-092-tool-rail-collapsed.png)

## Failures encountered and limitations

Development checks initially failed for a missing experimental Tooltip API
opt-in and a Kotlin collection assertion type mismatch; both were corrected.
Hover tests initially used an exit event at an inside position, leaving
Jewel's hover area active; they now exit outside the target.

Early native preview attempts failed due to off-EDT synthetic mouse injection,
OS focus races, auto-hidden tooltips and asynchronous dialog/tab composition.
The fixture now uses semantic setup, atomic EDT key/pointer dispatch,
controlled capture timing and explicit waits instead of relying on OS key
focus. An earlier combined run also timed out in the unchanged native
titlebar mouse test; that test passed in the final complete run.
No failed attempt is counted as a pass; the final table is from the successful
complete rerun at the identified revision.

The final JVM run emitted Skiko native-access warnings and a Cocoa AWT
window-deallocation/AppKit-thread warning; the command exited successfully and
JUnit recorded no errors. These runtime warnings remain a limitation.

The single skipped test is the opt-in prepared-reference search benchmark:
no prepared 10k fixture is configured for this run. Rail changes do not alter
result rendering or query timing. No new p95/performance claim is made, and a
skip is not functional or performance coverage. Rust source/domain/protocol
are unchanged; a native release build and real-adapter regressions ran, not
the full Rust unit/Clippy suite. No dependency was added.

Windows/Linux modifier matching is policy-tested on macOS, not natively run.
Packaging, assistive-technology and human workflow acceptance were not run.
This increment adds no identity authority, signatures or external publication:
Effective remains the existing internal lifecycle state.

## Required manual gates

- [ ] Human code/design/risk review of #50 and this stacked increment.
- [ ] Manual dark/light icon recognition, tooltip, selection and keyboard focus.
- [ ] Physical macOS Command+1..4 and Windows/Linux Alt+1..4, including OS/menu
  conflicts, held keys, editor/search focus and application/window switching.
- [ ] Manual busy/modal, unsaved-buffer and create/review/publish walkthrough.
- [ ] Assistive-technology review of labels, selection, focus and disabled tools.
- [ ] Review minimum-size layout, captures and runtime warnings before merge.

Leave #50 open/In progress and the new PR draft until required manual review/
testing is completed. Do not merge or consolidate the stack automatically.
