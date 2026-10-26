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
