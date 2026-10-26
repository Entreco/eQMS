---
title: EQMS-031 automated evidence
status: draft
related: [EQMS-031]
---

# EQMS-031 automated evidence

## Tested revision and environment

- Source revision: `71b1abe49363a3a7006605b60b8d93f5d5d77d20`.
- Final commands ran against this clean committed revision; evidence is
  committed separately afterward.
- Host: macOS 26.6.2 (25G83), arm64.
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`.
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`.
- JVM: OpenJDK 25.0.3; repository Gradle wrapper and locked Cargo dependencies.
- Git: 2.54.0 (Apple Git-157). Test author identities are fictional.

These are draft automated results, not human review, approval or compliance
evidence. Linked [design/risk](../../architecture/EQMS-031.md),
[requirements](../../requirements/EQMS-031.md) and
[mapped tests](../EQMS-031.md) define the delivered scope.

## Reproduction and observed outcomes

From a clean checkout of the tested revision:

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo build --workspace --release --locked
git diff --check
./gradlew :apps:desktop:test :feature:scaffold:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:ui:jvmTest --rerun-tasks --console=plain --quiet
```

All commands passed. Gradle tasks were forced to rerun; JUnit XML reports were
inspected under each module's `build/test-results/`. Raw machine logs and
generated reports are not committed.

| Area | Actual outcome | Mapping |
|---|---|---|
| Rust formatting / Clippy / locked release build / whitespace | Passed, Clippy warnings denied | T031-06 |
| Core unit tests | 66 passed, 0 failed, including 8 new audit tests | T031-01/02/03/04/05 |
| CLI unit tests | 10 passed, 0 failed, including audit help without stdin | T031-06 |
| Audit CLI integration | 3 passed, 0 failed, containing success/error/usage/checkpoint matrices | T031-05/06 |
| Existing workspace/document CLI integration | 12 passed, 0 failed | T031-06 |
| Existing Markdown CLI integration | 3 passed, 0 failed | T031-06 |
| Existing search CLI integration | 1 passed, 0 failed | T031-06 |
| Desktop JVM | 39 passed, 0 failed, 2 skipped (41 total) | T031-06 |
| Scaffold JVM | 18 passed, 0 failed | T031-06 |
| Document JVM | 9 passed, 0 failed | T031-06 |
| Search JVM | 8 passed, 0 failed | T031-06 |
| Shared UI JVM | 1 passed, 0 failed | T031-06 |
| Release audit smoke | Passed: new workspace plus saved draft, local/anchored modes, no index creation | T031-01/05/06 |

Totals: Rust 95 passed, 0 failed; JVM 75 passed, 0 failed, 2 skipped.
Rust doc tests contained no cases, not additional functional coverage.

Release smoke reproduction after the release build:

```sh
python3 - <<'PY'
import json, os, pathlib, subprocess, tempfile
binary = str(pathlib.Path('target/release/qms').resolve())
env = dict(os.environ, GIT_CONFIG_COUNT='2', GIT_CONFIG_KEY_0='user.name', GIT_CONFIG_VALUE_0='Audit Smoke', GIT_CONFIG_KEY_1='user.email', GIT_CONFIG_VALUE_1='audit-smoke@example.invalid')
with tempfile.TemporaryDirectory(prefix='eqms031-smoke-') as directory:
    def qms(*args, body=None):
        return subprocess.run([binary, *args], input=body, capture_output=True, env=env, check=True)
    qms('init', directory)
    qms('document', 'create', directory, '--title', 'Smoke', body=b'Body')
    local = json.loads(qms('audit', 'verify-ledger', directory, '--json').stdout)['data']
    assert local['trust'] == 'local_consistency' and len(local['drafts']) == 1
    anchored = json.loads(qms('audit', 'verify-ledger', directory, '--checkpoint', local['baseline_revision'], '--json').stdout)['data']
    assert anchored['trust'] == 'checkpoint_ancestry'
    assert not pathlib.Path(directory, '.eqms/index.sqlite3').exists()
    print('Release smoke passed: genesis + saved draft, local/anchored modes, no index creation')
