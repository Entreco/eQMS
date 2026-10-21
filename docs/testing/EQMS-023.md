---
title: Document authoring test specification
status: draft
related: [EQMS-023]
---

# EQMS-023 test specification

Defined before implementation. Automated outcomes belong in the evidence record.

| ID | Requirements | Scenario |
|---|---|---|
| TEST-023-001 | 001, 004 | Canonical round trip, title/body normalization, two independent documents, saves, reopen/history and exact comparison. |
| TEST-023-002 | 002, 003 | Assert baseline HEAD/journal/index/staged/untracked files unchanged; stale and concurrent saves reject overwrites; unchanged save retains revision. |
| TEST-023-003 | 003, 005 | Inject failure before ref publication; complete candidate remains unreachable and baseline/draft refs unchanged; after publication reopen discovers complete state. |
| TEST-023-004 | 004, 005 | Reject malformed IDs/revision expressions, cross-document selectors, modified content/journal/ancestry and unsupported metadata. |
| TEST-023-005 | 005, 006 | Native CLI stdin and human/JSON commands; failure envelopes, missing identity, malformed protocol/launch errors, paths with spaces. |
| TEST-023-006 | 007, 008 | UI create/save/reopen/history/diff, busy state, failed-save edit retention, cancel and dirty document/project/close guards; desktop adapter argv/stdin and response validation. |
| TEST-023-007 | 008 | Existing Rust workspace/index and CLI tests, Kotlin project tests, shared JVM/Wasm compilation and release build. |
| TEST-023-008 | 005, 006, 009 | Direct CLI tests use in-memory readers for create/save, reject invalid UTF-8 and read errors without saving, prove read-only commands do not read stdin, and verify rendering/dispatch envelopes. Existing process-level CLI tests guard compatibility. |

Requirement references in the table use the suffix of `REQ-023-`.
Review regressions extend TEST-023-002/004/008: symbolic draft publication must
not advance its target branch; create/save accept separate hyphen-leading title
arguments; history validates head journal records once and compares ancestor
journals to verified prefixes, with changed-save candidate checks reusing the
verified parent. Instrument record-validation counts rather than wall-clock timing.
Use existing Cargo and Gradle runners. Do not report unexecuted platform/UI
coverage as passing; document environmental blockers and exclusions.

## Manual review and testing

- [ ] Review draft-chain scope, atomic-save behavior, trust limits and prerequisite boundaries.
- [ ] Create/open a real project; create two drafts and save/reopen each.
- [ ] Compare selected revisions and confirm Draft/configured-author/local-time wording.
- [ ] Confirm failed/stale saves preserve editor content.
- [ ] Confirm unsaved document/project switches and window close can be cancelled.
- [ ] Confirm no baseline change or publication/approval is implied.

Human checks remain unchecked until actually performed.
