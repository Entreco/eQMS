---
title: Build and Run
status: active
owner: eQMS
last_updated: 2026-10-22
related:
  - EQMS-001
  - EQMS-004
  - EQMS-101
  - EQMS-090
  - EQMS-092
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
- Desktop Jewel UI and UI tests require **JetBrains Runtime 25**. Set
  `JAVA_HOME` to its home directory before launching Gradle (an IDE-bundled JBR
  is suitable if it is version 25). The desktop JavaExec and UI/desktop test
  tasks select vendor JetBrains explicitly; another JDK is not a supported
  fallback. If Gradle cannot discover it, configure
  `org.gradle.java.installations.paths` with its installation path.
- Use the project wrapper: `./gradlew`

## Kotlin build

- Build all modules:
  - `./gradlew build`

## Run Kotlin application entrypoints

- Prototype CLI (not native `qms`):
  - `./gradlew :apps:cmd:run`
  - Prints a placeholder notice only; no SQLite database is created. The
    obsolete Kotlin ledger/search modules and JDBC bootstrap have been removed.
    Existing prototype databases are not deleted or migrated.
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

The Jewel project screen and directory dialogs live in `feature:scaffold/jvmMain`.
Capability interfaces, DTOs, and reusable search state remain in `commonMain`,
with JVM and Wasm targets; Jewel is not available on Wasm. Use **Go** to
browse an entered directory path, **Up** for its parent, or click a child folder
to enter it, then **Select directory**. Desktop filesystem and native CLI access
remain in `apps:desktop`; the browser shell is not wired to these capabilities.
Desktop still requires a JVM, not Kotlin/Native.

## DutchDoxx desktop menus and settings

Desktop branding artwork lives in `apps/desktop/src/main/resources/branding/`.
The desktop module uses Compose Multiplatform generated drawables for About
and window images (`Res.drawable.about` / `Res.drawable.icon`). Gradle's
`stageComposeDrawables` task automatically stages the canonical JPG/PNG into
`build/generated/desktopComposeResources/drawable` before resource generation;
do not edit or commit those generated copies. The module remains Kotlin JVM.
Native taskbar loading and installer assets still use the canonical branding
resources, with no deprecated Compose string-path painter loading.
`logo.png` is the supplied launcher tile, with a dark background and rounded
corners; `logo.svg` is retained as original artwork, not the launcher source.
Generated `icon.png` (1024 square),
`icon.ico` (16-256 pixels) and `icon.icns` (up to 1024 pixels) provide Linux,
Windows and macOS launcher icons respectively. The PNG also supplies the
desktop window/taskbar icon where supported.
Generation preserves the tile background, rounded-corner transparency and aspect
ratio, including the padding supplied within logo.png. It does not trim that
padding. The conversion adds no further outer padding.
The supplied PNG is 164 x 165; it is
centered on a 165-square canvas before resizing. Larger assets are upscaled,
not additional source detail.

About now uses the separate full-width `branding/about.jpg` artwork, not the
launcher PNG. Replace that JPG to update only the About banner; preserve its
intended aspect ratio. About opens in a single independent, non-modal window
with native title bar and close controls. The workspace stays usable; choosing
About again restores/focuses it. Closing About does not close the application.
It follows the selected theme and retains the build version and draft notice.

Normal builds use the committed icons and need no conversion tools. To update
launcher artwork, replace `logo.png`, then regenerate and check the outputs
using Python with Pillow 11.3.0:

```sh
python3 scripts/generate_desktop_icons.py
python3 scripts/generate_desktop_icons.py --check
```

Use an isolated Python environment for this optional tool; Cairo is not needed.
Package each installer on its target OS; configuring icons does not validate
Windows/Linux installation or replace manual launcher/About visual review.

The desktop application is named **DutchDoxx**; the repository, workspace
formats, and native `qms` CLI keep their eQMS names. On macOS the native
main window uses Jewel/JBR unified chrome: native traffic lights share the
title bar with the app/project label and Create/Open/theme controls. The
bar follows the same saved light/dark appearance as the workspace. Drag
unused title-bar space to move the window. This requires the supported JBR;
ordinary JVMs are not a silent fallback for custom decoration. About retains
its separate native title bar; Windows/Linux retain their standard window
decoration and in-content toolbar.

