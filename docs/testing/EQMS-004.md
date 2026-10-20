---
title: Native foundation test specification
status: draft
related: [EQMS-004]
---

# EQMS-004 tests

Defined before implementation. [Requirements](../requirements/EQMS-004.md).

| ID | Method and expected result |
|---|---|
| T004-01 | Unit-test nonempty core version; build native release and review core dependency tree (no third-party dependencies). |
| T004-02 | Invoke empty args, --help, -h: exit 0, help on stdout, empty stderr. |
| T004-03 | Invoke --version, -V, --json --version and reversed order: exit 0; JSON parses and version matches core. |
| T004-04 | Unknown command/flag, unexpected value and --json alone: exit 2, empty stdout, actionable stderr; JSON parses with expected code. |
| T004-05 | Invalid arguments with --json before/after them produce structured errors; JSON plus help remains human-readable. |
| T004-06 | Run smoke tests in isolated temp directories; verify directory remains empty after each call. |
| T004-07 | Serial release --help/--version baseline: record first observed launch, warmups, raw warm samples and median/p95; no threshold until baseline reviewed. |
| T004-08 | Run existing cmd/desktop, core JVM tests and web development webpack; distinguish no-source tests from functional coverage. |

Quality gates: fmt check, Clippy with warnings denied, locked workspace tests,
locked release build. CI repeats Rust checks.

## Manual checklist — pending

- [ ] Human reviews design, requirements, risks, dependency changes and evidence.
- [ ] Human builds and runs help/version/JSON/error cases on their machine.
- [ ] Human confirms Kotlin shell behavior and reviews startup methodology.
- [ ] Reviewer records outcome before merging.
