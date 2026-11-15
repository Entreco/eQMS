---
title: EQMS-111 project chrome and settings design
status: draft
related: [EQMS-107, EQMS-109, EQMS-110, EQMS-111]
---

# EQMS-111 project chrome, Project Settings and accent

Issue: #82. Stacked on EQMS-110 (#81, draft PR #111, not yet merged); depends on its
`previewBranding`, `BrandingPreview`, `LogoPreview` and dialog windows. Gap closure
under `AGENTS.md`: `qms project branding set` exists but has no UI.

## Scope

In: header `DutchDoxx, logo, project name`; a gear **Settings** button replaces the
Light/Dark theme button and opens **Project Settings** (appearance toggle + branding
editor with explicit Save); the project accent styles default (primary) buttons.
Out: branding in documents/templates (EQMS-115); new core behaviour (none needed).

## Design

- `ProjectCapabilities.projectBranding(path)` returns `BrandingPreview` (tokens + generated
  logo drawing data from `qms project branding show`); `saveBranding(path, draft)` runs
  `qms project branding set` (journaled by core) and returns the recorded branding.
- Header: product name, divider, logo (`LogoPreview`, generated shape/initials from core or the
  stored PNG `branding/logos/<sha>.png`, read-only), divider, project name. Falls back to the
  folder name if branding is unavailable. Without a project: `DutchDoxx | Quality workspace`.
- Settings (gear button and File > Settings share one dialog): appearance applies immediately;
  branding fields reuse the EQMS-110 editor (`BrandingFields`). Save is explicit and enabled
  only when changed and previewed. Core is asked for a preview only when logo/accent change.
  Closing with unsaved changes needs a second confirmation (Discard). Before saving, the stored
  branding is re-read; if it changed meanwhile the save is refused ("changed elsewhere").
  Without a project only appearance is offered.
- Accent: `BrandTheme` provides a Jewel `LocalDefaultButtonStyle` coloured with the accent and
  on-accent (hover/pressed shades); disabled colours and focus outline remain Jewel's. No
  branding -> Jewel default.

- Window chrome: `ThemedWindowContent` (feature:ui) is shared by all dialog windows and About. On
  macOS it makes the native title bar transparent with the theme background and window appearance
  (as the main window), with a themed centred title; other platforms keep the platform title bar.
  It uses AWT `apple.awt.*` root-pane properties because Jewel has no decorated dialog; dialogs stay modal.

## Risk (RM/SRM)

Low; UI only. Hazards: unreadable text on accent (on-accent comes from core, contrast warnings
stay visible); losing edits (dirty guard); stale overwrite (changed-elsewhere check, not a lock:
a race between check and save remains); wrong project edited (path is the open project).
Limitations: PNG logo is read from the workspace path by the UI; window behaviour and visuals
are not verified by automated UI tests. No approvals are claimed.
