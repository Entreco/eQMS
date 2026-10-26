---
title: EQMS-020 automated evidence
status: draft
related: [EQMS-020]
---

# EQMS-020 automated evidence

## Revision and environment

- Tested source revision: `1286b8962cfe536bea83ad4a8e9cc92edd3a0d9c`.
- This evidence is committed separately after testing that clean revision.
- Host: macOS 26.6.2 (25G83), arm64.
- Rust: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`.
- Cargo: `cargo 1.99.1 (5f94df478 2026-08-27)`.
- JVM: OpenJDK 25.0.3, repository Gradle wrapper.
- Git: 2.54.0 (Apple Git-157); locked Cargo dependencies.
- Test identities are fictional fixtures. No human review/testing or approval
  is claimed; these are draft automated results, not compliance certification.

Design/risk: [EQMS-020](../../architecture/EQMS-020.md).
Requirements: [EQMS-020](../../requirements/EQMS-020.md).
Mapped tests: [EQMS-020](../EQMS-020.md).

## Reproduction and observed results

From a clean checkout of the tested source revision:

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo build --workspace --release --locked
git diff --check
./gradlew :apps:desktop:test :feature:scaffold:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:ui:jvmTest --rerun-tasks --console=plain --quiet
```

All commands completed successfully. Gradle was forced to rerun tasks rather
than treating previous cached/up-to-date results as freshly executed tests.
JUnit XML was inspected under each module's `build/test-results/`; generated
reports and raw machine logs are not committed.

| Area | Observed outcome | Mapping |
|---|---|---|
| Format, Clippy, release build, whitespace | Passed; warnings denied for Rust | T020-07 |
| Rust core | 58 passed, 0 failed; includes 4 codec tests and 1 pre-change-history compatibility test | T020-01/02/03/04/07 |
| CLI unit tests | 10 passed, 0 failed; includes stdin-read failure, help-without-input and exact-output response coverage | T020-06/07 |
| Existing CLI integration | 12 passed, 0 failed | T020-04/07 |
| New format/validation CLI integration | 3 passed, 0 failed; tests contain file/stdin/body/output/error matrices | T020-05/06 |
| Search CLI integration | 1 passed, 0 failed | T020-04/07 |
| Desktop JVM | 39 passed, 0 failed, 2 skipped (41 total) | T020-07 |
| Scaffold JVM | 18 passed, 0 failed | T020-07 |
| Document JVM | 9 passed, 0 failed | T020-07 |
| Search JVM | 8 passed, 0 failed | T020-07 |
| Shared UI JVM | 1 passed, 0 failed | T020-07 |
| Release binary smoke | Exact formatter stdout and independently calculated SHA-256 matched | T020-01/05 |

The Rust suites total 84 passed, 0 failed. JVM suites total 75 passed, 0 failed,
2 skipped. Rust doc tests contained no cases; this is not additional coverage.

Release smoke reproduction after the release build:

```sh
python3 - <<'PY'
import hashlib, json, subprocess
binary = './target/release/qms'
content = b'---\n{"schema_version":1,"id":"DOC-0123456789abcdef0123456789abcdef","title":"Policy","status":"Draft"}\n---\nBody'
formatted = subprocess.run([binary, 'document', 'format', '-'], input=content, capture_output=True, check=True)
assert formatted.stdout == content and not formatted.stderr
validated = subprocess.run([binary, 'document', 'validate', '-', '--json'], input=content, capture_output=True, check=True)
data = json.loads(validated.stdout)['data']
assert data['content_hash'] == 'sha256:' + hashlib.sha256(content).hexdigest()
print('Release smoke: exact stdout and independently computed SHA-256 passed')
PY
```

## Compatibility and functional observations

- `core/rust/tests/fixtures/eqms020-legacy.json` is a JSON-encoded Git
  fast-export stream generated with the original CLI at baseline `246f808`,
  before codec extraction. The fictional Legacy Fixture identity created the
  workspace and one saved Draft. JSON escaping retains trailing spaces without
  introducing literal trailing whitespace in the fixture file.
