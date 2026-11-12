---
title: EQMS-114 automated test evidence
status: observed-automated-results
related: [EQMS-114]
---

# EQMS-114 test evidence

## Tested revision and environment

- Tested revision: `6336820503168a8710cc6bb7647a210e8c9f82c4` (stacked on the EQMS-113 branch, `960a3d4`). Tests ran on the working tree just before this commit; the only difference is the `docs/operations/BuildAndRun.md` usage note.
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`

## Commands and observed results

Run with `GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null` (no global git identity, as in CI).

| Command | Result |
|---|---|
| `cargo fmt --all --check` | Passed (`cargo fmt --all` applied before) |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed |
| `cargo test --workspace --locked` | Passed: 135 core unit tests (6 conformance, 1 document conformance), 17 CLI unit tests, all CLI integration suites including `template_conformance` |

Mapping: T114-01 (`conformance::tests`), T114-02 (`document::tests::conformance_reports_metadata_and_structure_findings_per_revision`), T114-03 (`apps/cli/tests/template_conformance.rs`), T114-04 (the commands above).

## Limitations

- No Kotlin code changed; JVM, native and Wasm suites were not run; the Kotlin Findings/Metadata panel is not implemented.
- Warnings only: no severity grading, waivers, block-type checks or lifecycle gating.
- Manual review/testing is not performed. Draft evidence, not approved compliance evidence.
