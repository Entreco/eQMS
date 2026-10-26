---
title: EQMS-093 DutchDox menu evidence
status: draft
related: [EQMS-093]
---

# Observed automated evidence

## Typed Compose Multiplatform desktop resources

Issue #52, R093-11/T093-10 (also R093-08/09).
Final tested revision: `a57ab3b117168e08fd7778e0650409943ca76888`;
implementation `8268928`, native-window rendered-state test follow-up `a57ab3b`.
Design/requirements/tests preceded implementation in `b24bd52`. This evidence
is committed separately; human approval is not implied.

Environment is the same macOS 26.6.2 arm64 / JBR 25.0.3 b508.16 / Python
Pillow 11.3.0 environment described below, using the pinned Compose plugin and
resources library 1.12.0. The desktop module remains Kotlin JVM. The full SDK
was restored from the official archive and SHA-512 verified as documented
below after the IDE runtime again failed packaging due to missing jpackage.

All three deprecated desktop production/test painter calls now use generated
`Res.drawable.about` or `Res.drawable.icon` with
`org.jetbrains.compose.resources.painterResource`.
Canonical branding resources, native package icon paths, taskbar ImageIO
loading and icon generation are unchanged. Only the two runtime images are
staged automatically through a provider-wired Sync task.

Observed successful commands from the repository root:

```sh
./gradlew :apps:desktop:test :feature:scaffold:jvmTest \
  :apps:desktop:hotRunArgfile :feature:scaffold:compileKotlinWasmJs \
  :apps:web:compileKotlinWasmJs --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/about.jpg \
  apps/desktop/build/generated/desktopComposeResources/drawable/about.jpg
cmp apps/desktop/src/main/resources/branding/icon.png \
  apps/desktop/build/generated/desktopComposeResources/drawable/icon.png
python3 scripts/generate_desktop_icons.py --check
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

Desktop: 39 passed, 0 failures/errors, 2 existing opt-in benchmark/preview
skips. Scaffold: 18 passed, 0 failures/errors/skips. Total: 57 passed, 2 skipped.
`DesktopLogoTest.generatedDrawablesMatchCanonicalBytesAndDecodeWithOriginalDimensions`
compares drawable bytes through the supported generated-resource API against
canonical classpath bytes, decodes both typed drawables, checks dimensions and
launcher corner alpha. Existing tests cover About proportions/accessibility/
both-theme rendering, native menu/window icons and About lifecycle.
Production-host and native Robot interaction regressions also passed.

Scaffold and web Wasm compilation and hotRun argument/resource preparation
passed. A static search over desktop Kotlin sources found no deprecated
`androidx.compose.ui.res.painterResource` import or string-path painter call.
Document/search/Rust suites were not rerun: this increment changes only desktop
resource loading and its build wiring, not feature/domain behavior.

At the final revision, remove only
`apps/desktop/build/generated/desktopComposeResources`, then run:

```sh
./gradlew :apps:desktop:processResources --console=plain --quiet
```

This was executed after inspecting that exact task-owned directory. It
automatically regenerated both drawables with no manual staging task invocation;
both cmp commands above passed again. The initial full desktop suite also
started with this directory absent.

Packaging verified both generated drawable entries inside the bundled desktop
application JAR byte-for-byte against canonical branding:

```sh
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile
root = Path("apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/app")
expected = {n: Path("apps/desktop/src/main/resources/branding", n).read_bytes()
            for n in ("about.jpg", "icon.png")}
found = set()
for jar in root.glob("*.jar"):
    with ZipFile(jar) as z:
        for name in z.namelist():
            if "composeResources/com.entreco.eqms.desktop.generated.resources/drawable/" in name:
                asset = name.rsplit("/", 1)[-1]
                if asset in expected:
                    assert z.read(name) == expected[asset]
                    found.add(asset)
