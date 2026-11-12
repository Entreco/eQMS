# EQMS-082 automated evidence

Issue: [#35](https://github.com/Entreco/eQMS/issues/35).
Status: observed automated results, pending manual review/testing; not approved
compliance evidence. The issue remains open and In progress.

## Revision and environment

- Tested source: `984570cf42c7c24e450b2019604eb21fb245f7ff` (clean worktree).
- Branch: `feature/eqms-082-pdf-module`, based on main `599e232`.
- Run completed: `2026-11-12T21:10:13Z`.
- Host: macOS 26.6.2, arm64; Rust/Cargo 1.99.1; locked dependencies, no new crates.

This evidence is committed separately after the tested source; the evidence-only
commit does not change application or test behavior.

## Reproduction

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked --quiet -- -D warnings
cargo test --workspace --locked
```

## Results

fmt and Clippy (`-D warnings`): passed. Workspace tests: 194 passed, 0 failed.
New: 8 core tests in `core/rust/src/pdf.rs` and 5 CLI tests in `apps/cli/tests/pdf.rs`.

| Test | Result | Covered by |
|---|---|---|
| T082-01 | passed | `t082_01_structure_is_valid_and_deterministic`, `t082_01_04_export_is_exact_deterministic_traceable_and_read_only` |
| T082-02 | passed | `t082_02_paginates_with_header_footer_and_page_breaks`, `t082_02_page_setup_controls_media_box_and_tables_repeat_headers`, `long_words_code_lists_and_quotes_stay_inside_margins` |
| T082-03 | passed | `t082_03_identity_hash_and_signature_summary`, `t082_03_lifecycle_events_appear_in_summary` |
| T082-04 | passed | `t082_04_metadata_binds_source_hash`, CLI test above (PDF SHA-256 equals file digest) |
| T082-05 | passed | `t082_05_batch_export_for_release_manifest`, `t082_05_batch_rejects_invalid_manifests_and_writes_nothing` |
| T082-06 | passed | `t082_06_no_active_content_and_text_is_escaped` |
| T082-07 | passed | `t082_07_page_limit_is_enforced`, `t082_07_output_paths_are_safe` |
| T082-08 | passed | commands above; this record |

Developer-only visual check (not retained): a synthetic multi-page document
rasterized with macOS QuickLook and PyMuPDF showed the expected record page,
header/footer, tables, lists, quote, code and footnote layout.

## Limitations and exclusions

- Kotlin/JVM/Wasm suites were not run: no Kotlin source changed and no UI surface
  was added for PDF export.
- No external PDF conformance validator (e.g. veraPDF, qpdf) was run; structure
  is checked by tests (header, xref offsets, trailer) and a PyMuPDF open.
- Text-only output: no images, hyperlinks, embedded fonts, PDF/A, tagging or
  non-WinAnsi scripts; the 64 MiB, 2,000-page and 500-entry limits are tested
  only for the page limit and entry count.
- Signature summary reflects lifecycle events only; EQMS-022 and EQMS-042 are not
  implemented, so no cryptographic signature or immutable baseline exists.
- Manual print/viewer fidelity review and manual testing remain unchecked.
