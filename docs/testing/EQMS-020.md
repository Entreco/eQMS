---
title: Controlled Markdown tests
status: draft
related: [EQMS-020]
---

# EQMS-020 tests

Requirements: [EQMS-020](../requirements/EQMS-020.md).

| Test ID | Requirements | Method and expected result |
|---|---|---|
| T020-01 | R020-01, R020-02, R020-03 | Fixed legacy-byte fixture with independently calculated hash; semantic/strict round trips; fixed metadata order; idempotence; differing content hashes. |
| T020-02 | R020-01, R020-02, R020-03 | Reordered/whitespace JSON and CRLF/CR input formats identically but fails strict validation. Empty/Unicode bodies, quoted titles, trailing spaces, embedded delimiters and final-newline variants preserve documented bytes. |
| T020-03 | R020-01, R020-03 | Reject missing/unknown/duplicate fields, malformed headers/JSON, future schemas, invalid/unsafe IDs, blank/control titles, non-Draft values, BOM and invalid UTF-8. |
| T020-04 | R020-04 | Run existing authoring/history/tamper/search/workspace regressions; add an old-code-created saved-history fixture and exact-byte/hash compatibility checks. Mismatched document ID remains rejected. |
| T020-05 | R020-05, R020-06 | CLI file/stdin outside workspace, paths with spaces, exact human formatter stdout, JSON content/hash equality, unchanged source files and no generated workspace; cover both final-newline variants. |
| T020-06 | R020-05, R020-06 | Missing file, directory, read failure, invalid UTF-8/metadata, noncanonical validation and usage failures produce correct exit/streams/envelopes without partial success; help never reads stdin. |
| T020-07 | R020-04, R020-06 | Rust CI checks and desktop/feature JVM adapter regressions; existing command output, conflicts, no-op saves and baseline isolation remain compatible. |
| T020-08 | R020-07 | Manual review of limits, schema examples and user workflow; no imported data, identity, approval or general audit verification is claimed. |

## Manual gates (pending)

- [ ] Human reviews risk/design, requirements, tests and automated evidence.
- [ ] Human checks canonical examples, exact formatter output and invalid-input errors.
- [ ] Human reopens a pre-change draft and inspects its history/comparison.
- [ ] Human confirms Draft and format-only trust wording before merge.

Automated evidence belongs in `evidence/EQMS-020.md`, with exact tested source
revision, environment, commands, observed outcomes and explicit limitations.
