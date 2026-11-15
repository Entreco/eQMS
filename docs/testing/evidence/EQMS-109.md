# EQMS-109 automated test evidence

Draft, generated record; not approved compliance evidence. Manual review and manual testing are still required.

- Tested revision: `0c2e5991088e2a36c631a7437550d686ac442c51` (branch `issue/eqms-109-brand-tokens`, base `82c5303`). Only the design-doc risk table wording changed afterwards.
- Environment: macOS 26.6.2, OpenJDK 25.0.4, rustc 1.99.1, Gradle wrapper

## Commands and outcomes

| Command | Outcome |
|---|---|
| `./gradlew :feature:ui:jvmTest :feature:scaffold:jvmTest :feature:document:jvmTest :feature:search:jvmTest :apps:desktop:test --continue` | Pass, including `BrandTokensTest`, `BrandThemeTest`, `ProjectBrandingTest`, `DesktopBrandTokensTest`. An earlier run failed one new test (wrong expected button label "Dark theme"); the test was fixed and everything re-run. |
| `./gradlew :feature:ui:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs` | Compiles |
| `cargo fmt --check` / `cargo test -q` | Pass (no Rust changes; regression only) |
| Native integration tests (`integrationTest`) | Not run: CI-only on macOS runners (EQMS-127) |

## Requirement mapping

R109-01/02 -> T109-01/02; R109-03/04 -> T109-03; R109-05 -> T109-04; R109-06 -> T109-05 (incl. 360 dp width); R109-07 -> T109-07. T109-06 (no hard-coded accent literals) is a review check: the only remaining colour literals in chrome code are the fixed `Paper` document colours, which are intentionally not branded.

## Limitations

- Manual checklist in `docs/testing/EQMS-109.md` is not executed.
- The logo is parsed but not drawn; Jewel's own accent colours are not re-themed; no UI to edit branding.
- Narrow-window coverage is a 360 dp container in a Compose test, not a real resized window.
- Project board item could not be updated: the available `gh` token lacks project scopes.
