---
title: EQMS-094 desktop responsiveness tests
status: draft
related: [EQMS-094, EQMS-092, EQMS-102]
---

# EQMS-094 test specification

Issue: #65. Define mappings before feature implementation; preserve existing
test IDs.

| Test ID | Requirement mapping | Planned check |
|---|---|---|
| T094-01 | R094-01, R094-02 | Release-build, versioned synthetic fixtures cover eight small documents plus increasing document and revision counts. Record cold and warm project selection to first visible list item, History toggle to usable revision/event content, native process/Git counts, stage timings, samples, p50/p95, hardware/platform, binary hash, and exact source revision. |
| T094-02 | R094-03, R094-04 | Native Rust and CLI tests verify a combined history response contains exact revision and lifecycle data from one validated history; corrupt journal/ref/history and malformed output fail explicitly. Existing integrity and audit checks remain enforced. |
| T094-03 | R094-04, R094-06 | Desktop adapter tests assert one combined history command, strict envelope/payload/id/revision/hash/state validation, cancellation propagation, and no success-shaped fallback. |
| T094-04 | R094-05, R094-06, R094-07 | Compose tests assert shell/panels render before delayed list/history completion; loading/error feedback is panel-local; late responses cannot overwrite dirty title/body or publish History for an older revision. |
| T094-05 | R094-06, R094-07 | State tests cover tab/project disposal during reads, concurrent mutation versus list refresh, revision changes during History reads, failed refresh, and preserved edits. No cache is expected. |
| T094-06 | R094-03, R094-07, R094-08 | Run affected Rust unit/integration, CLI, desktop adapter/JVM, feature JVM, and Wasm checks; include corrupted history, external changes, errors, cancellation, dirty edits, and repeated panel opens. Record unavailable platform/manual cases as exclusions, not passes. |

## Baseline and budgets

The baseline must be recorded before changing production behavior. Set explicit
p95 budgets and regression thresholds from its observed values here before
implementing optimizations. Do not treat the reported ~2s project-open and ~1s
History observations as measurements or acceptance evidence.

| Dataset / outcome | Cold/warm | Baseline p95 | Target p95 | Regression threshold |
|---|---|---:|---:|---:|
| 8 documents; 128-byte bodies; one document has two revisions; project selection to first list content | 30 repeated runs after fixture preparation; OS cache uncontrolled | 1,133.42 ms | <=1,050 ms | <=1,246.77 ms (baseline +10%) |
| Same dataset; History toggle through revisions and lifecycle completion | Same | 845.10 ms | <=700 ms | <=929.61 ms (baseline +10%) |
| 16 documents; 128-byte bodies; first document has four revisions; project/list | Same | 2,126.68 ms | <=1,900 ms | <=2,339.34 ms (baseline +10%) |
| Same larger dataset; History toggle through revisions and lifecycle completion | Same | 1,304.89 ms | <=1,100 ms | <=1,435.38 ms (baseline +10%) |

Baseline values use a release qms on macOS 26.6.2 arm64 / Apple M5 Pro, JBR
25.0.3, source `5b6b134d68437789f27dc0e1577df094f549f364`, and 30 Compose
samples. JSON decoding and composition are included in UI readiness; standalone
Git trace runs are used only for subprocess counts because tracing perturbs
latency. The first measured pass followed fixture verification, so cold OS-cache
latency is uncontrolled and is not claimed. A regression threshold is a
guardrail, not an acceptable substitute for meeting the target budget.

Full commands and observed results: [EQMS-094 evidence](evidence/EQMS-094.md).

## Manual checklist — pending

- [ ] Human review of loading/error feedback and workspace responsiveness.
- [ ] Human exercise of project switching, dirty buffers, caret/scroll and History.
- [ ] Human verification of keyboard focus/accessibility and target-platform behavior.
- [ ] Human review of automated evidence and any recorded exclusions.