assert found == set(expected), found
print("Bundled drawables match canonical branding.")
PY
```

The macOS executable launched with bundled JBR and the generated window icon;
startup reported no resource-loading errors. Process inspection and successful
`jcmd <app-pid> Thread.print` showed an idle AWT event thread waiting for events.
The agent stopped only its launched process. About rendering was exercised by
desktop tests; bundled About bytes were verified, but opening About in the
packaged app was not separately automated.

Resolved failure: the first full run at `8268928` exposed a pre-existing native
host test race: appearance state changed before the new label was recomposed.
The test now also awaits the rendered label using its existing helper. This
does not suppress errors or change application behavior. Final results above
include that corrected test. Restricted-native-access messages remain warnings.

Limitations: Windows/Linux host execution, full clean-checkout rebuild, live
hotRun session and human visual/accessibility/manual testing were not executed.
Missing-staging regeneration, hotRun preparation and macOS packaged execution
are separate observed checks, not claims of those excluded scenarios.

- [ ] Human reviews About/window artwork and icons in run/hotRun/bundle.
- [ ] Human verifies supported Windows/Linux host behavior.
- [ ] Human reviews resource wiring, risk/requirements/tests and manual results.

Keep #52 open/In progress and #53 draft pending manual review/testing and merge.

## Unified macOS chrome and corrected launcher corners

Issue #52; R093-01/03/04/07/08/10, T093-06/07/09.
Tested implementation revision:
`277b483aaf9f89476a995856072b044fa433b113`.
Scope, risk, requirements and tests preceded implementation in `515a5c2`.
This evidence is committed separately. Records remain drafts, not approvals.

Environment: macOS 26.6.2 arm64, JBR 25.0.3 b508.16, existing Gradle wrapper
and pinned Jewel 0.41.0-262.10968.63; Python 3 with Pillow 11.3.0.
The full SDK was restored from the official JetBrains archive linked below
and verified against its published SHA-512:
`107246457e5e0af04f7511b1a2d11fc307a9f5ff51804102553d14dacfd544c8d6e602946447d23219b91571f0d23c92283659bebbf1354c5c17d62851de5999`.
No SDK or personal paths are committed.

Commands from the repository root, all exit 0 at the tested revision:

```sh
python3 scripts/generate_desktop_icons.py --check
EQMS_CHROME_PREVIEW_DIR="$EVIDENCE_DIR" ./gradlew \
  :apps:desktop:test :feature:scaffold:jvmTest \
  :feature:document:jvmTest :feature:search:jvmTest \
  :feature:scaffold:compileKotlinWasmJs \
  :feature:document:compileKotlinWasmJs \
  :feature:search:compileKotlinWasmJs \
  :feature:ui:compileKotlinWasmJs :apps:web:compileKotlinWasmJs \
  --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

`EVIDENCE_DIR` is an isolated writable capture directory, not a project workspace;
omit the environment variable to run without screenshot output.
`JBR_SDK` identifies the full supported SDK home.

| Suite | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| apps:desktop | 38 | 0 | 2 |
| feature:scaffold | 18 | 0 | 0 |
| feature:document | 9 | 0 | 0 |
| feature:search | 8 | 0 | 0 |
| Total | 73 | 0 | 2 |

The skipped desktop tests are the existing opt-in benchmark and real-native
workspace preview, not chrome/icon coverage. The new real-window and native
mouse tests both executed. Affected Wasm compilation passed. Rust was not
changed; Cargo tests were excluded because no Rust/domain behavior changed.

Observed results:

- The updated 164 x 165 RGBA source was preserved without trimming, redrawing
  corners or additional rounding. Generated PNG pixels exactly match centered,
  aspect-preserving Lanczos conversion; seven ICO sizes and all eight ICNS
  entries decoded with transparent corners. Reproducible generation and bundled
  ICNS byte comparison passed. About's JPG is unchanged.
- `DesktopWindowTest` uses the production window host and scaffold: one header,
  initial light preference, dark/light changes, matching title-bar style colors,
  same window instance and retained open workspace, native menu installation,
  960 x 720 minimum size, label clearance and the last action fitting at minimum
  width, and routed window-close callback. Synthetic semantics invoke workflow
  actions; they are not a claim of OS-level pointer testing.
- `DesktopWindowNativeTest` launches a regular Compose application outside the
  synthetic Compose test dispatcher. A real Robot mouse click on the title-bar
  theme action changes appearance. A multi-event drag in unused title-bar space
  changes native window location. Programmatic minimize/restore also completes.
  The test disposes its window; it uses no user workspace or preferences.
- The scaffold dirty editor/search regression now uses the same DesktopChrome
  appearance owner as outer window styling. Theme changes preserve unsaved
  text/query/preparation state and dirty-close protection. Existing Settings
  persistence/error, menu/busy/close, and independent About lifecycle tests pass.
- Robot captures `unified-light.png` and `unified-dark.png` were generated and
  agent-inspected using synthetic content: native traffic lights, a single
  unified bar, no second pale strip in dark mode, and visible non-overlapping
  controls at minimum width. Captures are session artifacts, reproducible by
  the command above, not approved human visual evidence.
