---
title: EQMS-113 document-template binding tests
status: draft
related: [EQMS-113]
---

# EQMS-113 test specification

Issue: #85.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T113-01 | R113-01 | Core unit tests (markdown): pinned document round-trips with a stable golden line; unbound golden unchanged; partial pins, bad id, version 0, bad hash and reordered fields rejected. |
| T113-02 | R113-02, R113-03 | Core test: create from a template seeds page and scaffold, explicit page override wins, supplied body wins over scaffold, unknown template/version rejected. |
| T113-03 | R113-04, R113-05 | Core test: bind, change and clear a Draft; plain save keeps pin; same-pin is a no-op; journal carries `template_from`/`template_to`; history verifies and each revision keeps its pin; stale revision and non-Draft rejected. |
| T113-04 | R113-04 | Core test: a tampered journal or document that changes the pin without the matching payload fails verification. |
| T113-05 | R113-02, R113-03, R113-04, R113-05, R113-06 | CLI test: install template, `create --type`, ambiguous type error, `--template`, show JSON, `set-template`, `--clear`, `show --revision` pin history, unbound JSON has no `template`. |
| T113-06 | R113-07 | `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test --workspace --locked` with `GIT_CONFIG_GLOBAL=/dev/null`. |

Manual review/testing (left unchecked in the PR): bind a document to an
installed template, change it, inspect `qms document history` and the journal,
and review the precedence rules against the roadmap in #78.
