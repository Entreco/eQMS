# EQMS-021 automated test evidence

## Tested revision and environment

- Source revision: `19d7401ced26311406130b6b90b2cb95df806975`
- Environment: macOS arm64 (Darwin); `rustc 1.99.1 (58cf8f9c3 2026-10-13)`;
  `cargo 1.99.1 (5f94df478 2026-08-27)`
- The evidence record is committed separately from the tested source revision.

## Commands and outcomes

| Command | Outcome |
|---|---|
| `cargo test --workspace --locked` | Passed: 100 tests across CLI unit, audit, CLI integration, Markdown integration, search integration, and Rust core targets; 0 failed. |
| `cargo fmt --all -- --check` | Passed. |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed with warnings denied. |
| `git diff --check` | Passed before the source commit. |

Coverage includes the allowed/invalid transition graph, durable state and event
history after reopen, expected-revision conflicts, unchanged canonical content,
baseline/index/working-tree isolation, CLI human/JSON responses, and existing
workspace and document-command regressions.

## Limitations

These are automated results on macOS arm64 only. No human review or manual
acceptance testing is claimed. No Windows/Linux runtime, external release,
cryptographic signature, authenticated reviewer identity, trusted-time,
exhaustive process/power-loss fault injection, or performance benchmark was
tested. Lifecycle events remain subject to the documented local Git trust
limitations; they do not establish regulatory approval.
