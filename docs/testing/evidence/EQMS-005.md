---
title: EQMS-005 automated test evidence
status: observed-automated-results
related: [EQMS-005, EQMS-010, EQMS-101]
---

# EQMS-005 test evidence

## Tested revision and environment

- Tested revision: `e1cbbf82d4bbc2db37a37d0d937bf51d199a3c6c`
- Test date: 2026-10-26
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed |
| `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 cargo test --workspace --locked` | Passed: 46 core unit tests, 8 CLI unit tests, 10 CLI integration tests, 1 search integration test, 0 doc tests |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo build --workspace --release --locked` | Passed |
| `git diff --check` | Passed |

The suite exercised interrupted-initialization recovery through the CLI,
rejection of invalid partial initialization, concurrent initializers, configured
Git identity enforcement, symlinked workspace paths, a nested non-root
workspace, recovery without destructive truncation, valid pending manifest
recovery, Git-filter mismatch rejection, literal Git pathspecs, and workspace
verification. The reported test suites contained 65 unit/integration tests in
total.

## Limitations and manual review — pending

- Automated execution was on macOS only. Descriptor-relative no-follow access
  was exercised on Unix; non-Unix parent-path race resistance was not verified.
- No manual review or manual tests have been performed. Review comments must
  still be checked by a human before merge.
- Local hashes, Git history, and configured author identity do not authenticate
  a signer, establish trusted time, or prevent replacement of all local history.
- These results are automated test evidence, not human approval or compliance
  evidence.

## Manual checklist — pending

- [ ] Human reviews workspace design, risk assessment, requirements, mappings,
  and this evidence.
- [ ] Human initializes and verifies a workspace with the documented CLI.
- [ ] Human checks recovery, identity configuration, and error reporting.
- [ ] Reviewer records the manual outcome before merge.
