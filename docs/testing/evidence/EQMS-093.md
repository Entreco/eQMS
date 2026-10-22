---
title: EQMS-093 DutchDox menu evidence
status: draft
related: [EQMS-093]
---

# Observed automated evidence

## Launcher and About artwork increment

Issue #52, R093-08/T093-07. Tested implementation:
`194c0f2de33ba9a796f408826aea77d8d0013444`.
Artwork design, requirement and test mapping preceded implementation in
`9f60f84`. Evidence is committed separately. The supplied SVG is preserved in
`apps/desktop/src/main/resources/branding/logo.svg`.

Using the same macOS/JBR environment recorded below:

```sh
python3 scripts/generate_desktop_icons.py --check
./gradlew :apps:desktop:test :feature:scaffold:jvmTest \
  :feature:scaffold:compileKotlinWasmJs :apps:web:compileKotlinWasmJs \
  --rerun-tasks --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

All commands exited 0. Generation used an isolated Python 3.9 environment with
CairoSVG 2.8.2 and Pillow 11.3.0, with Homebrew Cairo located using
`DYLD_FALLBACK_LIBRARY_PATH=/opt/homebrew/lib`. Initial converter discovery
failed because CairoSVG was not installed and then because the native Cairo
library was not on the loader search path. Isolated optional tools and the
documented library path resolved these failures; no runtime/build conversion
dependency was added.

- Desktop: 36 tests, 34 passed, two existing opt-in skips, no failures/errors.
  Scaffold: 16 tests passed. Both requested Wasm compilation targets passed.
- `DesktopLogoTest` decoded the real packaged PNG, verified square 1024-pixel
  dimensions, transparent corners and the exact three SVG layer colors.
  It rendered the real About dialog in both themes, asserted accessible logo
  and version visibility, and checked captured logo pixels for the orange layer.
- `DesktopNativeMenuTest` installed the actual window icon and exercised
  taskbar icon installation where supported. The initial test wrongly expected
  the source's 1024-pixel size in the AWT icon; Compose rasterizes it for the
  host (192 pixels here). The final test checks positive square dimensions;
  source dimensions/colors are covered separately by `DesktopLogoTest`.
- Icon generation was repeated with byte-for-byte equality against all three
  committed assets. Pillow decoded the PNG, all seven ICO frames
  (16/24/32/48/64/128/256), and all eight ICNS entries through 1024 pixels.
  All frames have transparent corners. The frame-inspection command emitted
  a Pillow deprecation warning for its ICNS size-selection API, not a failure;
  the generation script does not use that API.
- Native distribution configuration points macOS/Windows/Linux at their
  respective ICNS/ICO/PNG assets. The macOS bundle's `CFBundleIconFile` is
  `DutchDoxx.icns`; the embedded icon matches the committed ICNS exactly.
- The packaged application started with its bundled JBR and a responsive idle
  AWT event thread; identity remained DutchDoxx. The agent stopped this process
  after inspection.

Windows/Linux launcher installation and display were not executed on this
macOS host; generated/decoded assets and build configuration are not a claim
of target-host execution. Existing native visual, accessibility, signing and
manual-testing limitations remain. The two skipped tests are the same opt-in
benchmark/preview tests identified below; neither is passing logo coverage.

- [ ] Human checks launcher/Dock/taskbar appearance on macOS, Windows and Linux.
- [ ] Human checks About artwork proportions, both themes and accessibility.

## Product-name correction

The user corrected the product name to **DutchDoxx** (double x).
Correction revision: `69acfcde88ce9b76218e0fb7635de57a1e137924`.
Earlier observations below retain the historical DutchDox spelling and tested
revision; they have not been rewritten as new evidence.

At the correction revision these commands passed:

```sh
./gradlew :apps:desktop:test :feature:scaffold:jvmTest \
  :apps:desktop:hotRunArgfile --rerun-tasks --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