On macOS the native
application menu supplies OS-standard **About** and **Settings/Preferences**
alongside standard system items. **File > New project** uses the existing
creation chooser. **File > Close project** returns to welcome without quitting
or deleting files. Unsaved changes require confirmation; Cancel/Keep editing
preserves edits. Quit/window close remains guarded. File/Help commands are
disabled during conflicting operations or dialogs.

**Edit** and **View** have no custom commands yet. **Help > Check for updates**
checks published stable GitHub releases only when clicked. No release and
network/rate-limit failures are reported explicitly. An available release page
can be opened in the browser; there is no automatic download or installation.
This Help placement is intentional: no native bridge is added to customize the
macOS application menu. Development or unrecognized versions cannot be
automatically classified as up to date.

Settings contains only light/dark appearance. It defaults to dark when unset
and persists in the current OS user's Java Preferences node
`com/entreco/dutchdoxx`; it is not stored in a project. The existing toolbar theme
control uses the same preference. Storage errors are visible and changing theme
does not reopen a workspace or reset the editor/search.

The desktop version defaults to `1.0.0-dev`, independently of the Rust CLI.
Override it with `-PdutchDoxxVersion=1.0.0` for a corresponding release build;
do not label unreleased code as a published release. About and update comparison
use the full version; native bundle metadata uses its numeric portion.

Native packaging requires a **full JBR 25 SDK**, including `jpackage` and
`jmods`; an IDE-bundled runtime may run/test the app but lack packaging tools.
With `JAVA_HOME` pointing to that SDK:

```sh
./gradlew :apps:desktop:createDistributable
./gradlew :apps:desktop:runDistributable
```

On macOS this creates `apps/desktop/build/compose/binaries/main/app/DutchDoxx.app`.
It is a local, unsigned development distribution, not an installer or a
provenance-assured release. It does not bundle `qms`: configure `EQMS_QMS_PATH`
or install the trusted native executable on PATH, as with direct JVM launches.
See [design/risk assessment](../architecture/EQMS-093.md) and
[test specification](../testing/EQMS-093.md). Human native-menu review/testing
is required before merge.

## Source layout

`core/` is Rust-only. `core/rust` currently contains the authoritative domain
modules (`document`, `search`, `workspace`, `index`); this UI restructuring does
not split or rename its Cargo crate.

Kotlin UI modules live in `feature/`:

| Module | Owns |
|---|---|
| `feature:scaffold` | Project shell, theme, navigation/close guards, directory chooser and project capability contracts |
| `feature:document` | Document/lifecycle contracts, multi-tab workspace, editor, save, review, history and comparison |
| `feature:search` | Search contracts/state, query field, grouped results, pagination and layout timing |
| `feature:ui` | Shared Jewel fields, dialogs, rows and focus styling |

Dependencies are one-way: scaffold -> document -> search -> ui, with scaffold
and document also using ui directly. Capability contracts and reusable search
state use `commonMain`; Jewel presentation and desktop editor-buffer state use
`jvmMain`. Each feature retains JVM and Wasm targets.
The desktop injects filesystem/native CLI adapters; web remains a bootstrap,
not a browser implementation of these capabilities. The old `core:ui` Gradle
project and package namespace have been removed.

## Jewel desktop preview

The desktop uses Jewel `0.41.0-262.10968.63` standalone with Compose `1.12.0`
and Kotlin `2.4.10`. These are pinned together; do not update Compose independently
of Jewel's supported dependency matrix. The matching IntelliJ platform icon
artifact supplies component glyphs, including combo-box arrows.

The shell defaults to IntelliJ New UI dark styling. **Light theme** / **Dark
theme** switches the current session without reopening the project or resetting
editor/search state. Existing unified macOS chrome is retained. Open a project
to use left **Project** / **Search** tools, central document tabs and right
**Review** / **History** tools. Click a selected tool again or its hide button
to collapse it. Drag a divider or use the panel's minus/plus controls to resize.
Widths, selected tools and open tabs last for the session only.

