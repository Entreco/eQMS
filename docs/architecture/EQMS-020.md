---
title: Controlled Markdown v1
status: draft
related: [EQMS-003, EQMS-020, EQMS-023, EQMS-016]
---

# EQMS-020: Controlled Markdown v1

## Contract and boundary

`eqms_core::markdown` owns parsing, canonical encoding, strict validation and
hashing without Git, filesystem, CLI or UI dependencies. Authoring and saved
history verification reuse it. This extracts the existing EQMS-023 format;
existing saved bytes, IDs, hashes and journal bindings do not change.

The complete UTF-8 encoding is:

```text
---
{"schema_version":1,"id":"DOC-0123456789abcdef0123456789abcdef","title":"Policy","status":"Draft"}
---
Markdown body
```

The opening delimiter must start at byte zero (no BOM). The header ends at
the first LF-delimited `---` closing line, which must have a following newline.
The metadata is one JSON object (valid YAML flow syntax), not general YAML.
Semantic parsing accepts JSON whitespace/key order and CRLF/CR line endings.
It rejects missing, unknown and duplicate fields, unsupported versions, invalid
UTF-8, malformed delimiters/JSON, and unsupported values.

All four fields are required. `schema_version` is integer 1; `id` is `DOC-`
followed by 32 lowercase hexadecimal digits; `title` is trimmed, nonblank and
contains no control characters after trimming; `status` is exactly `Draft`.
These are the existing title rules, not a new restriction on saved documents.
Canonical encoding emits exactly the field order shown, compact JSON, LF
delimiters and LF body line endings. Other body bytes/whitespace are retained,
including final newline presence. Empty bodies are valid. Markdown syntax is
not interpreted or reformatted. Body front-matter-like text is content.

Strict validation parses and re-encodes, then requires exact input-byte equality.
Saved-history decoding always uses this strict path and separately checks the
expected document ID. Formatting uses semantic parsing, not strict validation.
No relaxed parser is introduced into history verification.

SHA-256 covers the entire canonical document including delimiters and metadata,
and is represented as `sha256:<64 lowercase hex digits>`. The public codec
returns hashes for its canonical output; validation returns one only after
exact-byte validation. A hash is not proof of identity, approval or trusted
history.

EQMS-003's UUID-based general artifact model is a separate contract. This v1
document codec neither changes that model nor invents a UUID/type mapping.
Richer metadata, general artifact persistence and migrations are follow-ups.

## Native CLI

| Command | Behavior |
|---|---|
| `qms document validate <file\|->` | Strict canonical validation; reports ID, schema, `canonical: true`, content hash |
| `qms document format <file\|->` | Semantic parsing and exact canonical output |

`-` reads injected UTF-8 stdin; other arguments are file paths. No workspace,
Git identity or index is required. Files are read only. Commands compute their
complete results before output. They never modify files, Git refs or journals.
Callers must not redirect output onto their input file; use a separate file.
These commands accept supported v1 documents, not arbitrary existing project
Markdown. Import belongs to EQMS-016.

Both commands use EQMS-101's schema-version-2 JSON envelope. Validation's `data`
is, for example:

```json
{"id":"DOC-0123456789abcdef0123456789abcdef","schema_version":1,"canonical":true,"content_hash":"sha256:<64 lowercase hex digits>"}
```

JSON formatting adds `content` with the exact canonical text. Human formatting
writes that text only, with no extra newline; other existing CLI output keeps
its current newline behavior. Human validation labels its result as canonical
Markdown format validation, not audit verification.

Usage errors exit 2; malformed/noncanonical input and I/O failures exit 1 with
the existing `OPERATION_FAILED` envelope on stderr when JSON is requested.
Noncanonical validation errors point to `qms document format`. No partial
canonical result is written on input failure. Stdout write errors remain
explicit failures under the existing process-I/O contract.

## Risk Management and Security Risk Management

Both apply: encoding affects quality-record hashes; local content is untrusted.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Format drift invalidates existing history/hashes | Shared codec; fixed legacy-byte/hash fixtures; strict history decoding and regressions | No schema migration or general artifact mapping |
| Formatter silently changes Markdown or overwrites input | Normalize only specified fields/line endings; preserve other body bytes; read-only inputs; exact stdout tests | Caller-controlled shell redirection can overwrite files |
| Ambiguous metadata accepted as valid | Reject duplicates/unknown/missing fields, unsupported versions/statuses, invalid IDs/titles and malformed UTF-8 | No general YAML, plugin metadata validation or reference checking |
| Hash/format result implies trusted history or approval | Explicit format-only semantics and Draft status | No signature, identity, trusted-time, publication or compliance assurance |

No general ledger verification is added; EQMS-031 remains separate. Existing
workspace/draft verification remains unchanged. This design and its related
requirements/tests/evidence are drafts pending human review/testing.
