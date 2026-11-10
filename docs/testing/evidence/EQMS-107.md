---
title: EQMS-107 automated test evidence
status: observed-automated-results
related: [EQMS-107]
---

# EQMS-107 test evidence

## Tested revision and environment

- Tested revision: `241d9bee19037d17aa7af25df287d74c0971ffbb`
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all --check` | Passed |
| `cargo clippy --workspace --all-targets -- -D warnings` | Passed |
| `cargo test --workspace` | Passed: 16 + 5 CLI unit/integration, 12 + 3 + 1 other integration, 90 core unit tests (incl. 9 branding) |
| Manual CLI smoke: `qms init <dir> --name "Acme Medical" --accent auto`, `project branding show --json`, `set --accent`, `show` on a plain workspace, `set --logo-file /nonexistent`, `verify` | Behaved as specified (persisted flag, warnings, error, 3 verified journal records) |

## Limitations

- Kotlin/JVM, native and Wasm suites were not run: no Kotlin code changed.
- No real-PNG fixture is exercised in automated tests; the core tests use synthetic PNG structures and the CLI decode path is only covered by its rejection case.
- Manual review/testing is not performed.
- Draft evidence, not approved compliance evidence.
