---
title: EQMS-108 DutchDoxx product identity and attribution design
status: draft
related: [EQMS-093, EQMS-107, EQMS-108]
---

# EQMS-108 product identity and attribution

Issue: #79 (epic #78). Dependency: EQMS-107 (#76), merged in PR #77.
Draft records, not approved compliance evidence; no approvals are claimed.

## Scope

Already shipped (EQMS-093): app icon set (`apps/desktop/src/main/resources/branding/`),
About window, window title `DutchDoxx`, DutchDoxx application menu.
This issue adds: a single source of product identity, a fuller About dialog
(wordmark, version, author, licence, fixed attribution), a small
"Powered by DutchDoxx" mark in the status footer, and naming rules.
Out of scope: per-project white-label UI (EQMS-109/110/111), a new icon
redesign, any signature, approval or audit-capability claims.

## Design

- `ProductIdentity` (`feature:scaffold` commonMain) is an immutable object of
  constants: product name, attribution text, author, licence notice and
  technical names. It takes **no input from project data**; project branding
  (EQMS-107) has no code path into it.
- `AboutContent` (jvm) renders wordmark, logo, version, description, author,
  licence and attribution from `ProductIdentity` plus the build version only.
  It has no parameter for project name, logo or accent.
- `ProductAttribution` renders `Powered by DutchDoxx` in the status footer,
  which `ProjectScreen` always composes; it is not conditional on a project,
  theme or branding.
- `ProductIdentity.windowTitle(projectName)` always contains `DutchDoxx`, so a
  project name may be added but never replaces it (for EQMS-111).
- Licence: the repository has no licence file. About states the licence is not
  yet specified rather than asserting one; the owner must set it before release.
- Author: repository owner `Entreco`.

## Naming rules

| Name | Use |
|---|---|
| DutchDoxx | Product name in UI, About, window title, installers and user-facing docs. |
| eQMS | Repository, domain/module and package names (`com.entreco.eqms`), roadmap IDs (`EQMS-nnn`). |
| `qms` | Native CLI binary and commands. |

Docs say "DutchDoxx" for the product and "eQMS"/`qms` only for technical
artefacts. Project white-label names never replace the product name.

## Risk and security rationale

Risk Management and Security Risk Management apply in a limited form.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Users mistake a white-labelled project for a different vendor product or lose provenance | Fixed, non-configurable attribution in About and footer; tested against project-name inputs | Attribution is UI-level; a modified fork can remove it. Not DRM. |
| Overstated capabilities in About (signatures, audit) | About text limited to "Drafts are not approvals." | Wording needs reviewer sign-off |
| Wrong/absent licence information | Explicit "not yet specified" notice | Owner must decide licence before release |
| Trademark conflict with "DutchDoxx" ("doxx" can mean exposing private data) | Recorded as a pre-release check; logo trademark/licensing is the project owner's responsibility | Not legal advice; not performed here |
