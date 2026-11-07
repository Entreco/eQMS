---
title: EQMS-106 document metadata tests
status: draft
related: [EQMS-106]
---

# EQMS-106 test specification

Issue: #70. Mappings are defined before implementation.

| Test ID | Requirement mapping | Planned check |
|---|---|---|
| T106-01 | R106-01, R106-02 | Rust unit tests: golden v2 bytes and independent SHA-256; defaults; round trip; each invalid field/range/placeholder/template case; v1, unknown, missing, duplicate and noncanonical input rejected; formatting normalizes whitespace/order. |
| T106-02 | R106-01, R106-02 | Rust property tests: any valid `PageSetup` encodes deterministically and round-trips; arbitrary strings never panic and valid output always revalidates. |
| T106-03 | R106-03 | Rust document tests: metadata-only save yields new revision/hash, journal `document.saved` event and non-empty diff containing the metadata line; identical save returns existing revision; history verification accepts v2 chain. |
| T106-04 | R106-03, R106-04 | CLI tests: `create/save` options, omitted options keep previous values, invalid values fail without writing; `show/history/render` leave refs unchanged. |
| T106-05 | R106-05 | Compose tests: Metadata panel fields edit the buffer, show inline validation errors, dirty the tab, persist only on Save, conflict/close guards apply, keyboard shortcut toggles panel. |
| T106-06 | R106-05 | Compose tests in light and dark themes and narrow window: panel remains usable. |
| T106-07 | R106-06 | Compose tests: ID/revision/hash/status shown exactly as saved; historical and non-Draft records are read-only. |
| T106-08 | R106-07 | Tests: capabilities no longer expose page-setup preference; no Page setup dialog; pagination uses the tab's metadata. |
| T106-09 | R106-08 | Regressions: `cargo test --workspace`, Gradle JVM suites (document, scaffold, ui, search, desktop), native and Wasm compile/test tasks. Exclusions recorded as exclusions, not passes. |

Manual review/testing (not performed by automation, left unchecked in the PR):
edit and save metadata, confirm diff in History, historical/non-Draft read-only
view, keyboard and screen-reader behavior, both themes, narrow windows.