```

Same macOS/JBR environment as below. Desktop: 32 passed, 2 existing opt-in
skips; scaffold: 16 passed; zero failures/errors. The actual AWT window title
and About tests now assert DutchDoxx. Generated bundle
`apps/desktop/build/compose/binaries/main/app/DutchDoxx.app` reports
`CFBundleName=DutchDoxx`, `CFBundleIdentifier=com.entreco.dutchdoxx`.
Build resource, version property (`dutchDoxxVersion`), HTTP user agent and
preference node (`com/entreco/dutchdoxx`) use the corrected name.
No preference migration is supplied for the unreleased misspelling.
Native OS visual inspection/manual testing limitations remain unchanged.
Logo integration is not included; source artwork can be added under
`apps/desktop/src/main/resources/branding/` for a subsequent change.

Issue: #52. Tested implementation revision:
`7da29103b25073fab44f418a776a60508cf118af`.
This evidence is committed separately after testing that revision.
Design/requirements/test specification were committed before implementation in
`db819e1`. Base was fetched main at `158aa88ce762ca0dc5dfe1785c54a7242ace14e7`,
which includes dependency PR #51. Its upstream manual checklist is checked;
this agent did not execute those human checks.

## Environment

- macOS 26.6.2, arm64.
- Gradle wrapper 9.6.1.
- JetBrains Runtime 25.0.3, build `25.0.3+-15898627-b508.16`.
- Normal JVM/UI tests and launches used the installed IDE JBR. Packaging used
  the matching full JBR SDK because the installed runtime lacks `jpackage`.
- Desktop version: default `1.0.0-dev`; native package numeric version `1.0.0`.

The packaging SDK was downloaded from JetBrains' official
[`jbrsdk-25.0.3-osx-aarch64-b508.16.tar.gz`](https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.3-osx-aarch64-b508.16.tar.gz).
Its SHA-512 was compared successfully with the adjacent official `.checksum`:

```text
107246457e5e0af04f7511b1a2d11fc307a9f5ff51804102553d14dacfd544c8d6e602946447d23219b91571f0d23c92283659bebbf1354c5c17d62851de5999
```

No SDK, personal installation paths, credentials, or raw host logs are committed.

## Reproduction and results

From the repository root, use the installed supported JBR as `JAVA_HOME`:

```sh
./gradlew :feature:scaffold:jvmTest :apps:desktop:test \
  :feature:document:jvmTest :feature:search:jvmTest \
  :feature:ui:compileKotlinWasmJs :feature:search:compileKotlinWasmJs \
  :feature:document:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs \
  :apps:web:compileKotlinWasmJs :apps:desktop:hotRunArgfile \
  --rerun-tasks --console=plain --quiet
