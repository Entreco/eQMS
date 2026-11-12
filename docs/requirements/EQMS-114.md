---
title: EQMS-114 template structure conformance requirements
status: draft
related: [EQMS-113, EQMS-114]
---

# EQMS-114 requirements

| ID | Requirement |
|---|---|
| R114-01 | A structure check reports a missing required section, a section at the wrong heading level and a section out of template order as structured findings with stable codes; optional sections and extra headings are not findings; headings inside fenced code are ignored. |
| R114-02 | Metadata findings report an unbound document, a pinned template that is not installed, and a pin hash that differs from the installed template; structure is checked only against a verified pinned template. |
| R114-03 | `qms document check <project> <id> [--revision]` returns `{id, revision, template, conforming, findings}` and the findings in the envelope `findings` array; it never fails because of findings and does not write. |
| R114-04 | `create`, `save` and `set-template` of a pinned document succeed and add the findings as warnings (envelope `findings`; `warning:` lines in text); unbound documents add none. |
| R114-05 | All findings carry severity `warning`; severity grading and waivers are out of scope. `document validate` is unchanged. |
| R114-06 | Existing Rust regressions pass, also without a global git identity. |

Design and limitations: [EQMS-114 design](../architecture/EQMS-114.md). Draft
records, not approved compliance evidence.
