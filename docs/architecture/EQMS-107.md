---
title: EQMS-107 project branding design
status: draft
related: [EQMS-106, EQMS-103, EQMS-107]
---

# EQMS-107 project branding foundation

Issue: #76. Slice 1 of the roadmap: DutchDoxx product identity, per-project
white-label branding, a document template system and publication
reconciliation. Only the Rust core and native `qms` CLI are in this slice.
Deferred: product identity/About attribution, Kotlin brand tokens, project
creation flow and app chrome, templates, waivers, reference model and
publication reconciliation.

## Design

- Branding is `branding/project.json` (canonical pretty JSON, schema 1):
  `name` (1-60 chars), `accent` (`#RRGGBB`, uppercase) and `logo`, either
  `generated{seed}` or `png{sha256}`. PNGs live at `branding/logos/<sha256>.png`.
- Writes go through `Workspace::record_event("project.branding_set", ...)`:
  files are written, hashed, committed with the hash-chained journal record.
  `show` verifies the workspace and checks the file hash against the latest
  event; mismatch is an error (tamper detection).
- With no event, `show` derives defaults from the directory name
  (`persisted:false`) and writes nothing.
- The generated logo is a pure function of SHA-256(seed): initials, a colour from
  a 12-colour curated palette and a shape, so the default is stable and random-looking.
- Contrast (WCAG ratio) of the accent is checked against light `#FFFFFF` and dark
  `#2B2D30` surfaces; shortfalls are warnings, not errors ("easy to comply,
  allow exceptions"). `on_accent` is the black/white text colour with better contrast.
- Branding is white-label presentation only; it will appear in app chrome, not
  in controlled document content, and is not part of document content hashes.
  The fixed DutchDoxx attribution is not configurable (planned UI slice).

## Risk and security rationale

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Malicious/oversized logo image | 512 KiB cap, 16-1024 px, PNG signature/IHDR/IEND checks in core; full decode in CLI; PNG only (no SVG/script) | Not sanitised or re-encoded; decoder bugs remain possible |
| Path traversal / symlink swap | Fixed paths, content-addressed names, symlinks refused, exclusive create | Remove-then-create write is not atomic |
| Silent identity change | Hashed, journaled, git-committed; `show` verifies | Local journal alone does not prove author identity (EQMS-103) |
| Unreadable accent | Contrast warnings in both themes | Advisory only |

Risk Management and Security Risk Management apply; no approvals are claimed.
