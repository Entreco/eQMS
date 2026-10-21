---
title: Fast grouped document search
status: draft
related: [EQMS-002, EQMS-023, EQMS-050, EQMS-051, EQMS-090, EQMS-101]
---

# EQMS-050/051: Project search

Rust owns search through a backend-independent `SearchEngine` contract and a
SQLite FTS5 implementation. SQLite is disposable, never document authority.
This first slice deliberately defers backend-selection configuration and
advanced metadata filters from EQMS-050. Tantivy, semantic search, attachments,
browser access and broad UI redesign are excluded.

Search includes the current saved head of every independent document draft ref.
The baseline-only `documents` index remains compatible. New transactional
migrations add verified saved revisions, normalized FTS fields, vocabulary and
source-ref metadata. Canonical parsing and history verification are reused from
EQMS-023. Preparation verifies changed histories before atomically replacing
their index entries, removes deleted refs and retains unchanged histories.
Historical revisions are indexed during preparation, not scanned per query.
The workspace lock serializes cooperating saves/preparation/query; compare the
complete draft-ref snapshot and baseline revision before serving results.
External changes invalidate pagination and require preparation. Cached revisions
are bound to immutable Git object IDs; the cache is not integrity evidence.
Local administrators replacing Git objects or the database remain outside the
trusted local-cache boundary. Verification/preparation failures remain errors.

## Matching and presentation

Normalize fields and literal input identically: Unicode lowercase, alphanumeric
tokens, `&` as `and`, no stop-word removal. Require all query tokens within one
field. Match exact tokens, then token prefixes, then one-edit typo matches for
query tokens of at least four characters (including adjacent transpositions).
Use an indexed vocabulary to retrieve typo candidates, not a document scan.
Never interpret input as SQL/FTS syntax. Stable ties use document ID and revision;
history ties prefer the newer saved revision. A document can match several groups.

Search current revisions across Documents/title, Authors/configured author and
Content/body. Only if all three are empty, retry historical revisions. Collapse
historical hits to the best revision per document in each group. Excerpts are
short plain text. Each group has five rows per page and independent Show more;
pages are bound to the same source snapshot.

Shared Compose `core:ui` owns the search box/results state and capability DTOs.
Desktop owns direct-argv native CLI calls on IO dispatchers. Debounce and
out-of-order response guards must fit the total latency budget. Blank input
restores the normal list; loading, history fallback, errors and no results differ.
Preparation is visible, cancellable at UI boundaries and refreshed after saves,
creates and explicit refresh. Current hits use existing dirty-navigation guards.
Historical hits open exact saved revisions read-only, labeled historical with
configured author/local timestamp claims, never editable replacements.
Unsaved text is excluded.

## CLI contract

`qms search <path> --prepare [--rebuild]` prepares/rebuilds saved-draft search data.
It returns `snapshot` and `indexed_revisions` under `data`, command
`search.prepare`. Rebuild does not delete canonical files or baseline index data.

`qms search <path> --query <text> [--snapshot <token>]` returns command `search`,
with `data` containing `snapshot`, `historical`, and `documents`, `authors`,
`content`. Each group has `hits` and nullable `next_offset`; each hit has `id`,
`title`, `revision`, `configured_author`, `local_timestamp`, and `excerpt`.
Optional `--documents-offset`, `--authors-offset`, `--content-offset` default to
zero. Nonzero offsets require a matching `--snapshot`; every group page has
at most five hits. A changed source/pagination snapshot is an explicit error.
JSON uses EQMS-101 schema 2, with existing nonzero errors. Human output is
readable. Missing/unprepared indexes instruct users to prepare, not return zero
matches. Corrupt databases report recovery instructions; no silent deletion.

## Risks and limits

Risk Management and Security Risk Management both apply proportionately.

| Hazard | Mitigation | Residual limit |
|---|---|---|
| Missing/stale quality records | Index saved draft refs, reconcile ref changes, snapshot-bound pages, distinguish errors from no results | External tools need not obey workspace locks |
| Historical content mistaken for current/approved | Global-only fallback, explicit historical/Draft labels and read-only opening | Configured author and local time are not authenticated |
| Edits lost during navigation | Reuse dirty-navigation guards; never save historical snapshots | Unsaved text is not searchable or crash recovery |
| Query/content injection | Literal tokenization, parameterized SQL, quoted FTS tokens, direct argv, plain-text excerpts | Native executable and local database remain trusted |
| Cache corruption or partial refresh | Transactional migration/reconciliation; explicit rebuild; retain prior complete index on failure | Cache integrity is not a substitute for authoritative verification |
| Slow matching freezes editing | Background preparation/query, indexed candidate lookup, stale-response guards, measured latency | Initial indexing/history costs are reported separately |

This is a draft design, not approval or compliance evidence.
