---
title: EQMS-107 automated test evidence
status: observed-automated-results
related: [EQMS-107]
---

# EQMS-107 test evidence

## Tested revision and environment

- Tested revision: `e1f641861c0e6e78425652cfc14978b23deb81da`
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all --check` | Passed |
| `cargo clippy --workspace --all-targets -- -D warnings` | Passed |
| `cargo test --workspace` | Passed: all suites, incl. 106 core unit tests (9 branding); rerun after merging origin/main |
| Manual CLI smoke: `qms init <dir> --name "Acme Medical" --accent auto`, `project branding show --json`, `set --accent`, `show` on a plain workspace, `set --logo-file /nonexistent`, `verify` | Behaved as specified (persisted flag, warnings, error, 3 verified journal records) |

## Limitations

- Kotlin/JVM, native and Wasm suites were not run: no Kotlin code changed.
- No real-PNG fixture is exercised in automated tests; the core tests use synthetic PNG structures and the CLI decode path is only covered by its rejection case.
- Manual review/testing is not performed.
- Draft evidence, not approved compliance evidence.
