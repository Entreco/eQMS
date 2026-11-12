---
title: EQMS-114 template structure conformance design
status: draft
related: [EQMS-112, EQMS-113, EQMS-114]
---

# EQMS-114 template structure conformance

Issue: #86, part of epic #78; depends on EQMS-113 (#85, PR #103). Rust core and
native `qms` CLI only. Kotlin Findings/Metadata panel, waiver/severity grading
(separate issue), style engine (#87) and UI (#88) are out of scope.

## Design

- **Pure check.** `conformance::check_structure(template, body)` compares the
  Markdown ATX headings of a document body (fenced code ignored) with
  `structure.sections`. Matching is by exact title; `level` must also match.
  Findings (all `severity: warning`, stable codes):
  - `structure.section_missing` - a `required` section has no heading.
  - `structure.section_level` - section found at a different heading level.
  - `structure.section_order` - a declared section appears before one that the
    template lists earlier (order is checked among present sections).
  Optional sections and extra headings are never findings. `guidance` and
  `allowed_blocks` are not evaluated.
- **Metadata findings.** `Documents::conformance(id, revision)` also reports
  `metadata.template_unbound` (no pin), `metadata.template_missing` (pinned
  id/version not installed) and `metadata.template_hash_mismatch` (installed
  content hash differs from the pin); structure is only checked when the pinned
  template resolves with a matching hash.
- **Findings model.** Findings are `{code, severity, message, section?}` and are
  returned in the existing schema-2 envelope `findings` array. They never fail a
  command: validation is advisory, so Draft saves warn and still succeed.
- **CLI.** New `qms document check <project> <id> [--revision R]` returns data
  `{id, revision, template, conforming, findings}`. `create`, `save` and
  `set-template` on a pinned document add the same findings to the envelope
  (and `warning:` lines in text mode). `document validate` (workspace-less
  canonical Markdown check) is unchanged.
- **Wording rule.** Conformance means "matches the template structure"; it does
  not certify regulatory compliance. Severity grading and waivers are a later issue.

## Risk and security rationale

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Author mistakes warning-free output for approval/compliance | Wording and docs state structure only; no lifecycle gating | Users must still review content |
| False negatives from heading parsing (setext headings, HTML) | Documented ATX-only rule; deterministic | Unusual heading styles are reported as missing |
| Stale or forged pin hides drift | Hash compared with installed template; mismatch is a finding | Not enforced as an error; no signatures |
| Untrusted body causes heavy work | Linear scan of body, bounded by existing document limits | none known |

Risk Management and Security Risk Management apply; no approvals are claimed.

## Limitations

Warnings only; no severity grading, waivers, blocking transitions, block-type
checks or Kotlin panel yet.
