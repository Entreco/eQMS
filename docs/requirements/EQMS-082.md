# EQMS-082 requirements

Status: draft; issue #35; design/risk: ../architecture/EQMS-082.md.

| ID | Requirement |
|---|---|
| R082-01 | Export an exact saved revision as deterministic PDF from the shared semantic tree without changing canonical records; never export unsaved buffers. |
| R082-02 | Paginate with the document's page size/orientation/margins, repeating header/footer placeholders, page numbers/total, explicit page breaks and repeating table headers. |
| R082-03 | Include artifact ID, revision, status, content hash and a signature/approval summary of lifecycle events that does not claim cryptographic signatures. |
| R082-04 | Make output traceable to the source hash via visible text and PDF metadata; report PDF SHA-256. |
| R082-05 | Support batch export from a release manifest v1 with strict validation, all-or-nothing preflight and an index with manifest/PDF digests. |
| R082-06 | Emit no active content (scripts, actions, links, attachments, remote loads, embedded fonts/images); replace unsupported characters visibly with diagnostics. |
| R082-07 | Enforce limits and safe output: outside project, no overwrite, validated names, page/size bounds, explicit errors. |
| R082-08 | Record exact-revision automated evidence; manual print review remains pending; do not imply implemented signing, baselines or PDF/A. |
