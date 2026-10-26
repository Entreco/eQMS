---
title: Ledger verification test mappings
status: draft
related: [EQMS-031]
---

# EQMS-031 tests

Requirements: [EQMS-031](../requirements/EQMS-031.md).

| ID | Mapping | Expected automated behavior |
|---|---|---|
| T031-01 | R031-01, R031-02, R031-04 | Genesis, recorded content changes and independent drafts verify; report exact counts; canonical files/refs/index remain unchanged. |
| T031-02 | R031-01, R031-05 | Changed/reordered/missing/duplicate/incomplete records and self-consistent historical rewrites/truncation fail with commit/record context. |
| T031-03 | R031-01, R031-05 | Historical wrong/missing/non-regular referenced content fails even when latest content/journal are valid. |
| T031-04 | R031-02, R031-05 | Corrupt draft history and drafts from unrelated baselines fail with document/head/revision context; existing draft tamper tests remain passing. |
| T031-05 | R031-03, R031-04 | Valid genesis/intermediate/HEAD checkpoints work; malformed, unrelated, unknown or draft-only checkpoints fail; unsupported merges/shallow/replacement/grafts fail explicitly. |
| T031-06 | R031-05, R031-06 | CLI human/JSON success/error/usage/help contracts; stdin is not read; current workspace verify/recover/authoring/search regressions remain passing. |

Run focused audit/CLI tests, then Rust CI: formatting, Clippy with denied
warnings, locked workspace tests and release build; run affected desktop/feature
JVM adapter regressions. Evidence records the exact committed source revision
and actual results/exclusions.

## Manual checks (pending)

- [ ] Review design/risk, requirements, code, tests and evidence.
- [ ] Verify a workspace with multiple drafts, with/without independent checkpoint.
- [ ] Inspect broken-segment diagnostics and confirm verification does not repair.
- [ ] Confirm trust labels do not imply identity, approval or complete deleted-ref detection.
