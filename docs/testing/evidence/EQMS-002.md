---
title: EQMS-002 automated test evidence
status: observed-automated-results
related: [EQMS-002, EQMS-005]
---

# EQMS-002 test evidence

## Tested revision and environment

- Tested revision: `0b7427510168d981ced8a5612fe2e9b34129e920`
- Test date: 2026-10-20
- Platform: macOS 26.6.2
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed |
| `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 cargo test --workspace --locked` | Passed: 25 library unit tests, 9 CLI integration tests, 0 doc tests |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo build --workspace --release --locked` | Passed |
| `git diff --check` | Passed |

The test run covered transactional/repeatable migrations, migration rollback,
future-schema rejection, nested and literal-backslash filenames on Unix,
symlink exclusion, failed-rebuild preservation, and unchanged Git/journal
history during index rebuild.

## Limitations and manual review — pending

- Automated execution was on macOS only; Linux and Windows behavior was not
  exercised.
- The index remains disposable query state; rebuildability does not provide
  search ranking or historical snapshots.
- Manual reviewer verification and manual rebuild/failure checks have not been
  performed.
- These results are automated test evidence, not human approval or compliance
  evidence.

## Manual checklist — pending

- [ ] Human reviews design, risks, requirements, mappings, and this evidence.
- [ ] Human rebuilds an index from a representative workspace.
- [ ] Human confirms failure behavior and unchanged canonical history.
- [ ] Reviewer records the manual outcome before merge.