The outer rails use icons only: folder (Project), magnifier (Search), check
(Review) and clock (History). Hover an icon for its name and shortcut.
On macOS use **Command+1** Project, **Command+2** Search, **Command+3** Review
and **Command+4** History; on Windows/Linux use **Alt+1..4** in the same order.
Shortcuts work within the document workspace, including title/Markdown/search
fields, and toggle the panel just like clicking. Opening a tool preserves
editor focus; hiding a focused panel moves focus to its rail icon. Only the
exact modifier with a top-row digit matches; holding the key toggles once.
Busy operations and modal dialogs block background toggles. About is a
separate non-modal window: its keys do not toggle workspace tools.
Panel headings remain labelled. Tab through controls and use Enter to activate.

Tool and editor surfaces have rounded corners. Drag either 8dp gutter between
a tool panel and the editor horizontally; the pointer changes to a resize
cursor. Both sides preserve a usable editor width, and disabled/busy panels
cannot be dragged. The minus/plus controls remain available for keyboard users.
Hover hints open inward beside their icon, aligned with its vertical center.
Editor tabs use a Markdown icon, a quiet inactive label, a rounded selected
surface and an integrated close icon. Unsaved tabs retain a separate `*`
marker; saved historical revisions retain their read-only qualifier.
The tab strip scrolls horizontally and reveals the selected tab automatically.

Each latest-document tab keeps its own unsaved buffer. Switching tabs or tools
does not discard edits; selecting an already-open document focuses its tab.
Exact historical revisions open in separate read-only tabs. Closing dirty tabs
offers **Save**, **Discard changes**, or **Cancel**. Project switching/closing
and quit coordinate all dirty tabs with **Save all** / **Discard all** when
needed. Failed or invalid saves retain text and stop navigation; earlier
successful saves in Save all remain saved. Cancelling the directory chooser or
failing workspace verification leaves the previous buffers intact. A successful
target open still requires the dirty-buffer decision before replacing them.

[Dark](../testing/evidence/EQMS-092-dark.png),
[light](../testing/evidence/EQMS-092-light.png), and
[history](../testing/evidence/EQMS-092-history.png), and
[comparison](../testing/evidence/EQMS-092-diff.png) previews show synthetic
drafts rendered with the real native adapters at the earlier scaffold revision.
Those are historical previews, not evidence for the lifecycle UI.
See the [Jewel assessment/design](../architecture/EQMS-092.md) and
[workspace follow-up evidence](../testing/evidence/EQMS-092-document-workspace.md).
The [icon-rail follow-up evidence](../testing/evidence/EQMS-092-tool-rail-icons.md)
records the compact rails, hover tooltips and shortcut checks separately.

To regenerate captures without touching existing workspaces, choose an output
directory outside the repository; the opt-in test creates a fresh workspace
with synthetic drafts and repository-local synthetic Git identity:

```sh
cargo build --workspace --release --locked
EQMS_JEWEL_PREVIEW_DIR="<absolute-output-directory>" \
EQMS_QMS_PATH="$PWD/target/release/qms" \
./gradlew :apps:desktop:test --tests '*DesktopJewelPreviewTest' --rerun-tasks
```

The test retains its generated `preview-*/jewel-preview` workspace under that
directory, which can then be selected with **Open project** in the actual app.
Captures are Compose content only, not OS window chrome or human visual-review
evidence. Ordinary tests skip this capture when no output directory is supplied.

## Draft documents

After opening a project, select **New document**, enter a title and plain
Markdown body, and select **Save**. Saved drafts appear in the document list
and can be reopened after restarting. The right **History** tool shows saved
revisions and lifecycle events; choose two revisions and **Compare** for an
exact read-only diff. Lifecycle-only revisions have no content difference.
**Open saved revision** opens a separate read-only tab.
Failed saves retain editor text. After an external update, preserve/reconcile
your edits before using **Reload saved document**; dirty reload requires a
Save/Discard/Cancel decision and never silently replaces text.

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

The right **Review** tool exposes **Submit for review**, **Approve** and
**Publish**, each with confirmation on a clean saved latest document. Submission
makes content read-only; return to draft/rework is not available. **Publish**
sets internal **Effective** status only: no controlled-baseline merge, immutable
release record or external distribution. Effective documents can be marked
**Obsolete** or **Superseded**; those states are terminal. Historical tabs
cannot transition. Failed/stale transitions retain the observed state and
require explicit reload/reconciliation, not a forced overwrite.

