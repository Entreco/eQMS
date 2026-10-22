---
title: EQMS-093 DutchDox desktop menus
status: draft
related: [EQMS-092, EQMS-093]
---

# DutchDox desktop menus

Issue: #52. Dependency: EQMS-092 (#50), merged PR #51. Manual approval of
these new records is not implied.

## Native integration and boundaries

Use Compose Window MenuBar for File (New project, Close project), empty Edit
and View, and Help (Check for updates). Set macOS application identity before
AWT initializes, including Gradle run/hotRun JVM arguments and bundle name.
Use public `java.awt.Desktop` About/Preferences/Quit handlers for the native
DutchDox application menu; retain OS-provided labels/system items and title bar.
JBR 25 exposes no public API for inserting arbitrary application-menu items.
The user approved moving Check for updates to Help rather than adding a native
bridge, dependency, reflection, or internal JDK access.

Desktop branding is DutchDox; eQMS repository/domain/CLI names are unchanged.
`dutchDoxVersion` is the desktop version source, default `1.0.0-dev`, independent
of the Rust CLI version. Build resources, About, update comparison and native
package metadata derive from it. Development versions are not comparable to
published stable releases. The numeric portion supplies the package version;
the visible About version retains the development suffix.

Native adapters, build identity, preference storage and HTTP/browser access stay
in apps:desktop. Jewel dialogs, typed menu commands, enabled state, and the
single project/editor/navigation state stay in feature:scaffold/jvmMain.
Rust and Wasm contracts remain unchanged. Non-macOS uses a regular Application
menu for About/Settings and the existing guarded window close.

## State and behavior

The scaffold owns command execution and exposes callbacks/state to the desktop
menu composition. No duplicate project state. New project reuses initialization
and the chooser; Close project clears session project/document/search state
only through the dirty guard and returns to welcome, without exiting or
deleting files. Conflicting commands are disabled/rejected during operations
and dialogs. Quit cancels the OS's immediate quit response and requests guarded
application exit. A pending quit can be cancelled by Keep editing.

Settings contains light/dark appearance only. The desktop stores it using Java
Preferences in an application-specific node. Read once before showing the
screen; write/flush off the UI thread. Missing values default dark; invalid
values and read/write failures are visible errors, not silent defaults.
The existing theme button and Settings share this state. Persistence failure
does not discard project/editor state or claim the change was saved.

About displays the desktop version and an explicit draft/not-approval notice.
Update checking is user-triggered only, on an IO dispatcher with bounded
connect/request time and response size, no credentials or workspace data.
Query GitHub's latest published stable release endpoint for Entreco/eQMS.
404 means no published release, not up to date. Validate JSON and release tag;
compare numeric stable semantic versions only (optional leading v, build
metadata ignored). Development or unknown versions are explicitly incomparable.
Report HTTP/rate-limit, malformed/oversized metadata and transport errors.
Cancel the request when its dialog closes; prevent duplicates. Only open
validated HTTPS GitHub release-tag URLs for this repository, on user request.
No download, install, background checking, or provenance claim.

## Risk applicability

Risk Management and Security Risk Management both apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Menu commands lose drafts or bypass modal/busy state | Single controller, dirty guards, disabled actions and mapped regressions | Human usability/accessibility review remains required |
| Native Quit bypasses save protection | Cancel native immediate quit and route through existing close request | OS force termination cannot be guarded |
| Preferences fail or reset workspace state | Visible errors, shared appearance state, off-thread persistence | Local user configuration is not a controlled project record |
| Untrusted release metadata/URLs or misleading availability | Bounded HTTPS request, explicit parser/version states, URL allowlist, no execution | Release availability does not authenticate software provenance |
| Platform integration regresses other shells | Public APIs, macOS checks, JVM/Wasm regressions | Actual native menus require OS-level inspection, not Compose screenshots |

Requirements: [EQMS-093](../requirements/EQMS-093.md).
Tests: [EQMS-093](../testing/EQMS-093.md).
