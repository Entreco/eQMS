---
title: EQMS-107 project branding requirements
status: draft
related: [EQMS-106, EQMS-107]
---

# EQMS-107 requirements

| ID | Requirement |
|---|---|
| R107-01 | Branding (name 1-60 chars without control characters or surrounding spaces, accent `#RRGGBB`, logo) is validated and encoded deterministically as canonical JSON; non-canonical or invalid files are rejected. |
| R107-02 | The default logo is a deterministic function of a seed (the project name by default) using a curated palette whose colours are legible with white or black text. |
| R107-03 | PNG logos are accepted only when structurally valid, 16-1024 px per side and at most 512 KiB, stored content-addressed; the CLI additionally decodes them. Other formats are rejected. |
| R107-04 | `set` writes tracked files and a hash-chained `project.branding_set` journal event with content hashes; `show` fails when the file does not match the latest event. |
| R107-05 | With no recorded branding, `show` returns defaults derived from the directory name, flagged `persisted:false`, without writing. |
| R107-06 | Insufficient accent contrast (3:1 on light or dark surface) yields warnings and never blocks. |
| R107-07 | `qms project branding show|set` and `qms init --name/--accent/--seed/--logo-file` expose branding in text and JSON; existing init behaviour is unchanged without these options. |
| R107-08 | Existing Rust regressions pass. |

Design, hazards and limitations: [EQMS-107 design](../architecture/EQMS-107.md).
Draft records, not approved compliance evidence.