- The unsigned macOS app bundle was created with the full SDK; Info.plist names
  `DutchDoxx.icns`, identical to the regenerated asset. Its executable launched
  with bundled JBR and loaded Jewel/JNA. Process inspection and a successful
  `jcmd <app-pid> Thread.print` showed an idle AWT event thread waiting for events,
  not a blocked UI stack. The agent stopped only the process it launched.

Reproduce the additional source/frame checks with Pillow:

```sh
python3 - <<'PY'
from pathlib import Path
from PIL import Image
p = Path("apps/desktop/src/main/resources/branding")
source = Image.open(p / "logo.png").convert("RGBA")
side = max(source.size)
expected = Image.new("RGBA", (side, side))
expected.alpha_composite(source, ((side-source.width)//2, (side-source.height)//2))
expected = expected.resize((1024, 1024), Image.Resampling.LANCZOS)
assert expected.tobytes() == Image.open(p / "icon.png").convert("RGBA").tobytes()
ico = Image.open(p / "icon.ico")
assert ico.ico.sizes() == {(n, n) for n in (16, 24, 32, 48, 64, 128, 256)}
for size in ico.ico.sizes():
    assert ico.ico.getimage(size).convert("RGBA").getpixel((0, 0))[3] == 0
icns = Image.open(p / "icon.icns")
assert len(icns.icns.itersizes()) == 8
for size in icns.icns.itersizes():
    assert icns.icns.getimage(size).convert("RGBA").getpixel((0, 0))[3] == 0
print("Source-derived PNG and all ICO/ICNS frames passed.")
PY
```

Failures encountered and resolved before the final revision: initial integration
compile errors were corrected against the pinned theme overload/layout API.
A full-width title-bar child overlapped traffic lights and clipped actions:
pinned Jewel measures children before adding native insets. Separate bounded
leading title/trailing actions plus a leading gutter corrected the actual
geometry. Compose test pointer injection into a separate native window failed
its EDT check; synthetic workflow actions now use semantics, and real mouse
coverage runs in a normal application. A single-move Robot drag did not move the
window; a multi-event native gesture did. Initial packaging failed because the
IDE runtime lacks jpackage; restoring the checksum-verified full SDK resolved it.
Existing restricted-native-access warnings remain warnings, not test failures.

Windows/Linux host launch/installation, fullscreen/zoom and traffic-light-button
interaction, inactive appearance, keyboard/accessibility, hotRun-specific visual
inspection and human review/testing were not executed in this increment.
Non-macOS retains its existing standard-window branch but has no target-host
execution claim. Earlier artwork review checkboxes do not approve the newly
supplied PNG or unified chrome.

- [ ] Human reviews unified macOS chrome in both themes and inactive state.
- [ ] Human tests controls, dragging, resize, minimize/zoom/fullscreen, menus,
  keyboard/accessibility and unsaved close cancellation in run/hotRun/bundle.
- [ ] Human reviews the corrected launcher corners on supported target hosts.
- [ ] Human completes implementation/risk review and manual testing before merge.

Keep #52 open/In progress and PR #53 draft pending these gates.

## Supplied launcher padding refresh

Tested revision `a3d55968f15eaa79a1c51310b6f1df6024acaedb` regenerates all three
launcher assets from the user's updated, padded logo.png without trimming it.
Source dimensions remain 164 x 165. About artwork is unchanged.

In the same environment as below, these commands exited 0:

```sh
python3 scripts/generate_desktop_icons.py --check
./gradlew :apps:desktop:test --tests '*DesktopLogoTest' \
  --tests '*DesktopNativeMenuTest' --rerun-tasks --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

Five selected tests passed, zero failures/errors/skips. The original background
sample points fell within the newly supplied transparent padding, causing an
initial assertion failure; samples now check inside the tile instead.
Reproducible asset equality and macOS bundled icon equality passed.
Windows/Linux target-host and manual visual review limitations remain unchanged.

## Dark-tile PNG launcher correction

Issue #52, R093-08/T093-07. Tested revision:
`547c8dff5859ee8747a017aebed9fe6e3eaf709e`.
Updated source/design/test mapping preceded implementation in `3aa2d2b`.
The launcher source is now the supplied `branding/logo.png`, not logo.svg.
About continues to use the unchanged about.jpg.

At this revision, in the same macOS/JBR environment described below:

```sh
python3 scripts/generate_desktop_icons.py --check
./gradlew :apps:desktop:test --tests '*DesktopLogoTest' \
  --tests '*DesktopNativeMenuTest' --rerun-tasks --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

All exited 0. Five selected tests passed; no failures/errors/skips.
Tests cover actual About banner/version rendering, source JPEG, tiled launcher
background/rounded-corner transparency and native window/taskbar installation.
Generation requires only Pillow 11.3.0, not CairoSVG/native Cairo.

