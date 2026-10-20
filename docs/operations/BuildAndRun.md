---
title: Build and Run
status: active
owner: eQMS
last_updated: 2026-10-20
related:
  - EQMS-001
  - EQMS-004
---

# Build and Run

Run commands from the repository root. The Rust native CLI is the foundation
for future eQMS commands; Kotlin cmd remains the SQLite bootstrap prototype.

## Rust native CLI

Install a stable Rust toolchain with Cargo, rustfmt and Clippy. The tested version
is recorded in [EQMS-004 evidence](../testing/evidence/EQMS-004.md).

```sh
cargo build --workspace --release --locked
./target/release/qms --help
./target/release/qms --version
./target/release/qms --json --version
```

Only help/version are implemented. `qms init` is a future feature, not a working
command. No JVM is required by `qms`.

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

JSON version/error contracts and exit codes are documented in
[the design](../architecture/EQMS-004.md).

## Kotlin prototype prerequisites

- JDK installed and available for Gradle
- Use the project wrapper: `./gradlew`

## Kotlin build

- Build all modules:
  - `./gradlew build`

## Run Kotlin application entrypoints

- Prototype CLI (not native `qms`):
  - `./gradlew :apps:cmd:run`
- Desktop app:
  - `./gradlew :apps:desktop:run`
- Web Wasm app (dev server):
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentRun`

## Web module tasks

- Build development webpack bundle:
  - `./gradlew :apps:web:wasmJsBrowserDevelopmentWebpack`
- Build browser distribution bundle:
  - `./gradlew :apps:web:wasmJsBrowserDistribution`

## Notes

- Web dev server prints the active localhost URL when started (port may vary if occupied).
- `apps/web/src/wasmJsMain/resources/index.html` is the web entry page and loads `web.js`.
