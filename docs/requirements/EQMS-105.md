---
title: EQMS-105 project lifecycle requirements
status: draft
related: [EQMS-105]
---

# EQMS-105 requirements

| ID | Requirement |
|---|---|
| R105-01 | The native File menu provides New project, Open project, Open recent and Close project. Close project is enabled only with an open project. All items are disabled while the workspace is busy. |
| R105-02 | The window contains no other project create/open/close controls: no header Create/Open buttons, no empty-state Project panel and no default project status text. The left tool is named Documents. |
| R105-03 | After a successful create or open the canonical project path is stored as the last project. Close project clears it. Only paths are stored. |
| R105-04 | On launch the stored project is opened through the same verifying open as the menu, without delaying window display. |
| R105-05 | If restore fails, the window shows the empty state and a visible error, and the stored path is retained. An unreadable or invalid stored value is shown as an error and treated as no project. |
| R105-06 | Open recent lists at most five distinct paths, most recent first, kept across Close project. Choosing one uses the same verifying open; failures are visible and do not remove the entry. |
| R105-07 | Opening, creating, restoring or closing a project respects the existing dirty-edit, busy, conflict and close guards. |
| R105-08 | Failure to save preferences is shown and does not close the project or stop the application. |
| R105-09 | Restoring, listing recents or closing never modifies workspace documents, refs or journals. Existing project, document, search, history and page-setup behavior is otherwise unchanged. |

Hazards, mitigations and limitations are defined in the
[EQMS-105 design](../architecture/EQMS-105.md). These records are draft, not
approved compliance evidence.
