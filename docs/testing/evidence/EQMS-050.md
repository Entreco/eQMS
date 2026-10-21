---
title: Project search automated evidence
status: draft
related: [EQMS-002, EQMS-023, EQMS-050, EQMS-051, EQMS-090, EQMS-101]
---

# EQMS-050/051 automated evidence

Tested implementation revision: `13191007efa715c6864fa6401c018d1348f206f0`.
The working tree was clean for the final performance runs. This record and raw
reports are committed separately after that tested code revision.

Release executable SHA-256:
`1ad0174d1d1dad770ca4a553c19014e320272cb9f8bb59684159011d9c467e2f`.
Both final CLI and UI reports identify this exact binary and revision.

These are observed automated results, not human approval, regulatory evidence
acceptance, or a production responsiveness guarantee.

## Environment

Apple M5 Pro, 18 available processors, 24 GiB RAM, aarch64; macOS 26.6.2
(Darwin 25.6.0). Rust/rustc 1.99.1, Cargo 1.99.1, Python 3.9.6, Gradle 9.6.1,
JDK 25.0.3. Native release build, ordinary warm filesystem caches, no cache
eviction or dedicated-machine isolation. JVM native-access warnings were
observed; they did not fail the checks.

## Commands and outcomes

Executed from the repository root; `<fixture>` below is an explicitly selected
session directory, not repository or user data.

| Command | Observed outcome |
|---|---|
| `cargo fmt --all -- --check` | Passed; formatter was also run after Rust edits. |
| `cargo clippy --workspace --all-targets --locked --quiet -- -D warnings` | Passed. |
| `cargo test --workspace --locked --quiet` | 65 passed: 8 CLI unit, 10 existing CLI integration, 1 search CLI integration, 46 core tests; zero failures. |
| `cargo build --workspace --release --locked --quiet` | Passed. |
| `./gradlew :core:ui:jvmTest :core:ui:compileKotlinWasmJs :apps:desktop:test :apps:web:compileKotlinWasmJs --console=plain --quiet` | Passed: 21 shared UI tests; desktop 24 passed and opt-in benchmark explicitly skipped without fixture environment; shared/web Wasm compilation passed. |
| `python3 scripts/benchmark_search.py <fixture> --reuse --samples 30 --warmups 3` | Final CLI benchmark passed query/fallback/fixture assertions; raw results linked below. |
| `EQMS_SEARCH_BENCHMARK_WORKSPACE=<fixture> EQMS_QMS_PATH="$PWD/target/release/qms" ./gradlew :apps:desktop:test --tests '*DesktopSearchBenchmarkTest' --rerun-tasks --console=plain --quiet` | Opt-in real desktop/native/Compose benchmark executed, not skipped: passed all four p95 <=150 ms assertions with 100 samples/query. |
| `git diff --check` | Passed before code commits. |

The ordinary suite's benchmark skip is superseded by the explicitly enabled,
separate benchmark run above, not counted as a functional pass.

## Coverage and traceability

See [requirements](../../requirements/EQMS-050.md) and
[test specification](../EQMS-050.md), TEST-050-001 through TEST-050-009.
Observed coverage includes:

- The `And` title/author/body examples, ampersands/common words, Unicode case,
  prefixes, all four one-edit operations, short-token exclusion and stable ranking.
- Literal FTS metacharacters, multiword field matching, empty queries, CLI
  human/schema-2 output, pagination/snapshot errors and native adapter validation.
- Global-only history fallback, historical title/body/author changes, per-document
  collapse, exact read-only historical navigation and return to latest.
- Index migrations and upgrade preservation, failed transaction rollback,
  rebuild, deleted/changed refs, verification failures and baseline/staged/journal
  preservation. Standard packing preserves branch/tag names and exact OIDs.
- Binary-safe batched Git reads, missing/non-blob/NUL paths, literal newline
  filenames on Unix, and requests larger than pipe capacity without deadlock.
- Dirty navigation, save-conflict edit retention, query/project cancellation,
  independent Show more, stale index refresh/retry, stale/duplicate render
  generation rejection and repeated identical query/result timing.
- Specific native process termination/reader-writer cleanup on cancellation,
  large bidirectional streaming and I/O error propagation.

## Reference fixture and performance method

