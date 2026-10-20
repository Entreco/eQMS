---
title: EQMS-090 desktop create/open test evidence
status: observed-automated-results
related: [EQMS-090]
---

# EQMS-090 test evidence

## Tested revision and environment

- Tested revision: `7467030b6dffcac91841d3750bd2cfdd929ee7a4`
- Test date: 2026-10-20
- Platform: macOS 26.6.2, Apple Silicon
- Gradle: 9.6.1
- JDK: 25.0.3
- Kotlin Gradle plugin: 2.3.10
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command or check | Result |
|---|---|
| `./gradlew --no-daemon --rerun-tasks :apps:desktop:test :apps:desktop:jar` | Passed: 5 desktop adapter tests and application JAR build |
| `./gradlew --no-daemon :apps:desktop:run --dry-run` | Passed: Compose Desktop run task is configured |
| `cargo fmt --all -- --check` | Passed |
| `cargo test --workspace --locked` | Passed: 25 core tests, 9 CLI tests, 0 doc tests |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo build --workspace --release --locked` | Passed |
| Native CLI smoke test with a temporary path containing spaces | `qms init --json` and `qms verify --json` both returned schema-version-1 success responses |
| `git diff --check` | Passed |

The desktop adapter tests verify separate argument passing, paths containing
spaces, successful `init`/`verify` response handling, structured CLI errors,
launch failure, malformed JSON, unsupported schema versions, and unexpected
response commands or fields.

## Limitations and pending manual review/testing

- The Compose UI was compiled but not manually exercised. No human GUI review,
  project-chooser testing, or manual integration testing has been performed.
- Native application packaging was attempted but the configured Android Studio
  JDK does not include a usable `jpackage`; packaging is outside this milestone.
- The full multi-module `./gradlew build` did not complete because the web/Wasm
  module could not resolve `com.github.webassembly:binaryen:125` from the
  configured Maven Central and Google repositories. The focused desktop build
  and tests passed.
- The UI needs the native `qms` executable available on `PATH` or the
  `EQMS_QMS_PATH` environment variable. Packaging and executable provenance are
  not implemented.

## Manual checklist — pending

- [ ] Human reviews the design, requirements, test mappings, evidence, and
  implementation.
- [ ] Launch the desktop app with `EQMS_QMS_PATH` set and confirm Create project
  and Open project are visible.
- [ ] Create a project, verify it opens only after successful initialization,
  then open the same project through the verify flow.
- [ ] Cancel each chooser and confirm no workspace operation is performed.
- [ ] Try an invalid workspace, a non-empty create destination, and an
  unavailable CLI; confirm clear errors and no success state.
- [ ] Reviewer records the manual outcome before merge.

These observations are automated test results, not human approval or compliance
evidence.

## Folder chooser refinement

- Tested revision: `4be044807dfe9f4d26fa0b5aafdf33181ad325fa`
- Environment: same macOS/JDK/Gradle environment described above.
- `./gradlew --no-daemon --rerun-tasks :apps:desktop:test :apps:desktop:jar`
  passed: 10 desktop tests, including 5 new folder/chooser tests, and JAR build.
- `git diff --check` passed.
- An initial compilation attempt failed after removing a still-needed
  `JFileChooser` import; the import was restored before the passing runs.
- T090-07 covered normal/spaced names, invalid names, exclusive creation,
  preservation of existing content, missing-parent failure propagation, and
  Create-only accessory installation on the Swing event thread.
- The existing Gradle `run`/`hotRun` local CLI-path fallback is included in this
  revision. It preserves explicit `EQMS_QMS_PATH` overrides.
- T090-08 remains pending: no human confirmation of visible button placement,
  prompt cancellation, folder selection, or error dialogs in Android Studio
  hotRun is claimed. Cancelling the chooser intentionally leaves any newly
  created directory on disk without initializing it.

### Additional manual checklist

- [ ] Confirm **New folder** is visible in Create project on macOS hotRun.
- [ ] Create a folder with spaces, confirm selection, and initialize it.
- [ ] Confirm invalid/existing names and permission errors are visible.
- [ ] Cancel the name prompt; confirm no folder is created.
- [ ] Create a folder then cancel the chooser; confirm it remains empty.
- [ ] Confirm Open project has no explicit New folder accessory.
