---
title: EQMS-093 DutchDoxx menu requirements
status: draft
related: [EQMS-093]
---

# Requirements

Issue: #52. Scope clarification approved by the user: updates live in Help,
avoiding a native application-menu bridge.

| ID | Requirement |
|---|---|
| R093-01 | Desktop menu identity, title, About and launch/package name are DutchDoxx, not MainKt. Keep native window controls and eQMS domain names; main macOS chrome follows R093-10. Version comes from one desktop build source and identifies development builds. |
| R093-02 | macOS native application menu provides OS-standard About/Settings and system items. File provides New project and Close project; Edit/View have no custom commands; Help provides Check for updates. Non-macOS remains launchable. |
| R093-03 | Menu New reuses guarded existing creation. Close project returns to welcome, never deletes data/exits; dirty cancellation preserves edits. Prevent competing busy/modal operations and preserve guarded Quit/window close. |
| R093-04 | Settings and toolbar share persistent light/dark appearance, default dark only when unset. Invalid preferences and IO failures are visible; changing theme preserves project/editor/search state. |
| R093-05 | About reports truthful desktop version. On-demand stable GitHub release checking reports checking, newer/current/older, no release, incomparable and error states accurately; no startup/background check or installation. |
| R093-06 | Bound network requests, allow cancellation, prevent duplicates, reject malformed metadata and unsafe release URLs, report browser errors, and send no workspace/user data or credentials. |
| R093-07 | Preserve create/open/save/history/search behavior, Rust domain boundaries, and affected Wasm compilation. Record native-check limitations; manual review/testing remain pending. |
| R093-08 | Use supplied branding/logo.png for macOS ICNS, Windows ICO, Linux PNG and desktop window/taskbar icons. Preserve its dark tile background, rounded-corner alpha and aspect ratio without additional outer padding. Keep About artwork separate and accessible; make generation reproducible without normal-build conversion dependencies. |
| R093-09 | About opens as one independent non-modal window with native title bar/close controls, full branding/about.jpg artwork without distortion, version and draft notice. Main workspace remains usable; repeated requests restore/focus About without duplicates. Closing About does not close/reset the project or application. Theme changes apply to About too. Launcher artwork remains independent of About. |
| R093-10 | On macOS/JBR, the main window uses an IntelliJ-style unified Jewel title bar with native traffic lights and the existing app/project label and Create/Open/theme actions exactly once. Chrome follows persisted and live light/dark appearance without resetting workspace/editor/search state. Preserve native menus, action hit-testing, dragging, resizing, minimize/zoom/fullscreen, minimum size and guarded close. Keep About and non-macOS native decoration unchanged. Unsupported runtime errors are explicit. |
