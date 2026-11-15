---
title: EQMS-110 project creation with branding design
status: draft
related: [EQMS-107, EQMS-109, EQMS-110, EQMS-111]
---

# EQMS-110 project creation flow with branding

Issue: #81. Depends on EQMS-107 (#76, merged: core branding, `qms init
--name/--accent/--seed/--logo-file`) and EQMS-109 (#80, merged: brand tokens).
This issue is a gap closure under the end-to-end rule in `AGENTS.md`: core
branding exists but cannot be reached from the desktop application.

## Scope

In: after choosing a directory for **File > New project**, a "Project identity"
step with a name field, a generated-logo suggestion (seeded by the name, with
Shuffle), an optional PNG upload, an accent picker with a live preview and the
core contrast warning, and a Create action that records branding through
`qms init`. One new workspace-free core/CLI command,
`qms project branding preview`, so the UI shows exactly what core will record.

Out: editing branding of an existing project and chrome/logo display (EQMS-111,
#82); template styling (EQMS-115); changing the project-open flow.

## Design

- Flow: directory dialog (unchanged) -> "Project identity" dialog -> `createProject`.
  Cancelling the identity step creates nothing.
- Core/CLI: `qms project branding preview --name N [--seed S] [--accent
  #RRGGBB|auto] [--logo-file F]` needs no workspace and writes nothing. It
  applies the same `apply_options` rules as `init`/`set`, so validation (name
  length, colour, PNG type/size/dimensions) is identical. It returns the
  `show` shape: `name`, `accent`, `on_accent`, `logo` (generated logos carry
  `initials`, `color`, `shape`), `warnings`. Defaults derive from the name
  (`Branding::default_for`).
- `ProjectCapabilities` gains `previewBranding(draft)` and
  `createProject(path, draft)` and `chooseLogoFile()`. Defaults keep existing
  implementations compiling: preview fails with "not supported",
  `createProject` falls back to `openProject(path, create = true)` and
  `chooseLogoFile` returns `null`.
- `DesktopProjectCapabilities` runs `qms init --json path --name=... --seed=...
  --accent=... [--logo-file=...]` (`=` form so values beginning with `-` are
  never parsed as options; arguments are passed as an argv list, never via a shell).
- `BrandingDialog` (`feature:scaffold`) debounces input (~200 ms), cancels
  superseded previews, and shows the preview in a `LogoPreview` (circle, square,
  hexagon with initials, or the PNG) plus accent swatch, core warnings
  (non-blocking) or the core error. Create is disabled while the name is
  invalid or no valid preview exists, and during creation. Shuffle appends
  `#<random>` to the name to form the seed (so initials stay name-based while shape and
  colour change); `auto` accent follows the seed. Eight accent swatches are
  fixed hex suggestions only; the hex field stays authoritative and core validates it.
  The preview draws the accent and on-accent that core returned.
- Logo upload: a Browse button (system file dialog via the capability) fills the
  path field; the file is validated by core through the preview. Kotlin does not
  parse the PNG for validation.

## Risk and security rationale

Risk Management and Security Risk Management apply in a limited form: the UI
passes untrusted user text and a user-chosen file path to a local subprocess
and the core stores them. No approvals are claimed.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Malicious or oversized logo file | Core `validate_png`: PNG only, 16-1024 px, at most 512 KiB, read capped; validated in preview before create | Trust in the local `qms` binary unchanged (EQMS-103) |
| Argument/option injection through name, seed or path | argv list (no shell); `--opt=value` form; core validates name/seed/colour | None known |
| Unreadable accent chosen | Core contrast warning shown live; non-blocking by design | Advisory only |
| Preview shown differs from what is stored | Preview uses the same core `apply_options`; create re-validates | Seeds are shuffled in the UI, so the previewed seed is passed explicitly |
| Project created but branding step fails | The UI reports the core error and states that the folder may already be initialised and can be opened with File > Open project | `init` is not transactional across workspace and branding (core behaviour from EQMS-107); preview validation makes this unlikely |
| Branding choice affects controlled documents | Chrome-only, unchanged from EQMS-109 | Template styling is EQMS-115 |

## Limitations

The system file dialog (Browse) and real-window keyboard traversal are not
covered by automated tests and are on the manual checklist. Editing and chrome
display of branding remain EQMS-111.
