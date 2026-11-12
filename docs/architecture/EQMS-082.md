# EQMS-082: PDF module

Status: draft. Issue #35. Derived, print-oriented PDF output of saved document
revisions. Canonical Markdown, Git history, content hashes, lifecycle rules and
journals are never modified. PDF is a derived view, not authoritative content.

## Contract

Rust core `pdf.rs` (no CLI/UI dependency, no new crates) lays out the shared
EQMS-083 semantic tree (schema 3, `eqms-markdown-3`) and writes PDF 1.4 with the
built-in base-14 fonts (Helvetica family, Courier) in WinAnsi encoding. Output is
byte-deterministic for fixed content, page setup, lifecycle history and renderer
(`eqms-pdf-1`): no timestamps, random IDs or compression. Characters outside
WinAnsi are replaced with `?` and reported as a diagnostic; fonts are not embedded.

Native CLI (authoritative):

- `qms document export-pdf <project> <id> --revision <rev> --file <out.pdf>`
  exports one exact saved revision (revision defaults to the head revision).
- `qms document export-pdf-batch <project> <manifest.json> --output-dir <dir>`
  exports every manifest entry plus `pdf-export-index.json`.

Release manifest input (v1, until EQMS-042 defines immutable baselines):
`{"schema_version":1,"release":"<label>","documents":[{"id":"DOC-…","revision":"<rev>"}]}`;
at most 500 entries, 1 MiB; unknown fields, duplicate IDs and missing revisions
are rejected. All entries are rendered in memory first; nothing is written unless
all succeed. Existing files are never overwritten. Output must be outside the
project (canonical storage) and filenames are derived from validated IDs.
The index lists, per entry, ID, revision, content hash, file, page count,
PDF SHA-256, and records the manifest SHA-256. The index is not a signed baseline.

## Page content

Page size, orientation, margins, header and footer come from the document's
`PageSetup` (`{title}`, `{page}`, `{total}` placeholders). A second footer line
shows ID, revision and the first 16 hex digits of the content hash on every page.
Top-level `<!-- pagebreak -->` nodes start a new page; blocks paginate with
orphan/widow-avoiding line breaking, repeating headers/footers and total page count
(two-pass layout). Tables repeat their header row after a page break. To keep the
running header and two footer lines clear of content, effective top/bottom margins
are at least 30/36 pt even when the document's margins are smaller. The record
page is always page 1; the document body starts on page 2.
Images are never fetched or embedded; a placeholder with alt text and target is
printed. Links print as text with the target in parentheses; PDF link actions and
raw HTML are never emitted.

The first page starts with a **Controlled output record**: title, artifact ID,
revision, lifecycle status at that revision, content hash, template binding,
renderer versions, configured author and saved timestamp, followed by a
**Signature and approval summary** listing the lifecycle events up to the
exported revision (time, transition, revision, event hash). It states that no
cryptographic signature is recorded because the signature engine (EQMS-022) is not
implemented; lifecycle events and author are asserted metadata, not authenticated
approval. The PDF Info dictionary carries `EQMS-DocumentId`, `EQMS-Revision`,
`EQMS-ContentHash`, `EQMS-Renderer`, so the PDF is traceable to its source hash
and can be compared with `qms document show`.

Limits: body 1 MiB (render limits), 2,000 pages, 64 MiB PDF. Over-limit export
fails explicitly.

## Risk Management and Security Risk Management

Both apply. Hazards: a PDF presented as approval evidence that does not match the
saved revision (stale/wrong revision, unsaved text); omitted or garbled content;
misleading signature claims; overwriting or leaking files; malicious content
executing in a viewer; resource exhaustion. Mitigations: export only exact saved,
verified revisions (never buffers); embed ID/revision/hash visibly and in
metadata; explicit non-signature statement; text-only output without JavaScript,
actions, attachments, remote resources or fonts; replacement diagnostics for
unsupported characters; bounded sizes; no-overwrite, outside-project, validated
file names; atomic batch preflight; PDF SHA-256 reported for external recording.
Residual limitations: hashes and lifecycle data are not authenticated approval;
PDF/A, tagged accessibility, non-Latin scripts, images, hyperlinks and cryptographic
signing are not implemented; a viewer's own rendering is outside our control;
batch indices are not release baselines (EQMS-042) or signatures (EQMS-022).
Manual review of print fidelity remains required. Not compliance evidence.

On-screen rendering stays in #36; DOCX in #33.