PY
```

## Observed functional coverage

- A baseline with genesis and two content events plus two independent drafts
  verifies with expected baseline/draft/revision counts. Genesis, intermediate
  and HEAD checkpoints work. Journal, Git refs/index and document bytes remain
  unchanged; no SQLite index is created.
- Changed, reordered, missing, duplicate and incomplete journal records fail,
  as do canonical self-consistent truncations and rehashed replacements of
  prior committed journal prefixes. Diagnostics identify baseline commit and
  available record/line/sequence context. Earlier invalid records are reported
  before a later incomplete tail.
- Historical content changes, missing files and executable-mode substitutions
  fail at the historical offending commit even after later commits restore
  content and the ordinary current-state workspace verifier passes.
- A malformed draft ref/history and a valid draft created on an unrelated
  baseline fail with document/head or creation-baseline context. Existing
  draft content/journal/ancestry/tamper regressions also pass.
- Ref/expression, unknown and draft-only checkpoints are rejected. Shallow
  history, replacement refs, grafts, merges, checked-out internal drafts and
  history-override environment variables fail explicitly.
- CLI validates version-2 success/error envelopes, report fields, human trust
  wording, exit 1 operational errors, exit 2 usage errors, empty stdout on
  failure and informational help without stdin.

## Failures, warnings and limitations

- An initial CLI test passed `--help` as a separate checkpoint value and
  expected an operational validation error; Clap correctly treated this as
  invalid usage. The test now uses `--checkpoint=--help` to exercise rejection
  of a literal invalid value. Final suites all pass.
- Existing Skiko restricted-native-access warnings appeared on this JDK but
  did not fail the JVM suites.
- `DesktopSearchBenchmarkTest` skipped because
  `EQMS_SEARCH_BENCHMARK_WORKSPACE` was not supplied.
  `DesktopJewelPreviewTest` skipped because `EQMS_JEWEL_PREVIEW_DIR` was not
  supplied. Search algorithms and desktop presentation are unchanged; no new
  performance measurement or screenshot/manual visual acceptance is claimed.
- Windows/Linux execution, Wasm/browser compilation/runtime, SHA-256 Git-repo
  runtime and forced power-loss experiments were not performed. No Kotlin
  source/protocol or mutation/recovery semantics changed; affected JVM and Rust
  regressions ran. Missing platform tests are not passing functional coverage.
- Nonlinear/shallow/replacement/graft histories are intentionally unsupported.
  Complete verification walks historical journal bytes and content with Git
  subprocesses; no large-ledger performance budget is established here.
- No cryptographic signature, authenticated identity, trusted time, independent
  remote trust service, deleted-ref inventory or compliance is established.
  External writers can ignore the coordination lock. Future event producers in
  EQMS-030 are not completed by generic chain/content verification.
- EQMS-016 `.qms` migration/import/in-app opening remains follow-up work;
  no extension standard, OS association or dogfood CI workflow is delivered here.

## Human gates (pending)

- [ ] Review risk/design, requirements, implementation, tests and evidence.
- [ ] Verify multiple drafts with/without an independent checkpoint.
- [ ] Inspect broken-segment errors and confirm no automatic repair.
- [ ] Confirm trust limits and record manual acceptance before merge.

Issue #19 stays open and In progress pending human review/testing and merge.

## PR #59 review follow-up

Tested review-fix source revision:
`eed48686b44d47b8d78b3cf4d6da97627a708ece`. This addendum is committed separately
after testing that clean revision. The environment/toolchain matches the
original record above and was rechecked.

The three review comments identified lazy fetching in partial clones, omitted
broken symbolic refs, and quadratic per-binding historical blob subprocesses.
Design, requirements and mappings were updated before the fixes. T031-07/08
cover the added controls; the initial record above remains historical evidence
for the initial implementation, not the current review revision.

### Reproduction and outcomes

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo build --workspace --release --locked
git diff --check
cargo test -p eqms-cli --locked --test audit growing_bindings -- --nocapture
./gradlew :apps:desktop:test :feature:scaffold:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:ui:jvmTest --rerun-tasks --console=plain --quiet
```

