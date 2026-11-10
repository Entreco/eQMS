---
title: EQMS-105 native-menu project lifecycle
status: draft
related: [EQMS-090, EQMS-092, EQMS-093, EQMS-094, EQMS-105]
---

# EQMS-105: Native-menu project lifecycle

Issue: #69.

## Scope

Make the native File menu the only place to create, open and close projects,
and reopen the last project on launch until it is explicitly closed.

Before this change project controls existed in the native menu, header buttons,
the left "Project" tool panel and the bottom status field. The open project was
not remembered.

## Boundaries

- No multi-window/multi-project sessions, templates or workspace format change.
- No lifecycle, signature or approval change.
- Page setup is unchanged; it moves into document metadata in EQMS-106 and its
  button stays in the Documents panel until then.
- Only paths are persisted, never document content or verification results.

## Design

**Menu.** File: New project, Open project, Open recent (up to 5, most recent
first), Close project. Menu items only post a `DesktopCommand` to
`DesktopChrome`; `ProjectScreen` consumes it and owns the existing directory
dialog, verifying open, dirty-edit navigation guard and busy handling. Commands
are refused while the window is busy, a dialog is open or a document operation
is running, so a stale or concurrent project change cannot start.

**In-window UI.** The header keeps the project name and the theme toggle. The
Create/Open header buttons, the empty-state "Project" panel and the default
"No project open"/"Workspace open" status text are removed. The empty state
points to the File menu. The left tool formerly named Project is renamed
Documents (same shortcut); it lists documents and keeps New document, Page
setup and Refresh.

**Persistence.** `DesktopPreferences` stores the last project path and the
recent list in the existing `com/entreco/dutchdoxx` preferences node (keys
`project`, `recent0`..`recent4`). `DesktopChrome` receives the initial values
and one `saveProjects(project, recents)` function, like appearance. The saved
path is the canonical path returned by `qms` after a successful create/open.
Close project stores no current project but keeps the recent list.

**Restore.** On first composition `ProjectScreen` opens the stored path with the
same verifying `qms verify` open used by the menu, in a coroutine, so the
window and an empty state render immediately. Success shows the workspace.
Failure (missing, moved, unverifiable, `qms` unavailable) shows an error
message and the empty state and keeps the stored path untouched. A stored value
that cannot be read or is invalid is surfaced as an error and treated as no
project. Preference read/write failures never prevent using the application.

## Hazards, mitigations and limitations

| Hazard | Mitigation |
|---|---|
| Automatically opening a stale or replaced path | Verification on every launch; visible error; path never auto-deleted |
| Lost edits when switching/closing | Existing `navigate` dirty guard for every menu action |
| Wrong-project results after a switch | Commands disabled while busy; project generation key unchanged |
| Misleading verified status | Status text only reports the actual open/restore outcome |
| Hidden state | Path-only preference; no content or secrets stored |

Residual limitations: a path may later hold a different valid workspace; local
verification does not prove identity. Preferences are per OS user and not
version-controlled. Native menus cannot be exercised on every platform in
automated tests. These records are draft, not approved compliance evidence.
