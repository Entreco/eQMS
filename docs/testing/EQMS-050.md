---
title: Search test specification
status: draft
related: [EQMS-050, EQMS-051]
---

# EQMS-050/051 test specification

Defined before implementation; record observed outcomes separately.

| ID | Requirements | Scenario |
|---|---|---|
| TEST-050-001 | 001,002 | `And` finds `And - Some Document`, `Research And Development`, `R & D`, author `Andi`, and body `This and That` in their own groups. Case/prefix/common words, multiword and literal punctuation cases. |
| TEST-050-002 | 003 | All four one-edit operations, short-token exclusion, exact/prefix/fuzzy ordering, deterministic ties and Unicode normalization. |
| TEST-050-003 | 004 | No global current hits triggers history; one current hit in any group suppresses all history. Changed historical author/title/body, best-revision collapse, exact revision read-only opening. |
| TEST-050-004 | 005 | Five-row pages, independent offsets, snapshot invalidation, blank/no matches, delayed old responses, project changes and unsaved exclusion. |
| TEST-050-005 | 006,010 | CLI human/schema-2 JSON, usage/operation failures, argv boundaries, invalid protocol, launch errors, FTS metacharacters and safe snippets. |
| TEST-050-006 | 007,010 | Migration repeat/upgrade/rollback/future versions, incremental save/ref deletion, rebuild, malformed history, missing/corrupt/stale index and preservation of baseline/index/staged/Git/journal state. |
| TEST-050-007 | 008 | UI dirty guards, failed-edit retention, current navigation, historical read-only/return-latest, existing document/project regression tests and shared JVM/Wasm compilation. |
| TEST-050-008 | 009 | Deterministic 10,000-document/~100 MiB fixture; p50/p95 end-to-end warm latency including debounce/native/decoding/render; separate initial indexing and history-fallback measurements; hardware/build/run-count/revision recorded. |

Requirement suffixes refer to REQ-050. Use existing Cargo/Gradle runners.
Missing UI/platform/performance coverage is a limitation, never a pass.

## Manual review/testing

- [ ] Review ranking, typo noise, fallback clarity and index trust boundary.
- [ ] Search a real project; inspect all three sections and Show more.
- [ ] Confirm historical results are obvious, read-only and navigate safely.
- [ ] Confirm dirty edits survive errors and cancelled navigation.
- [ ] Confirm measured responsiveness on representative hardware.

Human checks stay unchecked until actually executed.
