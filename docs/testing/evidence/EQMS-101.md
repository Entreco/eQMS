---
title: EQMS-101 automated test evidence
status: draft
related: [EQMS-101]
---

# EQMS-101 test evidence

## Tested revision and environment

- Tested code revision: `c674a558cc568eac2896bbfe8fff15948942f2e4`
- Test date: 2026-10-21
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1`, `cargo 1.99.1`
- JVM: OpenJDK 25.0.3

## Automated results

| Command | Observed result |
|---|---|
| `cargo fmt --all -- --check` | Passed. |
| `cargo clippy -p eqms-cli --all-targets --locked -- -D warnings` | Passed with warnings denied. |
| `cargo test --workspace --locked` | Passed: 53 Rust unit and integration tests; 0 failures. |
| `cargo build --workspace --release --locked` | Passed. |
| `./gradlew :apps:desktop:test --no-daemon` | Passed: 17 desktop tests; 0 failures or errors. |

The Rust tests cover schema-v2 version, workspace and document successes,
structured usage and operation failures, timestamps and required envelope
fields, preserved command data, and unchanged human-readable output. Desktop
adapter tests validate schema-v2 envelopes, nested payload decoding, structured
errors, and rejection of malformed or unsupported responses.

## Limitations and manual review

No manual GUI, human review, or native CLI-to-desktop manual integration test was
performed. Those checks remain pending before merge. Automated evidence records
only the commands and environment above; it does not establish trusted time,
human approval, or product compliance.
