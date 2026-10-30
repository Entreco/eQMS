---
title: EQMS-094 desktop responsiveness
status: draft
related: [EQMS-092, EQMS-102]
---

# EQMS-094 desktop responsiveness

Issue: #65. Risk Management and Security Risk Management both apply.

## Scope and current path

Keep the native `qms` CLI authoritative and Rust domain code independent of
Kotlin/UI frameworks. Desktop project opening continues to run `qms verify`.
Project document listing and History remain native-verified operations; do not
weaken journal, Git, revision, or provenance checks to reduce latency.

The initial source trace shows three measurable work paths:

1. Selecting a project awaits `qms verify` before composing `DocumentScreen`.
2. The screen's first refresh calls `document list`; the current Rust list path
   verifies the workspace and then asks Git for each ref head and verified
   revision chain.
3. Opening History sequentially calls document-history and lifecycle-history.
   Both native requests verify the workspace and traverse the same captured
   draft chain independently. Both are also wrapped in the screen-wide busy
   operation.

These are investigation leads. Record baseline measurements and subprocess
counts before choosing an optimization. First feedback, first document-list
content, and first usable History content are separate outcomes.

## Design

Retain an explicit project verification gate. Once it succeeds, show the
workspace shell and its panels without waiting for document listing or search.
Use panel-local loading and errors, and keep mutations/dirty navigation guarded
without disabling unrelated reads or editor state.

For History, expose a versioned native operation returning document revisions
and lifecycle events from one lock-protected, verified history snapshot. Decode
the complete envelope and validate all existing revision, event, hash, and
state invariants before updating the selected tab. Avoid a cache: every History
open remains freshly verified, so no cache freshness or invalidation policy is
introduced. A response may update only the same tab and source revision that
requested it; cancellation, disposal, tab/project changes, or a revision change
must not publish late results. Preserve current buffers and editor selection.

Use the measured list profile to select a focused native optimization. Any
batching must reuse an already captured ref/snapshot only within the existing
workspace lock and verification boundary. Keep explicit errors and exact
provenance. Do not introduce a Git library, persistent process, or unbounded
cache unless the observed profile demonstrates the need and the design/risk
trade-offs are reviewed first.

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
