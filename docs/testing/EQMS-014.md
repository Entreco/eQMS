---
title: EQMS-014 test specification
status: draft
related: [EQMS-014]
---

# EQMS-014 tests

Requirements are in [EQMS-014 requirements](../requirements/EQMS-014.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T014-01 | R014-01, R014-02 | Core unit tests: bundled manifest parses; invalid id/version/duplicate key/missing template/unknown validation artifact/missing heading are rejected before any write. |
| T014-02 | R014-03, R014-04 | Install into a fresh workspace; assert Draft documents with deterministic IDs, provenance in `document.created` payloads, `module.installed` event, manifest entry; workspace and ledger verification pass. |
| T014-03 | R014-03 | Install into two workspaces; assert identical document IDs and bodies. |
| T014-04 | R014-05 | Repeat install; assert `unchanged` and no new journal records. |
| T014-05 | R014-05 | Pre-create a subset of artifacts (simulated interruption); install; assert `completed`, existing drafts untouched. |
| T014-06 | R014-06, R014-08 | Edit an artifact, upgrade with changed template and a new artifact; assert edit preserved, conflict reported, new draft created. |
| T014-07 | R014-07 | Downgrade, major upgrade, missing/too-old dependency, unknown module; assert errors and unchanged HEAD. |
| T014-08 | R014-08 | Every created artifact is Draft with one saved revision and no lifecycle events. |
| T014-09 | R014-09, R014-10 | CLI: `module list` / `module add` in human and JSON modes; unknown module is a nonzero error. |
| T014-10 | R014-04 | CLI: `qms verify` and `qms audit verify-ledger` succeed after `module add`; `document list` shows drafts. |
| T014-11 | all | Regression: `cargo test --workspace`, clippy, fmt. |

## Manual checklist — pending

- [ ] Human reviews design, risk assessment, requirements and mappings.
- [ ] Human runs `qms module add qms-starter` in a representative workspace and inspects drafts and journal.
- [ ] Human edits a draft, repeats the command, and confirms the edit is preserved.
- [ ] Human confirms scaffold wording does not imply compliance.
- [ ] Reviewer records manual results before merge.
