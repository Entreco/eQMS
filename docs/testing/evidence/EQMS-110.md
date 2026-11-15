# EQMS-110 automated test evidence

Draft, generated record; not approved compliance evidence. Manual review and manual testing are still required.

- Tested revision: `1028f540db99a2557f608c01817e22bc71ea00fc` (branch `issue/eqms-110-project-creation-branding`, base `8ff676c`). Only this evidence file changed afterwards. Rust results below were last run at `a8dbecd`; later commits changed no Rust code.
- Environment: macOS 26.6.2, OpenJDK 25.0.4, rustc 1.99.1, Gradle wrapper

## Commands and outcomes

| Command | Outcome |
|---|---|
| `cargo fmt --check` | Pass |
| `cargo clippy --all-targets -- -D warnings` | Pass |
| `cargo test` | Pass: 196 passed, 0 failed (includes `preview_needs_no_workspace_and_validates_like_set`, `branding_preview_matches_what_init_records`) |
| `./gradlew :apps:desktop:test :feature:ui:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:scaffold:jvmTest --continue` | Pass, including new `ProjectBrandingCreateTest` (6), `LogoPreviewTest` (2), `DesktopProjectBrandingTest` (4). Three existing tests initially failed because New project gained the identity step; their fakes and flow were updated (`confirmIdentity`) and re-run. |
| `./gradlew :feature:ui:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs` | Compiles |
| `./gradlew :feature:scaffold:compileIntegrationTestKotlinJvm` | Compiles, incl. new `dialogsAreDecoratedResizableWindowsThatCloseFromTheirChrome` (T110-09) |
| Native integration tests (`integrationTest`) | Not run: CI-only on macOS runners (EQMS-127), so the dialog-window behaviour (R110-11) is not yet observed |

## Requirement mapping

R110-01/02 -> T110-01, T110-02; R110-03..R110-07 -> T110-03; R110-04/06 -> T110-04; R110-01/07 -> T110-05; R110-09 -> T110-06 (label reachability only); R110-08 -> T110-02; R110-10 -> T110-07.

## Observed latency (T110-08)

`qms project branding preview` (release build, 30 runs, macOS): median 3.0 ms, p95 8.1 ms, max 337.6 ms (first, cold run). Process start only; the UI adds a 200 ms input debounce. No threshold was asserted and no UI frame timing was measured.

## Limitations

- Dialog windows (R110-11) were never rendered or exercised by me; unit tests use the in-scene fallback.
- The manual checklist in `docs/testing/EQMS-110.md` is not executed; the real application window was not driven by an automated test or a human.
- The Browse system file dialog and real Tab/Enter/Escape traversal are untested; PNG rendering of a real file is covered only through the unreadable-file fallback.
- Failure after `qms init` leaves an initialised folder (core is not transactional across init and branding); the UI says so.
- Editing branding of an existing project and chrome display remain EQMS-111.
- Project board item could not be updated: the available `gh` token lacks project scopes.
