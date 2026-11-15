---
title: EQMS-111 project chrome and settings tests
status: draft
related: [EQMS-111]
---

# EQMS-111 test specification

Issue: #82. Defined before implementation.

| Test ID | Requirements | Check |
|---|---|---|
| T111-01 | R111-01, R111-02 | `ProjectSettingsTest.headerShowsProductLogoAndProjectName` (Compose). |
| T111-02 | R111-03, R111-04 | Updated `DesktopChromeTest`, `ScaffoldDocumentTest`, `ProjectBrandingTest`: gear button opens settings; theme toggles and keeps dirty editor/search state; persistence failure shown. |
| T111-03 | R111-05, R111-06 | `ProjectSettingsTest.settingsDialogChangesTheNameWithAnExplicitSave`, `saveFailureIsShown...`. |
| T111-04 | R111-07 | `ProjectSettingsTest` Discard confirmation; changed-elsewhere refusal is manual/design-reviewed (not automated). |
| T111-05 | R111-08 | `ProjectSettingsTest.settingsWithoutAProjectOnlyOffersAppearance`. |
| T111-06 | R111-09 | `BrandThemeTest.defaultButtonsUseTheProjectAccent...`. |
| T111-07 | R111-03 | CI-only native tests updated for the gear button (`DesktopWindowTest`, `DesktopAboutIntegrationTest`, `DesktopJewelPreviewTest`, `DesktopChromeIntegrationTest`); compile-checked only locally. |
| T111-08 | R111-10 | Gradle jvm suites, Wasm compile; Rust suite unchanged (no Rust changes). |

## Manual checklist (human, pending)

- [ ] Header reads DutchDoxx | logo | project name, also at the minimum window size and with a long name.
- [ ] Gear opens Project Settings as a movable, resizable window; theme toggles from there.
- [ ] Change name, shuffle logo, accent; Save; header updates; `qms project branding show` agrees.
- [ ] Close with edits asks to Discard; failed save keeps the edits.
- [ ] "New document" and other primary buttons use the project accent in light and dark themes.
- [ ] Settings feels fast and is easy to find for a non-technical user.
