---
title: Build and Run
status: active
owner: eQMS
last_updated: 2026-10-20
related:
  - EQMS-001
  - EQMS-004
  - EQMS-101
  - EQMS-090
---

# Build and Run

Run commands from the repository root. The Rust native CLI owns workspace
operations and the derived SQLite index; Kotlin cmd remains a bootstrap
prototype.

## Rust native CLI

Install a stable Rust toolchain with Cargo, rustfmt and Clippy. The tested version
is recorded in [EQMS-004 evidence](../testing/evidence/EQMS-004.md).

```sh
cargo build --workspace --release --locked
./target/release/qms --help
./target/release/qms --version
./target/release/qms --json --version
```

No JVM is required by `qms`. Git must be installed and configured with a user
name and email before initializing a workspace; the CLI uses that configured
identity and does not invent one.

Initialize and verify a workspace, then build its derived index on demand:

```sh
./target/release/qms init ./quality-workspace
./target/release/qms verify ./quality-workspace
./target/release/qms index rebuild ./quality-workspace
./target/release/qms recover ./quality-workspace
```

The workspace must be empty or contain only an empty Git repository. Canonical
configuration and the audit journal are committed in Git; Markdown under
`documents/` is indexed into the ignored `.eqms/index.sqlite3` file. Rebuilding
the index does not change Git history or the journal. See the
[workspace and index contract](../architecture/EQMS-005.md).

```sh
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo test --workspace --locked
cargo tree -p eqms-core
python3 scripts/benchmark_startup.py
```

Python 3 is needed only for the optional serial startup benchmark, not the CLI.
Build release first; the script prints JSON including environment, raw samples,
median and p95. First observed launch is not a controlled cold-cache measurement.
Use `--samples` and `--warmups` to adjust the sample counts.

All native `--json` responses use the schema-version-2 envelope documented in
the [CLI JSON contract](../architecture/EQMS-101.md). Command payloads are in
`data`, errors are in `error`, and JSON errors are written to stderr with
nonzero exit codes. See the [native foundation design](../architecture/EQMS-004.md)
and [workspace/index contract](../architecture/EQMS-005.md) for related behavior.

## Kotlin prototype prerequisites

- JDK installed and available for Gradle
- Use the project wrapper: `./gradlew`

## Kotlin build

- Build all modules:
  - `./gradlew build`

## Run Kotlin application entrypoints

- Prototype CLI (not native `qms`):
  - `./gradlew :apps:cmd:run`
- Desktop app (build the native CLI first; Gradle `run` and `hotRun` use the
  repository's absolute `target/release/qms` path, or `qms.exe` on Windows,
  unless `EQMS_QMS_PATH` explicitly selects another executable):
  - `cargo build --workspace --release --locked`
  - `./gradlew :apps:desktop:run`
  - `./gradlew :apps:desktop:hotRun`
