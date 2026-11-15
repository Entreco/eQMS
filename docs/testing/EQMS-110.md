---
title: EQMS-110 project creation with branding tests
status: draft
related: [EQMS-110]
---

# EQMS-110 test specification

Issue: #81. Defined before implementation.

| Test ID | Requirements | Check |
|---|---|---|
| T110-01 | R110-01, R110-02 | Rust CLI unit test (`project.rs`): preview needs no workspace, writes nothing, derives defaults from the name, honours `--seed`/`--accent auto`, rejects missing name, bad accent, bad logo file, non-PNG bytes. |
| T110-02 | R110-01, R110-08 | CLI integration test (`apps/cli/tests/cli.rs`): `--json` envelope command `project.branding.preview`; `init` with preview-identical options then `show` returns the same name/accent/logo. |
| T110-03 | R110-03..R110-07 | `ProjectBrandingCreateTest` (Compose): identity step appears after directory selection; cancel creates nothing; preview and core warnings shown; Create disabled for blank name/invalid preview; Shuffle changes seed; success calls `createProject` with the previewed draft; failure shown with hint. |
| T110-04 | R110-04, R110-06 | `LogoPreviewTest` (Compose): renders generated shapes and initials without error. |
| T110-05 | R110-01, R110-07 | `DesktopProjectBrandingTest` (fake process executor): exact argv for preview and for `init` (`=` form, values starting with `-`), success mapping, error envelope, wrong command, invalid JSON. |
| T110-06 | R110-09 | `ProjectBrandingCreateTest`: fields reachable by label; Create/Cancel focusable. Real Tab traversal is manual. |
| T110-07 | R110-10 | `cargo fmt --check`, `cargo clippy`, `cargo test`; Gradle jvmTest for `feature:ui`, `feature:scaffold`, `feature:document`, `feature:search`, `apps:desktop`; Wasm compile. |
| T110-08 | R110-01 | Latency check: time of `qms project branding preview` from the built binary, recorded as observed in the evidence (no threshold asserted). |

Automated outcomes are recorded separately. Missing/skipped tests do not count
as passing coverage.

## Manual checklist (human, pending)

Launch the desktop app (see Build and Run), then:

- [ ] File > New project, pick/create an empty folder, Select directory.
- [ ] The Project identity step appears; typing a name updates the logo and accent immediately; it feels instant.
- [ ] Shuffle changes the logo; picking a swatch or typing `#RRGGBB` changes the accent.
- [ ] A very light accent (e.g. `#FFFF00`) shows a contrast warning but Create still works.
- [ ] An invalid accent or blank name disables Create and shows why.
- [ ] Browse a valid PNG: it is previewed; a non-PNG or huge file shows an error.
- [ ] Tab/Shift+Tab reach every control; Enter creates; Escape cancels.
- [ ] After Create, run `qms project branding show <folder>`: values match what was previewed.
- [ ] Cancel at the identity step: the folder stays uninitialised.
- [ ] Review the risk table and scope split in the design.
