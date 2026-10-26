---
title: EQMS-003 automated test evidence
status: observed-automated-results
related: [EQMS-003]
---

# EQMS-003 test evidence

## Tested revision and environment

- Tested revision: `eaa37e45fd11780b87091aff71a60d7bc1c29e20`
- Test date: 2026-10-26
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed. |
| `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 cargo test --workspace --locked` | Passed: 71 Rust unit and integration tests; 0 failures, 0 doc tests. |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed with warnings denied. |
| `cargo tree -p eqms-core --depth 1` | Confirmed the core dependency graph has no CLI or UI framework dependency. |
| `git diff --check` | Passed. |

The model tests cover all six entity round-trips, schema-version rejection,
UUID serialization, RFC 3339 UTC normalization and invalid values, and
namespaced plugin type IDs with opaque JSON data.

## Limitations and manual review — pending

- Automated execution was on macOS only; Linux and Windows were not tested.
- No downstream consumer or plugin runtime is implemented by this issue.
- Timestamp normalization does not establish trusted time. Signature records do
  not perform cryptographic signing or verification.
- No human review or manual testing was performed. Automated results are not
  human approval or compliance evidence.

## Manual checklist — pending

- [ ] Human reviews the model design, risk assessment, requirements, mappings,
  and this evidence.
- [ ] Human inspects serialized examples and confirms the contract is clear for
  intended consumers.
- [ ] Reviewer records the manual outcome before merge.
