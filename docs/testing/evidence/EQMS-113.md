---
title: EQMS-113 automated test evidence
status: observed-automated-results
related: [EQMS-113]
---

# EQMS-113 test evidence

## Tested revision and environment

- Tested revision: `5edfdac8d05f8a99e709bc6eeba3da127d324223` (stacked on the EQMS-112 branch, `2c06974`)
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`

## Commands and observed results

Run with `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null` (no global git identity, as in CI).

| Command | Result |
|---|---|
| `cargo fmt --all --check` | Passed |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo test --workspace --locked` | Passed: 128 core unit tests (2 markdown pin, 4 document binding), 17 CLI unit tests, all CLI integration suites including `template_binding` |

Mapping: T113-01 (markdown pin tests), T113-02/03/04 (document binding tests), T113-05 (`apps/cli/tests/template_binding.rs`), T113-06 (the commands above).

## Limitations

- No Kotlin code changed; JVM, native and Wasm suites were not run.
- The pin is not re-verified against the template store on every read; there is no conformance, rendering or UI use of it yet (#86-#88).
- Manual review/testing is not performed. Draft evidence, not approved compliance evidence.
