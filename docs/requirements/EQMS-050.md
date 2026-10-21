---
title: Project search requirements
status: draft
related: [EQMS-050, EQMS-051]
---

# EQMS-050/051 requirements

| ID | Requirement |
|---|---|
| REQ-050-001 | One open-project search box groups latest saved draft matches by title, configured author and body; unsaved text is excluded. |
| REQ-050-002 | Literal Unicode case-insensitive tokens support prefixes and &/and equivalence, including common words; all query tokens must match within a field. |
| REQ-050-003 | Tokens of at least four characters support one insertion, deletion, substitution or adjacent transposition; shorter tokens do not use typo expansion. Exact matches outrank prefixes, which outrank typos, with deterministic ties. |
| REQ-050-004 | Only zero matches across all current groups trigger older-revision fallback. Historical results are deduplicated per document/group, labeled, and open exact saved revisions read-only. |
| REQ-050-005 | Each section displays five hits per page with independent Show more and snapshot-bound pagination. Blank input restores the list; obsolete responses cannot replace current query/project state. |
| REQ-050-006 | Rust search is backend-independent, exposed by versioned native CLI human/JSON contracts. Desktop process/protocol failures and search/index errors are visible. |
| REQ-050-007 | Preparation incrementally indexes verified draft histories using transactional migrations; detects source changes/deletions; supports explicit rebuild and preserves canonical/Git/baseline-index data. |
| REQ-050-008 | Existing create/save/history/dirty-navigation behavior remains intact. Historical snapshots cannot overwrite latest content. |
| REQ-050-009 | Warm final-keystroke-to-render p95 is at most 150 ms for a documented 10,000-document/~100 MiB fixture, including debounce, native invocation, decoding and rendering. Initial preparation and history fallback are measured separately. |
| REQ-050-010 | Queries are literal, SQL is parameterized, excerpts render as safe text, index/source errors never masquerade as no matches, and author/time remain configured/local claims. |

Backend configuration, complex filters, attachments, semantic search and browser
adapters are excluded from this slice rather than implicitly completed.
