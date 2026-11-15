---
title: EQMS-109 brand token tests
status: draft
related: [EQMS-109]
---

# EQMS-109 test specification

Issue: #80. Defined before implementation.

| Test ID | Requirements | Check |
|---|---|---|
| T109-01 | R109-01 | `BrandTokensTest`: valid parse (upper/lower case hex), rejects bad hex, bad on-accent, blank name, unknown logo kind. |
| T109-02 | R109-02 | `BrandTokensTest`: warnings preserved verbatim. |
| T109-03 | R109-03, R109-04 | `BrandThemeTest` (Compose): provided accent/on-accent visible to children in light and dark; fallback accent equals Jewel focus colour of each theme. |
| T109-04 | R109-05 | `DesktopBrandTokensTest` with a fake process executor: command line, success mapping, error envelope, wrong command, invalid JSON, invalid colour. |
| T109-05 | R109-06 | `ProjectScreenTest`: warnings shown after open, failure message shown, cleared on close, still visible at a narrow window width. |
| T109-06 | R109-04 | Source check in review: no `Color(0x...)` accent literals in `feature/ui`, `feature/scaffold` main sources. |
| T109-07 | R109-07 | `cargo fmt --check`, `cargo clippy`, `cargo test`, Gradle jvmTest for `feature:ui`, `feature:scaffold`, `feature:document`, `apps:desktop`, Wasm compile. |

Automated outcomes are recorded separately. Missing/skipped tests do not count
as passing coverage.

## Manual checklist - pending

- [ ] Human review of accent use in light and dark themes with a real project.
- [ ] Human check of a low-contrast accent: warning visible, app still usable.
- [ ] Human check at a narrow window width.
- [ ] Review of the risk table and the scope split.
