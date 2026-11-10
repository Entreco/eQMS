---
title: EQMS-107 project branding tests
status: draft
related: [EQMS-107]
---

# EQMS-107 test specification

Issue: #76.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T107-01 | R107-01, R107-03 | Core unit tests: validation limits, canonical round trip, PNG structure/size/dimension rejection. |
| T107-02 | R107-01, R107-02 | Core property tests: valid branding round-trips; generated logos are deterministic and legible. |
| T107-03 | R107-04, R107-05 | Core store tests with a real workspace: set/show round trip, journal event, tamper detection, non-persisted defaults. |
| T107-04 | R107-06 | Core unit test: contrast math and non-blocking warnings. |
| T107-05 | R107-03, R107-07 | CLI test: `set`/`show` round trip, bad accent and missing logo fail; manual smoke of `init --name --accent auto`. |
| T107-06 | R107-08 | `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test --workspace`. |

Manual review/testing (not performed by automation, left unchecked in the PR):
try PNG logos (valid, corrupt, oversized), inspect the git commit and journal,
review the risk table and the scope split.
