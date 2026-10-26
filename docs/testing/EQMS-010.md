---
title: EQMS-010 test specification
status: draft
related: [EQMS-010]
---

# EQMS-010 tests

Requirements are in [EQMS-010 requirements](../requirements/EQMS-010.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T010-01 | R010-01, R010-03, R010-04, R010-05 | Initialize at a path containing spaces; assert config, genesis journal, manifest, initial commit, canonical `data.path`, `data.created_resources`, and human resource output. |
| T010-02 | R010-01 | Reopen and verify the initialized workspace; assert the genesis event and initial commit are present. |
| T010-03 | R010-03 | Repeat init and try an existing non-empty directory; assert nonzero errors, unchanged `HEAD`/marker data, and no overwrite. |
| T010-04 | R010-02 | Remove global/system Git identity from the test process; assert init fails explicitly without configured author and committer. |
| T010-05 | R010-06 | Attempt initialization below a regular-file path; assert the cause is reported and the pre-existing file is unchanged. |
| T010-06 | R010-04, R010-05 | Assert human output stays readable and JSON output follows schema version 2 with resources relative to the workspace. |
| T010-07 | R010-08 | Verify init does not create the derived SQLite index or require database, template, signature, approval, or UI state. |
| T010-08 | R010-06 | Inject a Git pre-commit-hook failure after the commit attempt begins; assert generated resources and unborn `.git` are preserved, then recover successfully after correcting the cause. |
| T010-09 | R010-06 | Exercise rollback of attempt-created files and directories; assert pre-existing Git configuration is preserved and retry succeeds. |

## Manual checklist — pending

- [ ] Human reviews the init design, risk assessment, requirements, mappings,
  and automated evidence.
- [ ] Human initializes and verifies a representative workspace.
- [ ] Human inspects the human-readable and JSON resource lists.
- [ ] Human confirms repeat-init, existing-data, identity, and failure behavior.
- [ ] Reviewer records manual results before merge.
