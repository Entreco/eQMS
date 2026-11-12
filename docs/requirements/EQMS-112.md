---
title: EQMS-112 template model requirements
status: draft
related: [EQMS-112]
---

# EQMS-112 requirements

| ID | Requirement |
|---|---|
| R112-01 | A template (id slug, version >= 1, name, document type, structure, style, optional page defaults, header/footer regions) is validated against bounded limits; unknown fields and other schema versions are rejected. |
| R112-02 | Style is a closed declarative schema (bundled font families, bounded sizes, `accent`/`#RRGGBB` colours, table/image/TOC/cover/page-break options); it contains no CSS, scripts, URLs or system fonts. |
| R112-03 | Slot text allows only the placeholders `{title} {page} {total} {id} {revision} {status} {project}`, at most 120 characters. Slot images are the project logo or a hashed template asset. |
| R112-04 | Encoding is canonical and deterministic; decoding rejects non-canonical bytes; the content hash is SHA-256 of the canonical bytes. |
| R112-05 | `install` writes an immutable version, its assets and one hashed `template.installed` journal event; re-installing the same or an older version fails; assets must be valid PNGs matching their hash and be used by the template. |
| R112-06 | `list`/`show` verify the journal and every installed template and asset hash, failing on tampering. A built-in generic template is always available. |
| R112-07 | `qms template validate|list|show|install` expose this in text and JSON; `validate` needs no workspace. |
| R112-08 | Existing Rust regressions pass, also without a global git identity. |

Design and limitations: [EQMS-112 design](../architecture/EQMS-112.md). Draft
records, not approved compliance evidence.
