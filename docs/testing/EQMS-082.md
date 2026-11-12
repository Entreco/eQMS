# EQMS-082 test specification

Status: draft. Defined before implementation; not evidence.

| Test | Requirements | Checks |
|---|---|---|
| T082-01 | R082-01 | Core: valid PDF structure (header, xref offsets, trailer); identical bytes on repeated export; canonical files/head unchanged after CLI export; unknown revision/ID fails. |
| T082-02 | R082-02 | Core: long document spans several pages; `Page n of N` correct; header/footer expanded; page break starts a new page; no leading/blank page; landscape/Letter media boxes; table header repeats. |
| T082-03 | R082-03 | Core/CLI: ID, revision, status, hash and lifecycle events appear; non-signature statement present; only events up to the exported revision listed. |
| T082-04 | R082-04 | PDF Info keys equal saved revision identity; reported PDF SHA-256 matches file bytes; a changed body changes hash and PDF. |
| T082-05 | R082-05 | CLI batch: success with index and digests; invalid schema, duplicate IDs, missing revision, unknown field, >500 entries, one failing entry writes nothing. |
| T082-06 | R082-06 | Core: no `/JS`, `/Launch`, `/URI`, `/EmbeddedFile`, `/OpenAction`, `/Annots`; escaping of `( ) \`; non-WinAnsi replaced with diagnostic; raw HTML literal. |
| T082-07 | R082-07 | CLI: output inside project, existing file, bad file name rejected; page and size limits enforced. |
| T082-08 | R082-08 | `cargo fmt/clippy/test` workspace; revision-bound evidence file; manual print review unchecked. |

Implementation mapping: `core/rust/src/pdf.rs` unit tests and
`apps/cli/tests/pdf.rs` integration tests. Text extraction in tests inspects the
uncompressed content streams; no external PDF viewer is used, so viewer rendering
fidelity is a manual check.
