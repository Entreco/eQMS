---
title: EQMS-094 desktop responsiveness
status: draft
related: [EQMS-092, EQMS-102]
---

# EQMS-094 desktop responsiveness

Issue: #65. Risk Management and Security Risk Management both apply.

## Scope and measured baseline

Keep the native `qms` CLI authoritative and Rust domain code independent of
Kotlin/UI frameworks. Desktop project opening continues to run `qms verify`.
Project document listing and History remain native-verified operations; do not
weaken journal, Git, revision, or provenance checks to reduce latency.

The initial source trace and pre-implementation profile showed three measurable
work paths:

1. Selecting a project awaits `qms verify` before composing `DocumentScreen`.
2. The screen's first refresh calls `document list`; the current Rust list path
   verifies the workspace and then asks Git for each ref head and verified
   revision chain.
3. Opening History sequentially called document-history and lifecycle-history.
   Both native requests verified the workspace and traversed the same captured
   draft chain independently. Both were also wrapped in the screen-wide busy
   operation.

The benchmark measured first feedback, first document-list content, and first
usable History content separately. Baseline values and subprocess counts are
recorded in [EQMS-094 tests](../testing/EQMS-094.md).

## Implemented design

Retain an explicit project verification gate. Once it succeeds, show the
workspace shell and its panels without waiting for document listing or search.
Use panel-local loading and errors, and keep mutations/dirty navigation guarded
without disabling unrelated reads or editor state.

The pre-implementation release profile measured 97 Git process starts for the
8-document list and 193 for the 16-document list. Each list previously
enumerated ref names, then started another Git process per document to resolve
its head. Capture ref names and object IDs together and verify each chain at
that captured object ID. `Documents::open` performs workspace structural
checks; every public document operation performs full workspace
verification under its operation lock, so remove only the redundant eager
verification before `list`/History operations. Keep all integrity checks and
failures in the locked operation.

For History, the versioned native `history-snapshot` operation returns document
revisions and lifecycle events from one lock-protected, verified history
snapshot. The baseline performed separate history and lifecycle-history
operations, each with 26 Git process starts on the 8-document fixture and 42
on the larger one.
Decode the complete envelope and validate all existing revision, event, hash,
and state invariants before updating the selected tab. Avoid a cache: every
History open remains freshly verified, so no cache freshness or invalidation
policy is introduced. A response may update only the same tab and source
revision that requested it; cancellation, disposal, tab/project changes, or a
revision change must not publish late results. Preserve current buffers and
editor selection.

Document-list and History loading do not set the workspace mutation-busy state.
List results are generation-checked against mutations and older refreshes;
History results are bound to the requesting tab and source revision. Search
preparation for a blank query has no user-visible result: it is skipped on
workspace open and when clearing the query; the first nonblank query prepares
search on its existing independent job. Existing save/transition refresh
behavior still re-prepares an active query.

Keep explicit errors and exact provenance. No Git library, persistent process,
or unbounded cache was introduced.

## Risk assessment

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Skipped/torn verification or misleading provenance | Keep authoritative `qms` verification; bind combined history to one locked snapshot and preserve strict decoding | Local verification does not prove identity or detect whole-history replacement |
| Late/wrong-project or wrong-tab results | Cancel scoped jobs and compare project/tab/source-revision identity before applying results | A concurrent external change after a response still requires refresh |
| Lost edits or changed caret/scroll during refresh | Keep buffers tab-owned; never replace dirty text; preserve existing save/conflict/close guards | External modifications can require explicit reconciliation |
| Read loading blocks mutations or hides errors | Scope read progress/errors to Project or History; retain mutation/modal guards | Long storage/Git operations can still take time |
| Cache presents stale data as verified | No History/list cache is introduced; every native read verifies current state | Repeated opens repeat fresh verification |
| Performance evidence overstates benefit | Versioned fixture, release build, cold/warm runs, exact revision/hardware/counts and raw samples | Synthetic fixtures and one platform do not guarantee user-machine latency |

Focused latency budgets and regression thresholds are recorded in
[EQMS-094 tests](../testing/EQMS-094.md) after the pre-implementation baseline.
Requirements and mappings are in [requirements](../requirements/EQMS-094.md).
Generated results are not approved evidence; manual visual/input/accessibility
review and testing remain required before merge.
