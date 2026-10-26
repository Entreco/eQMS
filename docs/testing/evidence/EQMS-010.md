---
title: EQMS-010 automated test evidence
status: observed-automated-results
related: [EQMS-010, EQMS-005, EQMS-101]
---

# EQMS-010 test evidence

## Tested revision and environment

- Tested revision: `96913b3ecb88d08d9d9a59030568253650c6472f`
- Test date: 2026-10-26
- Platform: macOS 26.6.2, Darwin 25.6.0, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed |
| `cargo test --workspace` | Passed: 8 CLI unit tests, 12 CLI integration tests, 1 search integration test, 47 core unit tests, 0 doc tests |
| `cargo clippy --workspace --all-targets -- -D warnings` | Passed |
| `cargo build --workspace --release` | Passed |
| `git diff --check` | Passed |

The automated coverage includes initialization at a path containing spaces,
JSON and human-readable resource output, repeat-init and existing-data
rejection, configured Git identity enforcement, an I/O failure without
overwriting a blocker, rollback of attempt-created files/directories while
preserving pre-existing Git configuration, retry after rollback, recovery after
a failing pre-commit hook, no SQLite index during init, and workspace
verification.

## Limitations and manual review — pending

- Automated execution was on macOS only. Non-Unix cleanup behavior has not been
  independently exercised.
- No manual review or manual tests have been performed. Review comments must
  still be checked by a human before merge.
- The initial `.git` repository and workspace root are intentionally preserved
  after rollback; cleanup failures or unsupported states may require manual
  repair.
- A commit error preserves workspace files because the commit outcome may be
  uncertain; the operator must run `qms verify` and, when no commit exists,
  correct the cause and use `qms recover`.
- Local Git history and configured author identity do not authenticate a
  signer, establish trusted time, or imply approval/compliance.
- These results are automated test evidence, not human approval or compliance
  evidence.

## Manual checklist — pending

- [ ] Human reviews workspace initialization design, risks, requirements,
  mappings, and this evidence.
- [ ] Human initializes and verifies a representative workspace.
- [ ] Human checks JSON and human-readable resource lists, retry, and recovery
  behavior.
- [ ] Reviewer records manual results before merge.
