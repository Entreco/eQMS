---
title: Native foundation automated test evidence
status: draft
related: [EQMS-004]
---

# EQMS-004 automated execution record

Tested code revision: `237f27bb07db0e2a114141cd02639980575c93b2`.
This record is added in a subsequent documentation-only commit; the tested
implementation and lockfile are unchanged. Execution: 2026-10-20.

Environment: Apple M5 Pro, arm64, Darwin 25.6.0 / macOS 26.6.2;
rustc 1.99.1 (58cf8f9c3 2026-10-13), Cargo 1.99.1,
Gradle 9.6.1, JetBrains JDK 25.0.3.

Specification: [tests](../EQMS-004.md),
[requirements](../../requirements/EQMS-004.md),
[design/risks](../../architecture/EQMS-004.md).

## Automated results

| Command | Exit | Observed result |
|---|---|---|
| `cargo fmt --all -- --check` | 0 | Formatting accepted |
| `cargo clippy --workspace --all-targets --locked -- -D warnings` | 0 | No warnings |
| `cargo test --workspace --locked` | 0 | 1 core unit test and 5 CLI integration tests passed; no doc tests |
| `cargo build --workspace --release --locked` | 0 | Native release executable built |
| `cargo tree -p eqms-core` | 0 | eqms-core 0.1.0 only; no third-party dependencies |
| `./gradlew --no-daemon :apps:cmd:run :apps:desktop:run :core:ledger:jvmTest :core:search:jvmTest :apps:web:wasmJsBrowserDevelopmentWebpack` | 0 | Prototype cmd opened SQLite schema v1; desktop ran; web webpack build available; both core JVM test tasks NO-SOURCE |
| `python3 scripts/benchmark_startup.py` | 0 | Serial release measurement recorded below |

CLI tests execute 17 isolated invocations across five cases. All assert the
working directory remains empty and validate exit/stream contracts. Structured
responses are parsed as JSON, not matched by substring.

T004-01 through T004-07 have automated evidence here; T004-08 has prototype
run/build evidence only, not Kotlin functional test coverage or browser UX
testing. CI on Linux and human tests were not executed locally.

Existing Kotlin warnings remain: SQLite logging provider/native-access warnings,
plugin-added repository warnings and Wasm interop warnings on recompilation.
No unrelated prototype cleanup was attempted.

## Release startup baseline

Invocation: `target/release/qms --version` and `--help` via Python subprocess,
stdout discarded; serial wall-time measurements with no cache eviction.
The final run occurred after build/regression tasks finished. Other system load
is uncontrolled. Five warmups and 50 measured samples per operation. First
observed launches are not controlled cold-cache measurements. Builds and Cargo
startup are excluded; OS process creation is included.

| Operation | First observed (ms) | Warm median (ms) | Warm nearest-rank p95 (ms) |
|---|---|---|---|
| --version | 3.620167 | 1.7293125 | 1.91725 |
| --help | 1.950584 | 1.701167 | 2.001791 |

Raw samples in milliseconds, in execution order:

```json
{
  "--version": [
    1.885375, 1.875, 1.705167, 1.888084, 1.733875,
    1.820584, 1.775916, 1.909292, 1.691334, 1.676083,
    1.709041, 1.779542, 1.755709, 1.7885, 1.83875,
    1.59275, 1.824875, 1.8025, 1.705792, 1.6425,
    1.675333, 1.941542, 2.011, 1.847083, 1.654,
    1.869458, 1.74225, 1.616583, 1.630042, 1.738708,
    1.716625, 1.6375, 1.793209, 1.672041, 1.791458,
    1.720083, 1.717375, 1.543625, 1.640042, 1.760083,
    1.696583, 1.682583, 1.746666, 1.734125, 1.700625,
    1.700917, 1.677833, 1.700708, 1.72475, 1.91725
  ],
  "--help": [
    1.6775, 1.645084, 1.63075, 1.711459, 1.686583,
    1.7485, 1.678, 1.642917, 1.686333, 1.593542,
    1.640833, 1.746208, 1.622709, 1.624667, 1.590834,
    1.780333, 1.727166, 1.653375, 1.779625, 1.768666,
    1.702417, 1.623625, 1.70375, 1.740333, 1.808833,
    1.884542, 1.851334, 1.630375, 1.650541, 1.667042,
    1.672375, 1.774333, 1.701625, 1.7645, 1.69925,
    1.725917, 1.717334, 1.652208, 1.689542, 2.063167,
    2.001791, 1.694375, 1.728042, 1.685834, 1.700709,
    1.712375, 2.335459, 1.890458, 1.712334, 1.675125
  ]
}
```

These are help/version foundation measurements on one machine, not evidence of
document-operation latency or a universal speed guarantee. No acceptance
threshold has been established yet.

## Pending human review

- [ ] Review risk applicability, design, requirements and test coverage.
- [ ] Independently build/test native CLI and affected shells.
- [ ] Review baseline method and agree future performance budgets.
- [ ] Approve records and PR only after manual review/testing.
