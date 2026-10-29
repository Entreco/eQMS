# EQMS-092 whole-tab hover and panel contrast correction

Issue #50, existing draft PR #62. Tested source:
**`8c7438e2e2b370b6c8bea02af4c5ed48826c3edd`**, 2026-10-29.
Evidence and captures committed separately. This supplements the
[previous UX review](EQMS-092-workspace-ux-review.md); it does not rewrite it.

## Root cause and correction

The earlier rounded panels used the same fill as their shell, so the clipped
corners were barely discernible. Panel fills now use a darker neutral in dark
mode and white in light mode, leaving the shell visible through the corners
and gutters. Tab hover relied on theme tokens without ensuring an observable
whole-tab change. A shared hover source now covers the entire tab, including
the close child, with an explicit foreground-blended highlight. Disabled tabs
do not highlight; selection remains visible after pointer exit.

R092-21 / T092-18 now verifies rendered pixels rather than just implementation
shape/state: selected/inactive tab backgrounds change by more than 0.03 in
the red channel away from the close icon; hovering the close child retains
the same whole-tab highlight; disabled hover leaves pixels unchanged.
For Project, Review and editor surfaces, corner cutouts and panel fills differ
by at least 0.025 in the red channel and opposite cutouts expose the same shell.
All checks run in both themes. These thresholds establish a visible change,
not WCAG compliance or human visual approval.

## Reproduction and observed results

Environment: macOS 26.6.2 arm64, JBR 25.0.3+-15898627-b508.16, Gradle 9.6.1,
Kotlin 2.4.10, Compose 1.12.0, Jewel 0.41.0-262.10968.63, Cargo 1.99.1.
Use an isolated checkout of the source above, JBR as JAVA_HOME and a graphical
desktop. Choose a fresh absolute output directory outside the repository.

```sh
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

| Surface | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| feature:ui JVM | 6 | 0 | 0 |
| feature:search JVM | 8 | 0 | 0 |
| feature:document JVM | 27 | 0 | 0 |
| feature:scaffold JVM | 19 | 0 | 0 |
| apps:desktop | 43 | 0 | 1 |
| Total | **103** | **0** | **1** |

Native release build, four feature Wasm compilations, web Wasm compilation
and whitespace check succeeded. Existing pointer resize, shortcut, dirty-close,
editor-buffer and lifecycle regressions passed.

The first new hover test failed because it sent an exit before the test mouse
was hovering. The fixture now moves the pointer outside/inside normally.
The final complete rerun above passed at the identified revision.

## Synthetic previews and limitations

- [Dark surfaces](EQMS-092-contrast-dark.png)
- [Light surfaces](EQMS-092-contrast-light.png)
- [Whole-tab hover, dark](EQMS-092-tab-hover-dark.png)
- [Whole-tab hover, light](EQMS-092-tab-hover-light.png)

Generated at 1100x800 through the production host with isolated synthetic
native documents and EDT-dispatched input. No real quality records are used.
The prepared 10k reference benchmark remains unconfigured/skipped, not passed;
no new performance claim. Full unchanged Rust unit/Clippy suites, packaging,
physical OS input, native Windows/Linux and assistive-technology tests were
not run. The successful JVM run still emitted Skiko native-access and Cocoa
AWT deallocation warnings documented in the previous evidence.

- [ ] Human visual acceptance of rounded silhouettes and whole-tab hover.
- [ ] Physical platform input and accessibility review.
- [ ] Review remaining runtime warnings and existing workflow manual checklist.

Generated records are not approved compliance evidence. Keep #62 draft and
#50 Open/In progress; no merge or stack consolidation is requested.
