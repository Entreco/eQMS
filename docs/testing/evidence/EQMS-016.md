# EQMS-016 automated test evidence

## Tested revision and environment

- Source revision: `0c4d47c876e3146edb3f513cc26dd1483d810910`
- Environment: macOS arm64 (Darwin); `rustc 1.99.1 (58cf8f9c3 2026-10-13)`;
  `cargo 1.99.1 (5f94df478 2026-08-27)`; `git version 2.54.0 (Apple Git-157)`
- The evidence record is committed separately from the tested source revision.

## Commands and outcomes

| Command | Outcome |
|---|---|
| `cargo fmt --all -- --check` | Passed. |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | Passed with warnings denied. |
| `cargo test --workspace --locked` | Passed: 123 tests (CLI unit 15, audit 5, cli 12, import 2, markdown 3, search 1, core 85); 0 failed. |
| `cargo build --workspace --release --locked` | Passed. |
| `scripts/dogfood-check.sh` | Passed: 65 repository Markdown files imported as Draft, idempotent re-import, all canonical draft files validated, `qms verify` passed, `audit verify-ledger --checkpoint <genesis>` reported `checkpoint_ancestry` for 65 drafts. |
| `scripts/dogfood-check.sh` with an added invalid-UTF-8 source (untracked, removed afterwards) | Failed as expected: exit 1, `document.import` reported the file is not valid UTF-8; no success output. |
| `git diff --check` | Passed. |

## Test mapping

| Test | Result |
|---|---|
| T016-01 | `import::tests::imports_lossless_draft_with_identifiers_and_source_hash`, `identifiers_are_stable_sorted_and_unique`: passed. |
| T016-02 | `import::tests::titles_come_from_front_matter_heading_or_file_stem`, no-lifecycle-event assertion: passed. |
| T016-03 | `import::tests::invalid_batch_creates_nothing_and_reimport_is_idempotent`: passed. |
| T016-04 | `apps/cli/tests/import.rs` (2 tests); existing document/audit/search CLI suites: passed. |
| T016-05 | Dogfood script positive and negative runs above. |
| T016-06 | Workflow commands in `docs/operations/EQMS-016.md` were run manually in a temporary workspace (import, save, history, diff, verify-ledger with checkpoint) and by the script; documentation review is a manual check. |

## Limitations

These are automated results on macOS arm64 only; the GitHub Actions run of the
new CI step is not yet observed. No human review or manual acceptance testing is
claimed. The Kotlin desktop/web shells were not rebuilt or tested because this
change touches only Rust core/CLI, a shell script, CI and documentation. The
dogfood check uses an ephemeral workspace and self-declared genesis checkpoint:
it does not provide a persistent audit trail, authenticated identity, trusted
time, reviewer approval or compliance assurance. Windows/Linux runtimes and
performance were not tested (the script takes roughly 35 s locally).

## Re-verification after merging main (EQMS-013)

- Source revision: `c1475acdd27b299dbf2d4426796fa18c804b3e7a` (merge of `origin/main` 72606a7 into the branch; the only conflict was the `docs/README.md` index, resolved by keeping both lists).
- Same environment and commands as above: fmt, clippy (warnings denied), `cargo test --workspace --locked` (128 tests passed, 0 failed), release build and `scripts/dogfood-check.sh` all passed. The script now imports 68 documents (EQMS-013 records included) with `checkpoint_ancestry` verified.
