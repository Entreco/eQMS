# EQMS-013 automated test evidence

## Tested revision and environment

- Source revision: `fc78e6962688fb0be0d5aaa0a461ff4c2211d429` (differs from the
  tested working tree only by one documentation sentence in
  `docs/architecture/EQMS-013.md`)
- Environment: macOS arm64 (Darwin); `rustc 1.99.1 (58cf8f9c3 2026-10-13)`;
  `cargo 1.99.1 (5f94df478 2026-08-27)`
- The evidence record is committed separately from the tested source revision.

## Commands and outcomes

| Command | Outcome |
|---|---|
| `cargo test --workspace --locked` | Passed, 0 failed, including TEST-013-001..004 (core `roles` unit tests) and TEST-013-005/007 (`apps/cli/tests/approval.rs`); existing suites unchanged and passing (85 core tests, CLI unit/integration targets). |
| `cargo fmt --all -- --check` | Passed. |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed with warnings denied. |
| `git diff --cached --check` | Passed before the source commit. |

TEST-013-006 is verified by review: `core/rust/src/roles.rs` uses only
`serde`, `serde_json` and `std`; no dependency was added. TEST-013-008 is the
workspace regression run above.

## Limitations

Automated results on macOS arm64 only; no human review or manual acceptance
testing is claimed. Roles are asserted input: no identity, authentication,
signature, separation-of-duties or workflow enforcement was implemented or
tested. The matrix is not yet consumed by `qms document transition` or release
operations. Draft records pending manual review.