Additional pixel/frame inspection verified:

- Source PNG is 164 x 165 with alpha; the dark tile itself is opaque, with alpha
  retained at its rounded corners. Centering on a 165-square canvas adds only
  the unavoidable one-column difference; no previous 10% padding is retained.
- Generated 1024-square PNG pixels exactly match source composition and Lanczos
  resizing. Larger sizes are upscaled; no new high-resolution detail is claimed.
- All seven ICO frames (16/24/32/48/64/128/256) and eight ICNS entries through
  1024 decoded and retained opaque dark tile pixels. The inspection API emitted
  the same Pillow ICNS size-selection deprecation warning noted below, not a
  generation or decoding failure.
- The macOS bundle contains the corrected ICNS byte-for-byte.

Only affected logo/native-menu tests and bundle generation were rerun; domain
logic, About window behavior and Wasm sources were not changed. Earlier full
suite observations remain historical evidence, not new full-suite results.
Windows/Linux installer execution and human visual review remain pending.

- [ ] Human reviews the new tiled icon in macOS/Windows/Linux launchers.

## Independent About window and full banner

Issue #52, R093-09/T093-08. Final tested revision:
`3e7278dbb6205932049168e22cce00f95594aff8`
(implementation `398258d`, stable test fixture follow-up `3e7278d`).
Design/requirements/tests preceded implementation in `60f178c`.
The supplied `branding/about.jpg` (564 x 165) is used only for About;
SVG-derived launcher icons remain unchanged.

Commands, in the same macOS/JBR environment as below:

```sh
./gradlew :apps:desktop:test :feature:scaffold:jvmTest \
  :feature:scaffold:compileKotlinWasmJs :apps:web:compileKotlinWasmJs \
  --rerun-tasks --console=plain --quiet
JAVA_HOME="$JBR_SDK" ./gradlew :apps:desktop:createDistributable \
  --no-daemon --console=plain --quiet
cmp apps/desktop/src/main/resources/branding/icon.icns \
  apps/desktop/build/compose/binaries/main/app/DutchDoxx.app/Contents/Resources/DutchDoxx.icns
```

All exited 0 at the final revision. Desktop: 38 tests, 36 passed, two existing
opt-in skips; scaffold: 18 passed; zero failures/errors. The two requested
Wasm targets compiled and macOS bundle creation passed. ZIP inspection verified
that the packaged app JAR's `branding/about.jpg` exactly matches the supplied
file. The bundled launcher ICNS still matches the existing asset.

Observed automated coverage:

- Actual visible AWT About frame has title `About DutchDoxx` and native
  decoration (`isUndecorated=false`); no modal Dialog is introduced.
- Repeated About requests reuse the same frame. Minimizing and requesting
  About restores it. Native WINDOW_CLOSING disposes that frame alone;
  reopening creates a new single frame and Close disposes it.
- Main workspace remains enabled, theme control works while About is open,
  project state survives About closure, and editing while About is open retains
  the unsaved application-exit guard. Keep editing does not exit the application.
- Real About-window semantics show accessible artwork, version and draft
  notice in both themes. Separate direct rendering of the same DesktopLogo
  composable verifies the banner's 564:165 aspect ratio and orange artwork
  pixels; source JPEG decoding verifies dimensions.

Investigation limitations and resolved failures:

- The test runner's simulated pointer injection into a real native window
  hit a Swing EDT assertion. The Close-action check now dispatches its semantic
  OnClick action; native close is independently tested with WINDOW_CLOSING.
- Compose's test-scene screenshot capture of a separate native window did not
  reliably contain its rendered pixels. Native-window semantics/decoration
  and standalone artwork pixel rendering are tested separately rather than
  claiming those screenshots prove OS-visible appearance.
- A minimize/restore check exposed asynchronous frame state; restoration now
  explicitly clears ICONIFIED as well as the Compose minimized state.
- The added dirty-exit test initially recreated fake capability objects when
  its close-request state recomposed, resetting the fixture's document state.
  The follow-up commit stabilizes those injected instances and uses explicit
  text-edit semantics while About has native focus. Final revision-specific
  rerun passed.

No new Rust behavior, live network check, Windows/Linux execution, or human
testing is claimed. Previous native OS visual/accessibility limitations and
the same two opt-in skips still apply.

- [ ] Human verifies About native chrome, focus/restoration and main-window
  interaction on target platforms.
- [ ] Human reviews the full JPG banner, sizing and readability in both themes.

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
