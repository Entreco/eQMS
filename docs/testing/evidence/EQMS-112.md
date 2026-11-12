---
title: EQMS-112 automated test evidence
status: observed-automated-results
related: [EQMS-112]
---

# EQMS-112 test evidence

## Tested revision and environment

- Tested revision: `b303efda84c988328f3660513c3f6a8db334a0a9` (based on origin/main `5083340`)
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`

## Commands and observed results

Run with `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null` (no global git identity, as in CI).

| Command | Result |
|---|---|
| `cargo fmt --all --check` | Passed |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo test --workspace --locked` | Passed: 122 core unit tests (7 template), 17 CLI unit tests (1 template), all integration suites |

## Limitations

- No Kotlin code changed; JVM, native and Wasm suites were not run.
- Templates are not yet bound to documents or used by rendering (#85, #87).
- Assets use synthetic PNG structures in tests; the CLI does not decode them.
- Manual review/testing is not performed. Draft evidence, not approved compliance evidence.