```

Exit 0 at the tested revision. Parsed JUnit XML totals:

| Suite | Tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| feature:scaffold | 16 | 16 | 0 | 0 |
| apps:desktop | 34 | 32 | 2 | 0 |
| feature:document | 9 | 9 | 0 | 0 |
| feature:search | 8 | 8 | 0 | 0 |
| Total | 67 | 65 | 2 | 0 |

All five requested Wasm compilation targets passed. Nonfatal Skiko restricted
native-access warnings were emitted; they were not test failures.

| Mapping | Observed coverage |
|---|---|
| T093-01, T093-05 | `DesktopNativeMenuTest` passed with an actual Compose/AWT window: DutchDox title, File/Edit/View/Help order, New/Close labels and initial enabled state, empty Edit/View, Help update label, and public native About/Preferences/Quit support/handler installation. Generated About version test passed. This does not inspect the OS application's own menu labels. |
| T093-02 | Scaffold menu dispatch tests passed: New uses creation chooser, Close warns on dirty edits, Keep editing preserves text, discard returns welcome without quitting, no-project close ignored, modal/busy competing requests rejected. Existing application-close/navigation guard regressions passed. |
| T093-03 | Default/light/dark, invalid preferences, denied access, flush failure, and synchronization/error presentation passed. An isolated real preference node was written through the adapter, read successfully by a second JVM, and removed in cleanup. Settings theme changes preserved dirty editor/search state. |
| T093-04, T093-05 | Seven desktop adapter tests cover numeric version ordering, v/build metadata, unknown/dev/prerelease comparison limits, no release, HTTP/rate-limit/offline/timeout failures, malformed/unpublished metadata, mismatched/unsafe URLs, browser failure and byte-limit cancellation. Five scaffold chrome tests include duplicate suppression, asynchronous check cancellation/retry and error dialogs. HTTP fixtures were injected; no live release query or updater installation is claimed. |
| T093-06 | Existing desktop/scaffold/document/search regressions and feature/web Wasm compilation passed. `git diff origin/main -- core/` was empty. No Rust implementation or domain behavior changed. |

The skipped tests were the existing opt-in
`DesktopSearchBenchmarkTest.preparedReferenceWorkspaceFinalInputToLayout` and
`DesktopJewelPreviewTest.realNativeWorkspaceRendersInBothThemes`. Their
benchmark/preview environment variables were deliberately not supplied.
They are not counted as passing coverage. Search algorithms and preview
capture behavior were not modified; this increment does not claim new
performance measurements or native-menu screenshots from those tests.
Rust tests were excluded because Rust/Cargo were unchanged.

## Packaging and launch observations

Set `JBR_SDK` to the full SDK home, then:

```sh
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
```

Exit 0. Inspected generated
`apps/desktop/build/compose/binaries/main/app/DutchDox.app/Contents/Info.plist`:
`CFBundleName=DutchDox`, `CFBundleIdentifier=com.entreco.dutchdox`,
`CFBundleShortVersionString=1.0.0`. Launcher configuration includes
`apple.awt.application.name=DutchDox`, `apple.laf.useScreenMenuBar=true` and
`-Xdock:name=DutchDox`. The app includes a JBR runtime with HTTP/preferences
modules, but does not bundle the native `qms` executable.

At the tested revision the following launches started successfully:

```sh
./gradlew :apps:desktop:run --console=plain --quiet
./gradlew :apps:desktop:hotRun --console=plain --quiet
EQMS_QMS_PATH="$PWD/target/release/qms" \
  apps/desktop/build/compose/binaries/main/app/DutchDox.app/Contents/MacOS/DutchDox
```

Each launch was observed as a running desktop JVM. `jcmd` confirmed DutchDox
application identity and native screen-menu properties, and live idle AWT event
threads without startup exceptions. The generated hotRun argument file also
contains explicit startup identity/dock options: hotRun requires its own
JavaExec configuration rather than relying on main setting properties after
hot-reload startup. Agent-started launch processes were stopped after inspection.

## Failures, exclusions and limits

- Initial compilation identified a missing MenuBar extension import and,
  during timeout hardening, an inference error. Both were fixed before the
  tested implementation commit; final revision-specific compilation passed.
- Initial packaging failed because the IDE runtime lacked `jpackage`.
  Restoring the checksum-verified matching JBR SDK resolved it; final bundle
  creation and launch passed.
- OS-level Apple Events inspection failed with
  `Not authorized to send Apple events to System Events (-1743)`.
  No permission bypass was attempted. Actual OS application-menu labeling,
  visual placement, keyboard/accessibility behavior, and native Quit gestures
  still require human verification. AWT menu assertions and JVM properties are
  supporting observations, not substitutes for those checks.
- No Windows/Linux execution, DMG installer, signing/notarization, screen-reader
  inspection, live GitHub release availability, or human testing is claimed.
- The app bundle is an unsigned development artifact, not a validated release.
  Draft records are not approved compliance evidence.

## Required manual review/testing

- [ ] Review design/risk, requirements, test mappings, source and evidence.
- [ ] Confirm DutchDox, not MainKt, in the macOS application menu for run,
  hotRun and bundle; inspect native About/Settings/system items and File/Edit/
  View/Help menus, labels, separators and enabled states.
- [ ] Exercise New/Close project, chooser cancellation, errors, busy states,
  dirty Keep editing/discard, native Quit and window-close protection.
- [ ] Confirm Settings/toolbar appearance synchronization and persistence
  across actual application launches without editor/search loss.
- [ ] Inspect About and on-demand update/error/browser flows and verify that
  no background checking or installation occurs.
- [ ] Review both themes, keyboard focus, accessibility, and target platforms.

Keep issue #52 open/In progress and the PR draft until human review/testing.
