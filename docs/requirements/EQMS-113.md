---
title: EQMS-113 document-template binding requirements
status: draft
related: [EQMS-112, EQMS-113]
---

# EQMS-113 requirements

| ID | Requirement |
|---|---|
| R113-01 | A document may record a template pin (id, version >= 1, `sha256:` content hash) as optional canonical metadata; the three fields are all present or all absent, strictly validated, hashed with the document, and absent pins leave existing canonical bytes unchanged. |
| R113-02 | `qms document create --type <T>` / `--template <id> [--template-version N]` resolves an installed, verified template, pins it, seeds page setup from template `page` defaults and the body from the scaffold when no body is supplied. Ambiguous or unknown types/templates fail without writing. |
| R113-03 | Explicit page options override template defaults, which override built-in defaults (EQMS-106 overrides remain). A later template change never rewrites the stored page setup. |
| R113-04 | Changing or clearing a Draft's template is an explicit `set-template` save producing a `document.saved` event with `template_from`/`template_to`; history verification requires those keys exactly when the pin changes. Non-Draft documents and stale `--expected` revisions are rejected. |
| R113-05 | Ordinary `save` keeps the previous pin; every revision keeps the pin it was saved with and `show --revision`/`history` expose it. |
| R113-06 | Document JSON exposes `template` only when pinned. Existing unbound documents, imports and module artifacts are unchanged. |
| R113-07 | Existing Rust regressions pass, also without a global git identity. |

Design and limitations: [EQMS-113 design](../architecture/EQMS-113.md). Draft
records, not approved compliance evidence.
