---
title: EQMS-070 automated test evidence
status: observed-automated-results
related: [EQMS-070]
---

# EQMS-070 test evidence

## Tested revision and environment

- Tested revision: `dad0b4bece5de899e66c578a7fb29c2809fe6de5`
- Test date: 2026-11-12
- Platform: macOS 26.6.2, arm64
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`; Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`

## Commands and observed results

| Command | Result |
|---|---|
| `cargo fmt --all -- --check` | Passed |
| `cargo test --workspace` | Passed: 159 tests total, 0 failed (116 core unit tests including 10 in `plugin`) |
| `cargo clippy --workspace --all-targets -- -D warnings` | Passed |
| `git diff --check` | Passed |

## Coverage by test ID

| Test ID | Automated test (`eqms_core::plugin::tests`) |
|---|---|
| T070-01 | `api_version_is_required_and_must_be_supported` |
| T070-02 | `valid_manifest_with_all_contribution_kinds_parses`, `unknown_fields_and_oversized_input_are_rejected` |
| T070-03 | `identifiers_namespaces_and_references_are_validated` |
| T070-04 | `reserved_and_unknown_capabilities_reject_the_manifest` |
| T070-05 | `each_contribution_requires_its_capability` |
| T070-06 | `authorize_denies_core_controls_and_checks_capabilities` |
| T070-07 | `load_failures_are_isolated_and_reported`, `empty_load_is_empty_report` |
| T070-08 | Review: `plugin.rs` imports only `model`, serde and std |
| T070-09 | Full workspace run above |

## Limitations and manual review — pending

- Automated execution was on macOS only; no CI run recorded.
- Panic capture in `PluginRegistry::load` is not exercised by a test.
- Contract only: no plugin runtime, code execution, CLI command or call-site
  enforcement exists; `authorize` is not yet invoked by core flows.
- No manual review or manual tests have been performed.
