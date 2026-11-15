---
title: EQMS-109 brand token system design
status: draft
related: [EQMS-107, EQMS-109, EQMS-115]
---

# EQMS-109 brand token system (Kotlin/Compose)

Issue: #80. Depends on EQMS-107 (#76, merged). Consumes the Rust core/CLI
branding; the CLI stays authoritative and Kotlin does not re-implement
contrast or `on_accent` logic.

## Scope

In: validated `BrandTokens` (name, accent, on-accent, logo descriptor, core
warnings); `BrandTheme` layered over the Jewel IntUi light/dark theme; a
desktop adapter reading `qms project branding show --json`; first consumers
(selected editor tab indicator, contrast-warning notice in the project screen).

Out (tracked elsewhere): rendering the logo and project chrome (EQMS-111, #82),
project creation flow (EQMS-110, #81), template style engine (EQMS-115, #87;
it will consume `BrandTokens`), restyling Jewel's own default-button/selection
colours, branding edits from the UI.

## Design

- `BrandTokens` (`feature:ui` commonMain, pure Kotlin): `accent` and `onAccent`
  are parsed from strict `#RRGGBB`; `onAccent` must be `#000000` or `#FFFFFF`
  as emitted by core. `warnings` are the core contrast warnings, verbatim.
  `logo` is a descriptor (`Generated(seed)` or `Png(sha256)`), not rendered yet.
  Invalid input yields a failure, never a partially valid token set.
- `BrandTheme(tokens, content)` (`feature:ui` jvmMain) provides
  `LocalBrandTokens`. With `null` tokens (no project, or branding unreadable)
  the accent falls back to Jewel's focus outline colour of the active theme, so
  no accent colour is hard-coded. `Brand.accent` / `Brand.onAccent` read the tokens.
- `ProjectCapabilities.brandTokens(path)` has a default returning `null`, so
  existing implementations keep working. `DesktopProjectCapabilities` runs
  `qms project branding show --json <path>`, validates the v2 envelope
  (`command == project.branding.show`) and maps `data` to tokens.
- `ProjectScreen` loads tokens when a project opens, wraps content in
  `BrandTheme`, and shows core contrast warnings (or a load failure) in a
  non-blocking notice ("easy to comply, allow exceptions").
- Branding is white-label chrome only; paper/document colours (`Paper`) stay
  fixed so controlled-document appearance does not depend on project branding.

## Risk and security rationale

Risk Management and Security Risk Management apply in a limited form: this is
presentation code, but it parses output from a subprocess and drives UI colour.
No approvals are claimed.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Unreadable text on accent | Core `on_accent`; core warnings shown in the UI; accent used only for indicators, never as the sole carrier of meaning | Warnings are advisory; Jewel's own components keep the stock accent |
| Malformed/malicious CLI output | Strict envelope and colour validation; failure falls back to theme defaults and is reported | Trust in the local `qms` binary is unchanged (EQMS-103) |
| Branding shown for the wrong project | Tokens are keyed to the opened project path and reloaded on open; cleared on close | Race on rapid project switches is handled by ignoring stale results only when the path differs |
| Branding affecting controlled documents | Document paper colours untouched | Template style (EQMS-115) will define document-level use |

## Limitations

The logo token is carried but not drawn. Jewel component accents are not
re-themed. The UI cannot edit branding.