- The compatibility test imports that fixed stream with `git fast-import`,
  materializes the baseline, and opens it with the new codec. The document
  remains `DOC-3683c4441260979ff0733dd027d68911`, saved commit
  `2c83fc82db4e2bbeb178361134478aa5d84a2cc6`, and content hash
  `sha256:224514eb12998689344189b973cb15debd199e90ea748e0a70033fc4ff3a6308`.
  Strict history verification succeeds; reconstructed canonical bytes match.
  An incorrect expected ID and noncanonical saved encoding are rejected.
- A separate fixed-byte Policy/Body fixture hashes to
  `sha256:2866a39ccb18a2d807569fbbadb98f7b17a2cfc15e4155bc60efc4fc5bc44ccf`,
  independently calculated with `shasum -a 256` and the release smoke's
  Python `hashlib`.
- Semantic parsing accepts supported metadata whitespace/key order and
  CRLF/CR variants; strict validation rejects their noncanonical encodings.
  Formatting is idempotent. Empty/Unicode bodies, escaped titles, embedded
  delimiters, trailing spaces and final-newline variants preserve specified
  bytes. Changed canonical content produces a different hash.
- Invalid UTF-8, BOM, malformed JSON/headers, duplicate/unknown/missing fields,
  unsupported schemas/statuses and invalid IDs/titles fail explicitly.
- CLI integration runs in a directory without a workspace. File paths with
  spaces and stdin both work; source bytes remain unchanged and no workspace
  is created. Raw formatter bytes and JSON content/hash agree. Missing files,
  directories, invalid content, noncanonical validation and usage errors have
  nonzero exit codes with empty success stdout; injected read failures return
  explicit errors.
- Existing authoring, stale/no-op saves, history/diff, tampering, baseline
  isolation, workspace/index/search and desktop adapter regressions pass.
  Format validation does not extend their trust guarantees.

## Failures, warnings and exclusions

- During development, an initial Cargo invocation used the binary name `qms`
  as a package selector; corrected to package `eqms-cli`. An extraction build
  caught the still-needed SHA-256 import for ID generation; restored.
- A misplaced test module caused a formatting parse failure and was moved to
  module scope. Whitespace checks caught newly added Markdown hard-break
  spaces; removed without changing unrelated roadmap text. All final source
  checks above pass; these intermediate failures are not passing coverage.
- Skiko emitted existing restricted-native-access warnings on this JDK; no
  test failure resulted.
- `DesktopSearchBenchmarkTest` was skipped because
  `EQMS_SEARCH_BENCHMARK_WORKSPACE` was not supplied. This format-only change
  does not change search algorithms or claim new performance measurements.
- `DesktopJewelPreviewTest` was skipped because `EQMS_JEWEL_PREVIEW_DIR` was
  not supplied. No screenshot capture or manual visual acceptance is claimed;
  desktop presentation is unchanged.
- Windows/Linux runtime, Wasm/browser compilation/runtime and forced
  power-loss tests were not executed. No Kotlin/Wasm source or existing
  persistence protocol changed; affected JVM/CLI/core regressions ran.
- No import, general artifact migration, lifecycle/signature/publication,
  plugin-schema/reference validation or generalized ledger verification was
  implemented/tested in this scope.
- Local hashes validate bytes, not authenticated identity, trusted time,
  approval, compliance or protection against whole-history replacement.

## Manual gates (pending)

- [ ] Human reviews risk/design, requirements, mapped tests, implementation and evidence.
- [ ] Human checks canonical examples, exact formatter output and invalid-input errors.
- [ ] Human opens a pre-change draft and inspects history/comparison.
- [ ] Human confirms format-only/Draft/trust wording and records acceptance.

Issue #14 remains open and In progress pending human review/testing and merge.
