---
title: EQMS-093 DutchDoxx menu tests
status: draft
related: [EQMS-093]
---

# Test specification

Issue: #52. Defined before implementation.

| ID | Requirements | Check |
|---|---|---|
| T093-01 | R093-01, R093-02 | Actual JBR launch and OS menu inspection: DutchDoxx identity/title, native About/Settings/system items, File order, empty Edit/View, Help updates; run/hotRun/bundle metadata. Separate OS observations from Compose captures and unexecuted manual checks. |
| T093-02 | R093-03 | Scaffold Compose tests dispatch actual exposed menu commands: New uses chooser/init, Close returns welcome, dirty cancel/discard, no project disabled, busy/modal commands rejected, Quit/window close remain guarded. |
| T093-03 | R093-04 | Preference adapter tests: unset/default, light/dark read/write/reload, invalid value and read/write failures. Scaffold tests: Settings and toolbar synchronize, failures visible, theme preserves dirty/search state. |
| T093-04 | R093-05, R093-06 | Injected release transport: newer/equal/older, v/build-metadata versions, dev/unparseable tags, no releases, rate limit/HTTP/offline/timeout, malformed/oversized metadata, unsafe URL and browser failures. Actual asynchronous dialog duplicate/cancel behavior. |
| T093-05 | R093-01, R093-05 | About shows generated version, development builds remain incomparable. Package metadata derives from same numeric version. |
| T093-06 | R093-07 | Affected scaffold/document/search/desktop tests and feature/web Wasm compilation. No Rust implementation changes; native menus are not proven by Compose-only tests. |
| T093-07 | R093-08 | Regenerate/compare all platform icons from logo.png; inspect dimensions, retained dark tile/rounded-corner alpha and all ICO/ICNS frames. Compare packaged PNG pixels to aspect-preserving source conversion. Verify window and macOS bundle icon; retain About tests. Configure Windows/Linux assets but record host execution as pending if unavailable. |
| T093-08 | R093-09 | Inspect actual visible AWT About frame: native decoration/title, no modal Dialog, one instance on repeat request, restoration after minimizing, disposal/reopen via native close, workspace usability and retained state. Render the full JPG in both themes, check source/rendered aspect ratio, version/draft notice, and About Close action. Re-run desktop/scaffold tests, affected Wasm compilation and macOS packaging. |
| T093-09 | R093-01, R093-03, R093-04, R093-07, R093-10 | Launch the production macOS window host under JBR; assert one header/actions set, persisted initial theme and live light/dark chrome, stable window/workspace state, minimum size, native menus and guarded window close. Verify client-region action clicks, native control spacing and no second pale title strip. Exercise dragging, resize, minimize/restore and zoom/fullscreen where automation permits; record missing native interaction as pending manual checks, not passing coverage. Retain About lifecycle and non-macOS standard-window branch; run affected scaffold/document/search/desktop tests, Wasm compilation and macOS packaging. |

Start with `:feature:scaffold:jvmTest :apps:desktop:test`, then affected
`:feature:document:jvmTest :feature:search:jvmTest` and feature/web Wasm
compilation. Inspect supported package task/metadata and launch app under JBR.
Use isolated fixtures/preferences; never modify existing workspaces in tests.

Evidence must identify the tested code revision before a separate evidence
commit, JBR/environment, commands, observed failures/exclusions, OS integration
observations, and limitations. Human review and manual testing remain unchecked.

Observed results: [EQMS-093 evidence](evidence/EQMS-093.md).