All final commands above passed at the review-fix source revision.

| Area | Observed outcome |
|---|---|
| Rust formatting, Clippy, release build, whitespace | Passed |
| Core | 68 passed, 0 failed, including 10 audit tests |
| CLI unit | 10 passed, 0 failed |
| Audit CLI integration | 5 passed, 0 failed |
| Existing CLI / Markdown / search integration | 12 / 3 / 1 passed, 0 failed |
| Desktop JVM | 39 passed, 0 failed, 2 skipped |
| Scaffold / document / search / shared UI JVM | 18 / 9 / 8 / 1 passed, 0 failed |

Totals: Rust 99 passed, 0 failed; JVM 75 passed, 0 failed, 2 skipped.
No new manual acceptance or platform coverage is implied.

- T031-07 creates an actual local `--filter=blob:none` clone with an absent
  historical content blob, verified with lazy fetching disabled. Audit fails
  explicitly on promisor configuration before object traversal. Git Trace2
  records no fetch/upload-pack/fsck during the rejected verification, and all
  object-pack file names/bytes remain unchanged. Shared Git and batch readers
  set `GIT_NO_LAZY_FETCH=1` as defense against implicit downloads.
- Core tests cover dangling/cyclic symbolic refs, malformed direct refs and
  direct refs pointing to missing objects. They fail the read-only connectivity
  check with ref context instead of disappearing from draft discovery. A
  packed valid draft ref remains discoverable and verifies.
- T031-08 records actual Git subprocess events for a 14-commit ledger that adds
  12 bound paths sharing one blob, then adds a commit with unchanged journal.
  Observed: **45 Git processes, 14 tree reads, 2 blob batches and 13 journal
  reads**. The two batches are the single previously unseen content blob and
  existing current-workspace validation. The unchanged journal is not reread.
  Tests assert exact tree/batch/journal counts and a linear process-count bound.
- Existing historical content/missing-file/mode corruption and draft tamper
  regressions still pass. Path/mode membership is checked at every commit even
  when a content hash is reused from the object-ID cache.

### Intermediate failures and residual limitations

- An initial test edit placed two tests inside another test function; the
  compiler warned they were unregistered. They were moved to module scope;
  the final audit integration runner reports all five tests executed, and
  Clippy passes with warnings denied.
- The first development JVM suite had one failure:
  `DesktopWindowNativeTest.nativeMouseClickReachesTheUnifiedTitleBarAction`
  timed out waiting 5000 ms for native mouse input. The isolated test retry
  passed; the full fresh JVM suite then passed at the committed review
  revision. No UI code or timeout was changed. This transient native-input
  failure is recorded rather than omitted.
- Existing Skiko native-access warnings and the two opt-in skips remain as
  described above. Windows/Linux, Wasm/browser, SHA-256 repository runtime and
  large-ledger wall-clock benchmarks remain unexecuted.
- The connectivity preflight conservatively fails on corrupt unrelated refs
  too. It is read-only but walks local connectivity. Batch/hash caching removes
  quadratic subprocess/blob-hashing work for accumulated bindings, not all
  historical CPU work: journal-prefix parsing and per-commit path comparisons
  can still grow with history size. No global performance budget is claimed.
- Deleted refs remain undetectable without an independent inventory; detectable
  broken refs are now explicit failures. External writers can ignore the lock.
  Partial/promisor repositories are intentionally unsupported rather than
  automatically hydrated. Identity/approval/trusted-time limitations and
  unchecked human gates remain unchanged.