Generated using `scripts/benchmark_search.py <new-fixture>` through real Git
fast-import and production history verification/index preparation. It contains:

- 10,000 current saved draft documents, 10,486 body bytes each:
  **104,860,000 current body bytes**, slightly above 100 MiB.
- 100 additional older saved revisions, one on every hundredth document.
- Five recurring title families, repeated risk/traceability prose and numbered
  documents; one third use configured author `Andi`.

The fixture is synthetic and text is repetitive. It is not proof of performance
on arbitrary vocabulary distributions, deep histories, larger projects or
other operating systems.

CLI measurements use 3 warmups and 30 retained samples per query. UI measurements
use 20 warmups and **100 retained samples per query**, cycled across four queries.
The UI stopwatch starts at the final input update and ends at actual Compose
result layout, including the **10 ms debounce**, native process invocation,
workspace verification, authoritative Git ref enumeration, SQL, JSON decoding
and layout. Timing is bound to an immutable request generation; stale callbacks
are rejected. No measured samples, including outliers, are discarded.

The UI benchmark uses the real desktop adapter and real native queries but
omits the initial full-document list load. It isolates warm search, not project
opening. Its initial preparation is a no-op refresh of the already prepared
fixture (148.08 ms), not a cold indexing measurement.

### Final observed latency

| Query | Mode | CLI p50 / p95 (ms) | UI input-to-layout p50 / p95 (ms) |
|---|---|---:|---:|
| `And` | Current | 69.63 / 77.03 | 94.68 / **108.30** |
| `risk` | Current | 66.06 / 75.03 | 92.84 / **111.17** |
| `managment` | Current, typo tolerant | 64.66 / 70.53 | 92.60 / **105.43** |
| `trace` | Current, prefix | 63.20 / 67.17 | 91.01 / **102.57** |
| `legacyobsolete` | Historical fallback | 49.68 / 55.11 | Not measured in UI benchmark |
| `zzzznomatch` | Historical fallback, no matches | 51.27 / 56.30 | Not measured in UI benchmark |

The agreed warm **p95 <=150 ms** target passed for all four current UI queries.
Raw samples, fixture shape, executable hash and environment are in
[CLI JSON](EQMS-050-cli.json) and [UI JSON](EQMS-050-ui.json).
Machine-specific paths have been redacted from committed reports.

## Failed attempts, fixes and limits

The first loose-ref reference run exceeded the budget: `And` CLI p95 was
259.07 ms, with roughly 150 ms spent enumerating 10,000 loose refs. The user
explicitly selected standard `git pack-refs --all` during preparation rather
than a potentially stale metadata cache or a persistent service.

Initial preparation of that first, pre-optimized implementation took
**608,442.43 ms** to verify/index all 10,100 saved revisions. This is a preliminary
cold baseline, not a cold measurement of the final revision. Cold preparation
on the final revision was not rerun; no cold improvement is claimed. Full
history verification remains expensive, and project opening/full-document
listing is outside the warm-search measurement.

The first UI benchmark exposed a stale-layout timing attribution bug; generation
binding and regressions fixed it. Subsequent 30-sample UI measurements still
failed the latency budget despite ref packing. Exact Git blob verification was
then batched without skipping checks, current-result historical windows were
removed, a covering metadata index avoided loading body pages for all candidates,
and debounce was reduced to 10 ms. Warmups/samples were expanded to 20/100 to
characterize JVM tails, without changing the 150 ms threshold or removing
measured outliers. The final committed-revision run above passed.

No Windows/Linux execution, native packaging/signing, browser filesystem/service
adapter, full Gradle monorepo build, or human review/testing was performed.
Browser/shared Wasm compilation is not browser functional search coverage.
Configured authors/local timestamps remain claims, and local Git/cache trust
does not protect against an administrator replacing all data.

## Manual review/testing

- [ ] Review search semantics, packing, index trust boundary and dependency status.
- [ ] Exercise a real project, all groups, Show more and external saves.
- [ ] Confirm historical clarity/read-only behavior and safe return to current.
- [ ] Confirm dirty edits survive failed saves and cancelled navigation.
- [ ] Review representative responsiveness on intended deployment hardware.

Leave #23/#24 open and their project items In progress pending human review and
remaining explicitly deferred EQMS-050 scope. No agent approval or merge is implied.
