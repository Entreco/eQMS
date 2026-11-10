---
title: EQMS-014 automated test evidence
status: observed-automated-results
related: [EQMS-014, EQMS-010, EQMS-101]
---

# EQMS-014 test evidence

## Tested revision and environment

- Tested revision: `0c13fbbf4fed5f6cdd2ca73e834c10fb70337bce`
- Test date: 2026-11-10
- Platform: macOS 26.6.2, Darwin 25.6.0, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`; Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`
- Git: `git version 2.54.0 (Apple Git-157)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed |
| `cargo test --workspace` | Passed: 15 CLI unit, 5 audit, 12 cli, 3 markdown, 2 module, 1 search integration tests; 89 core unit tests; 0 doc tests |
| `cargo clippy --workspace --all-targets -- -D warnings` | Passed |
| `cargo build --workspace --release` | Passed |
| `git diff --check` | Passed |

## Coverage by test ID

| Test ID | Automated test |
|---|---|
| T014-01 | `module::tests::invalid_manifests_are_rejected`, `bundled_modules_are_valid_and_deterministic` |
| T014-02, T014-08 | `module::tests::install_creates_draft_artifacts_with_provenance_and_verifies` |
| T014-03 | `module::tests::install_is_deterministic_across_workspaces` |
| T014-04 | `module::tests::repeat_install_is_unchanged_and_writes_nothing` |
| T014-05 | `module::tests::interrupted_install_is_completed_by_retry` |
| T014-06 | `module::tests::upgrade_preserves_edits_and_reports_conflicts` |
| T014-07 | `module::tests::unsafe_installs_fail_without_changes`, `tests/module.rs::unknown_module_is_a_nonzero_error` |
| T014-09, T014-10 | `tests/module.rs::module_list_and_add_report_drafts_and_preserve_on_repeat` |
| T014-11 | Existing workspace, document, audit, search and CLI tests in the full run |

## Limitations and manual review — pending

- Automated execution was on macOS only.
- Only the bundled `qms-starter` module exists; it is generic scaffolding, not
  standards-derived, not an implemented process and not compliance evidence.
- Concurrent installs and failure injection during the manifest/journal update
  were not exercised beyond the existing `record_event` recovery tests.
- No manual review or manual tests have been performed.
- Local Git history and journal do not authenticate signers, establish trusted
  time, or imply approval.

## Manual checklist — pending

- [ ] Human reviews the design, risks, requirements, mappings and this evidence.
- [ ] Human runs `qms module add qms-starter` in a representative workspace.
- [ ] Human edits a draft, repeats the command, and confirms preservation.
- [ ] Reviewer records manual results before merge.
