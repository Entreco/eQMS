---
title: EQMS-094 desktop responsiveness evidence
status: generated
related: [EQMS-094, EQMS-092, EQMS-102]
tested_revision: e0ca06b5f57ae4b3548fb2afef3a3d9a1955d461
---

# EQMS-094 automated evidence

Issue: #65. This generated record is not human approval.

## Tested source and environment

- Source revision: `e0ca06b5f57ae4b3548fb2afef3a3d9a1955d461`
- Worktree: clean for all recorded runs
- Native build: release `qms`, SHA-256
  `db05fb87c90fcb8bd916ee53f6d28662d8cf5984d9fa8caab1ad64ca2f734956`
- Platform: macOS 26.6.2, aarch64, Apple M5 Pro
- Java: 25.0.3+ (JBR build `15898627-b508.16`)
- Dataset: `EQMS-094 synthetic v1`, 128-byte initial bodies; 8 documents / 2
  revisions on the first document and 16 documents / 4 revisions on the first
  document. Each fixture was authoritatively verified before measurement.
- Each UI timing is 30 Compose samples. Adapter samples include CLI execution
  and JSON decoding; project/list and History readiness samples include Compose
  readiness. Native child-process durations and Git start counts are sampled
  separately where available. JSON decoding and individual composition/layout
  costs are not isolated. Standalone Git trace runs were used for counts because
  tracing perturbs timing.

## Latency and subprocess outcomes

| Dataset / operation | Baseline p95 | Target p95 | Measured p95 | Outcome |
|---|---:|---:|---:|---|
| 8 docs: project open to first list content | 1,133.42 ms | 1,050 ms | 520.55 ms | Pass |
| 8 docs: History toggle to usable content | 845.10 ms | 700 ms | 146.58 ms | Pass |
| 16 docs: project open to first list content | 2,126.68 ms | 1,900 ms | 895.86 ms | Pass |
| 16 docs: History toggle to usable content | 1,304.89 ms | 1,100 ms | 118.94 ms | Pass |

Separate `GIT_TRACE2_EVENT` counts per CLI operation:

| Dataset | Verify | List | History snapshot |
|---|---:|---:|---:|
| 8 docs | 4 | 30 | 9 |
| 16 docs | 4 | 54 | 9 |

The pre-change list counts were 97 and 193, respectively. History previously
used separate document and lifecycle operations with 26 Git starts each on the
8-document fixture and 42 each on the 16-document fixture. The new combined
snapshot uses 9 Git starts on both datasets. Full sample distributions,
stage/process timings, fixture metadata, and binary identity are in
[8-document JSON](EQMS-094-8.json) and
[16-document JSON](EQMS-094-16.json).

## Commands and outcomes

Run from a clean checkout of the tested revision:

```sh
cargo fmt --all -- --check
cargo test --workspace
cargo build --release -p eqms-cli
./gradlew :feature:search:jvmTest :feature:document:jvmTest :feature:scaffold:jvmTest :apps:desktop:test --no-daemon
./gradlew :feature:search:compileKotlinWasmJs :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs --rerun-tasks --no-daemon
```

All commands above passed. Rust workspace results: 72 core unit tests, 13 CLI
unit tests, 21 CLI integration tests, and doc tests passed. The affected search,
document, scaffold, and desktop JVM suites passed. Release `qms` built
successfully. A final `git diff --check` also passed.

The Wasm UI-test configuration was separately checked with:

```sh
./gradlew :feature:document:wasmJsTest --no-daemon
```

It is blocked by the existing Compose target configuration:
`checkComposeUiTestConfigurationForWasmJs` requires `binaries.executable()` on
the `wasmJs` target to load the Skiko runtime. This is recorded as an exclusion,
not a passing UI test; Wasm source-set compilation did pass.

Fixtures and UI timings were generated with:

```sh
python3 scripts/benchmark_desktop_responsiveness.py "$EQMS094_FIXTURE_8" --binary target/release/qms --documents 8 --revisions 2 --body-bytes 128 --git-counts --history-bundle
python3 scripts/benchmark_desktop_responsiveness.py "$EQMS094_FIXTURE_16" --binary target/release/qms --documents 16 --revisions 4 --body-bytes 128 --git-counts --history-bundle
EQMS_QMS_PATH=target/release/qms EQMS_RESPONSIVENESS_WORKSPACE="$EQMS094_FIXTURE" EQMS_RESPONSIVENESS_REPORT="$EQMS094_REPORT" EQMS_RESPONSIVENESS_RUNS=30 ./gradlew :apps:desktop:test --tests 'com.entreco.eqms.desktop.DesktopResponsivenessBenchmarkTest.projectOpenAndHistoryReadiness' --rerun-tasks --no-daemon
```

The last command was run once per fixture with the matching workspace/report
variables. Both tests passed their pre-established latency budgets and asserted
that blank search did not start search preparation.

## Limitations and pending human checks

These are synthetic local-workspace measurements on one macOS host. Fixture
creation/verification precedes the timing run; OS page-cache state is not
controlled, so cold-cache latency is not claimed. The results do not guarantee
latency on other storage or hardware. No human visual, input, accessibility, or
manual project-switching review/testing was performed. Those checks remain
unchecked pending review of the draft PR; the issue remains open.
