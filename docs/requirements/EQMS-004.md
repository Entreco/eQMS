---
title: Native foundation requirements
status: draft
related: [EQMS-004, UN-009, UN-010]
---

# EQMS-004 requirements

Design: [native foundation](../architecture/EQMS-004.md).
Tests: [test specification](../testing/EQMS-004.md).

| ID | Requirement | Verification |
|---|---|---|
| R004-01 | Cargo builds a native qms executable without requiring JVM; core has no CLI/UI dependencies. | T004-01, dependency-tree review |
| R004-02 | Empty invocation and help display useful help with exit 0. | T004-02 |
| R004-03 | Version uses core version; JSON version follows the unified schema-version-2 contract defined by EQMS-101. | T004-03, R101-01 |
| R004-04 | Unknown commands/flags and malformed arguments exit 2, with diagnostics on stderr and no stdout; JSON errors use INVALID_ARGUMENT. | T004-04 |
| R004-05 | Global JSON error behavior is independent of flag order; help remains human-readable. | T004-05 |
| R004-06 | Foundation invocations do not create workspace files. | T004-06 |
| R004-07 | Startup measurements record release build, hardware/platform, method, raw samples and statistics without claiming controlled cold-cache results. | T004-07 |
| R004-08 | Kotlin shells/prototype remain usable and are clearly distinguished from native qms. | T004-08 |

UN-010 motivates R004-01 through R004-06; UN-009 motivates R004-07.
All records remain draft pending manual review/testing.
