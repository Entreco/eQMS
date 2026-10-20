---
title: Build and Run
status: active
owner: eQMS
last_updated: 2026-10-20
related:
  - EQMS-001
  - EQMS-004
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

JSON command/error contracts and exit codes are documented in the
[native foundation design](../architecture/EQMS-004.md) and the
[workspace/index contract](../architecture/EQMS-005.md).

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

## Web module tasks

- Build development webpack bundle:
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack`
- Build browser distribution bundle:
  - `./gradlew :apps:web:wasmJsBrowserDistribution`

## Notes

- Web dev server prints the active localhost URL when started (port may vary if occupied).
- `apps/web/src/wasmJsMain/resources/index.html` is the web entry page and loads `web.js`.