Approval is a local lifecycle event, not signing or proof of reviewer authority.
Configured identity/local time remain unauthenticated claims. Signatures,
reference validation, comments and reviewer assignments remain unavailable.
See the [draft contract](../architecture/EQMS-023.md) and
[lifecycle usage](EQMS-021.md).

Authoring checks:

```sh
cargo test --workspace --locked
./gradlew :apps:desktop:test :feature:scaffold:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:ui:jvmTest
./gradlew :feature:scaffold:compileKotlinWasmJs :feature:document:compileKotlinWasmJs :feature:search:compileKotlinWasmJs :feature:ui:compileKotlinWasmJs
```

## Verify recorded ledger history

```sh
./target/release/qms audit verify-ledger ./quality-workspace --json
./target/release/qms audit verify-ledger ./quality-workspace --checkpoint <full-trusted-baseline-commit>
```

The checkpoint argument is a complete lowercase commit ID obtained from an
independently trusted source, not a branch or Git expression. The command checks
chronological baseline journal prefixes/content bindings and all discovered
saved draft histories separately. A draft's creation baseline must belong to
verified baseline ancestry. No checkpoint means local consistency only.
Neither mode proves identity, trusted time, approval, compliance or completeness
of draft refs: deleted drafts require an independent inventory.

Only complete local linear baseline histories are supported; partial/promisor
repositories, merges, shallow clones, replacement refs, grafts and
history-override environment variables are rejected explicitly. Shared Git
readers disable lazy fetching, so missing local objects fail without downloading.
A read-only ref/object connectivity check rejects detectable broken/dangling
refs rather than silently excluding them; corrupt unrelated refs also fail
this conservative check. Baseline reads batch tree/blob data and cache verified
content hashes by object ID while still checking each commit's paths/modes.
Verification uses the existing workspace lock and does not repair
files/history or create an index. Existing `qms verify` remains the current
baseline check; `qms recover` remains a separate explicit recovery action.
Verification errors exit 1 with segment context; usage errors exit 2.
JSON uses command `audit.verify-ledger` in the schema-version-2 envelope.
See [EQMS-031](../architecture/EQMS-031.md) for report fields and limitations.

## Validate and format controlled Markdown

These standalone operations accept the existing v1 Draft format, not arbitrary
Markdown/YAML documents. No workspace or configured Git identity is required:

```sh
./target/release/qms document validate 'policy.md' --json
./target/release/qms document format 'policy.md' > 'policy.canonical.md'
./target/release/qms document validate 'policy.canonical.md'
cat 'policy.canonical.md' | ./target/release/qms document format - --json
```

Never redirect formatter output onto the input file: the shell can truncate it
before the command reads it. The CLI only reads files; it does not save a
revision, change refs/journals, or create a workspace.

Validation is strict: metadata and complete bytes must already be canonical.
Formatting accepts supported v1 JSON whitespace/key order and line-ending
differences; it emits fixed-order compact JSON, trims the title and normalizes
line endings to LF. Other body whitespace and final newline presence are
preserved. Human formatting writes exact content without an extra newline.
JSON responses use the existing schema-version-2 envelope; `data` includes ID,
document schema version, `canonical: true` and `content_hash`. Formatting adds
the exact canonical text as `content`.

Malformed UTF-8/metadata, unsupported fields/versions/statuses, noncanonical
validation and I/O failures exit 1 (JSON `OPERATION_FAILED` on stderr);
usage errors exit 2. A format hash describes canonical bytes, not verified
audit history, identity or approval. Documents remain Draft. Import of this
project's existing Markdown belongs to EQMS-016; generalized ledger verification
belongs to EQMS-031. See [the v1 format contract](../architecture/EQMS-020.md).

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
to latest document** focuses/opens the latest tab without replacing its unsaved
buffer. Content is editable only if its observed state is Draft. Unsaved editor
text is not searchable. Search hits do not prove approval; configured author/local
time are claims. Search preparation and failures are visible; **Refresh search**
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