- Web Wasm app (dev server):
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentRun`

The first desktop milestone supports creating a project with `qms init` and
opening/verifying one with `qms verify`. The UI delegates workspace rules to the
native CLI; see the [EQMS-090 design](../architecture/EQMS-090.md).

In **Create project**, use **New folder** to create a named child of the
displayed directory; the new folder is selected for initialization. Invalid
names, existing paths, and permission errors are shown without overwriting.
Cancelling the chooser leaves any folder already created on disk but does not
initialize a workspace. **Open project** selects an existing workspace.

The project screen and directory dialogs are shared Compose Multiplatform
components in `core:ui` (JVM and Wasm targets), without Swing UI. Use **Go** to
browse an entered directory path, **Up** for its parent, or click a child folder
to enter it, then **Select directory**. Desktop filesystem and native CLI access
remain in `apps:desktop`; the browser shell is not wired to these capabilities.
Desktop still requires a JVM, not Kotlin/Native.

## Draft documents

After opening a project, select **New document**, enter a title and plain
Markdown body, and select **Save**. Saved drafts appear in the document list
and can be reopened after restarting. **History** shows saved working revisions,
configured author and local timestamp claims; choose two revisions and
**Compare** for a read-only diff. Unsaved navigation/closing asks before discard.
Failed saves retain editor text. If another process saved first, copy your
edits, refresh/reopen the latest draft, reconcile them, and save again.

Native equivalents (body is UTF-8 stdin, not a shell argument):

```sh
printf '# Quality policy\n' | ./target/release/qms document create ./quality-workspace --title 'Quality policy' --json
./target/release/qms document list ./quality-workspace --json
```

Use the returned document ID and full revision in the command syntax below:

```text
qms document show <project> <id> [--revision <full-revision>]
qms document save <project> <id> --title <title> --expected <full-revision>
qms document history <project> <id>
qms document diff <project> <id> <from-full-revision> <to-full-revision>
```

Save reads the replacement body from stdin. `--json` uses the versioned
`document.<operation>` envelope with a `data` field; errors exit nonzero.
Unchanged canonical content returns the existing revision. Saving never
publishes: each document has an internal private branch, while the baseline and
its derived SQLite index remain unchanged. Do not manually check out or edit
internal draft branches. Backups must include the Git repository and its refs,
not just baseline working files; no remote synchronization is implemented.

Draft transactions publish only a fully verified Git commit through an atomic
ref update. A pre-publication interruption leaves no partial document ref;
unreachable objects are normal Git garbage-collection candidates. If a create
response is lost, inspect the document list before retrying creation. After a
lost save response, reopen the latest revision rather than forcing an old
revision token. No draft-specific repair/reset command is needed.

Review, approval, signatures, publication and reference validation are not yet
available. See the [draft document contract](../architecture/EQMS-023.md).

Authoring checks:

```sh
cargo test --workspace --locked
./gradlew :apps:desktop:test :core:ui:jvmTest :core:ui:compileKotlinWasmJs
```

## Search saved documents

The desktop search box searches only the open project. Results are grouped into
**Documents** (titles), **Authors** (configured author), and **Content** (body
excerpts), with five rows per section and **Show more**. Input is literal text,
not query syntax: matching ignores case, includes word prefixes, treats `&` as
`and`, and tolerates one typo in words of at least four characters. All query
words must match within the group's field. Exact tokens outrank prefixes, which
outrank typo matches. Search accepts at most 512 characters/32 words.

Only if all three groups have no current saved matches does search include
older saved revisions. Historical hits are labeled and open read-only; **Return
to latest draft** resumes editing the current version. Unsaved editor text is
not searchable. Saved drafts are not approvals; configured author/local time
are claims. Search preparation and failures are visible; **Refresh search**
reconciles external changes or retries a failed preparation.

```sh
./target/release/qms search ./quality-workspace --prepare --json
./target/release/qms search ./quality-workspace --query 'And' --json
./target/release/qms search ./quality-workspace --query 'risk managment'
```

Native `search.prepare` returns indexed revision count and a source snapshot.
Native `search` returns grouped hits and each group's nullable `next_offset`.
For later pages pass `--snapshot` from that response and the appropriate
`--documents-offset`, `--authors-offset`, or `--content-offset`. Pages contain
five hits; offsets default to zero. Source changes invalidate pagination.
After external edits, run `--prepare` again. `--prepare --rebuild` rebuilds saved
search data without changing canonical content, draft refs or the baseline index.
Preparation runs standard Git `pack-refs --all` housekeeping to keep large
projects fast: physical ref files may move into packed storage, but every ref
name/revision, HEAD, staged file and history entry is preserved.
If SQLite corruption prevents opening it, move only the disposable
`.eqms/index.sqlite3` and its associated SQLite sidecars aside while no process
is using it, then prepare again. Never remove Git history or canonical files.

The repeatable CLI benchmark generates genuine draft refs and verifies them
through production preparation. Use a **new**, explicitly chosen fixture directory:

```sh
python3 scripts/benchmark_search.py /path/to/new-search-fixture
python3 scripts/benchmark_search.py /path/to/new-search-fixture --reuse
```

Default: 10,000 documents, 10,486 body bytes each (~100 MiB), 100 older revisions,
30 samples and three warmups per query. Output reports hardware, fixture shape,
first-observed/preparation latency, p50/p95 and raw samples. CLI elapsed time is
not desktop query-to-render evidence. See [search design](../architecture/EQMS-050.md)
and [test specification](../testing/EQMS-050.md).

The opt-in Compose benchmark uses that prepared reference fixture and the actual
desktop/native adapter. It measures 20 warmups and 100 final-input-to-layout
samples per current query, including debounce, CLI, decoding and layout:

```sh
EQMS_SEARCH_BENCHMARK_WORKSPACE=/path/to/new-search-fixture \
EQMS_QMS_PATH="$PWD/target/release/qms" \
./gradlew :apps:desktop:test --tests '*DesktopSearchBenchmarkTest' --rerun-tasks
```

The fixture must have 10,000 documents and at least 100 MiB of current body
text. JSON samples are printed into the Gradle test report/XML. The benchmark
isolates warm search by omitting initial full-document list loading; it does not
measure project opening. Without the fixture environment it is explicitly
skipped. Human responsiveness checks remain separate.

## Web module tasks

- Build development webpack bundle:
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack`
- Build browser distribution bundle:
  - `./gradlew :apps:web:wasmJsBrowserDistribution`

## Notes

- Web dev server prints the active localhost URL when started (port may vary if occupied).
- `apps/web/src/wasmJsMain/resources/index.html` is the web entry page and loads `web.js`.
