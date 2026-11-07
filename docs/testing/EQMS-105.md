---
title: EQMS-105 project lifecycle tests
status: draft
related: [EQMS-105]
---

# EQMS-105 test specification

Issue: #69. Mappings are defined before implementation.

| Test ID | Requirement mapping | Planned check |
|---|---|---|
| T105-01 | R105-03, R105-05, R105-06, R105-08 | `DesktopPreferences` unit tests: round trip, clear on close, recent ordering/dedup/cap of five, invalid or oversized stored values fail explicitly, backing-store failure is a `Result.failure`. |
| T105-02 | R105-01 | Native menu test: File lists New project, Open project, Open recent, Close project; Close disabled without project; recent entries reflect chrome state. |
| T105-03 | R105-01, R105-02 | Compose tests: no in-window Create/Open buttons, Project panel or default status text; menu commands open the directory dialog and create/open projects; left tool is Documents. |
| T105-04 | R105-03, R105-04, R105-05 | Compose tests: stored project is verified and opened at startup; the window renders before a delayed open completes; failure keeps empty state, shows error and does not rewrite the stored path; invalid stored value is shown. |
| T105-05 | R105-03, R105-06 | Compose tests: successful open/create saves project and recents; Close clears the project but keeps recents; selecting a recent opens it and failures keep it. |
| T105-06 | R105-01, R105-07 | Compose tests: menu commands are refused while busy; dirty edits prompt before switching/closing and cancelling keeps the project and stored path. |
| T105-07 | R105-08 | Compose test: save failure shows an error and the project stays open. |
| T105-08 | R105-09 | Run affected desktop and feature JVM suites (project, document, search, history, page setup, tool rail shortcuts) after migrating tests to menu commands; record exclusions as exclusions, not passes. |

Manual review/testing (not performed by automation, left unchecked in the PR):
launch with a remembered project; close and relaunch; move the project folder
and relaunch; native macOS menu behavior; keyboard and accessibility review.
