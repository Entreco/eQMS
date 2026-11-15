# EQMS-115 test evidence

Automated results observed on a development machine. This is **not** approved
compliance evidence and records no human testing.

- **Tested revision:** `307ac8eb7dc27fdd25b1982eeb0cdfca71b3d8ff` (branch `issue/eqms-115-template-style-engine`; code identical, run before the docs-only commit that adds this file)
- **Environment:** macOS 26.x, rustc 1.99.1, OpenJDK 25, Gradle wrapper
- **Plan:** `docs/testing/EQMS-115.md`

| Command | Outcome |
| --- | --- |
| `GIT_CONFIG_GLOBAL=/dev/null cargo test --workspace --locked` | All suites passed (core unit tests 149 passed, incl. `style` unit tests, `style_golden`, `style_project`, CLI `style_render`) |
| `cargo fmt --check` | Clean |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Clean |
| `cargo build --release --locked` then `./gradlew :apps:desktop:test :feature:ui:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:scaffold:jvmTest` | apps/desktop 67 tests (3 skipped, pre-existing), feature/document 78, feature/ui 11, feature/search 8, feature/scaffold 45; 0 failures |
| `./gradlew :feature:ui:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs :apps:desktop:compileIntegrationTestKotlin :feature:scaffold:compileIntegrationTestKotlinJvm` | Compiled |
| `scripts/eqms-115-sample-project.sh <tmp>` then `qms document render <tmp> <id> --style --json` | Pinned document returned style "Formal SOP" (origin pinned, cover, numbers, template asset image) plus the generated-logo diagnostic |

## Requirement coverage

R115-01..08 by T115-01..05 (Rust unit, golden, project and CLI tests); R115-09/10 by the desktop decoder/capability tests, `DocumentPaginationTest` (cover, contents with real page numbers, page break, heading numbers, region slots, unchanged unstyled output) and `DocumentScreenTest` (preview-style picker); see `docs/testing/EQMS-115.md`.

## Limitations

- The visual result (colours, fonts, spacing, header/footer images) is **not** machine-verified; the manual checklist in `docs/testing/EQMS-115.md` is pending.
- Fonts are not bundled; no native-image build was run.
- Journal verification on each render is not benchmarked.
- The project board could not be read or updated (token lacks `read:project`).
