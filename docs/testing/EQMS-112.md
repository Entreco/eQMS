---
title: EQMS-112 template model tests
status: draft
related: [EQMS-112]
---

# EQMS-112 test specification

Issue: #84.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T112-01 | R112-01, R112-03 | Core unit test: twenty single-field mutations (id, version, names, sections, blocks, placeholder body, size, colour, image width, TOC depth, break level, unknown placeholder, slot length, asset hash, margins) are each rejected. |
| T112-02 | R112-04 | Core unit tests: built-in round trip and stable hash; strict decoding rejects tabs, unknown fields, empty object. |
| T112-03 | R112-04 | Core property tests: valid templates round-trip; arbitrary bytes never panic when decoded. |
| T112-04 | R112-05, R112-06 | Core store test with a real workspace: install with assets, missing asset rejected, immutability, newer version accepted, wrong asset rejected, journal verifies, tampering detected. |
| T112-05 | R112-07 | CLI test: validate, list, install, show round trip; duplicate install, unknown id and missing file fail. |
| T112-06 | R112-02 | Review: the schema types contain no free-form style fields (enums and bounded numbers only). |
| T112-07 | R112-08 | `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test --workspace --locked` with `GIT_CONFIG_GLOBAL=/dev/null`. |

Manual review/testing (left unchecked in the PR): install a template with a PNG
asset, edit the installed file and confirm `list` fails, review the risk table
and schema against the roadmap in #78.
