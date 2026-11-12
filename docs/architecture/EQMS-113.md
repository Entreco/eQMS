---
title: EQMS-113 document-template binding design
status: draft
related: [EQMS-106, EQMS-112, EQMS-113]
---

# EQMS-113 document-template binding

Issue: #85, part of epic #78; depends on EQMS-112 (#84, PR #102). Rust core and
native `qms` CLI only. Conformance (#86), the style engine (#87), UI (#88),
standards packs (#89) and reconciliation are separate issues.

## Design

- **Pin in the controlled Markdown (additive to schema 2).** A bound document
  carries three optional metadata fields after `footer` and before `status`:
  `template_id`, `template_version`, `template_hash` (the template's
  `sha256:` content hash). They are all present or all absent. Unbound
  documents are byte-identical to before, so no schema bump or migration is
  needed (same approach as EQMS-106 defaults). The pin is covered by the document
  content hash, so every revision keeps the template it was saved with.
- **Create.** `qms document create --type <T>` (or `--template <id>
  [--template-version N]`) resolves an installed template, pins it, seeds page
  setup and, when stdin is empty, the body from the template scaffold
  (`structure.body_placeholder`). `--type` matches `document_type` exactly across
  the latest version of every template id; zero or several matching ids is an
  error (use `--template`). Without a template option, behaviour is unchanged.
- **Change.** `qms document set-template <project> <id> --expected <rev>
  (--template <tid> [--template-version N] | --clear)` is an explicit, Draft-only
  metadata save: a new revision with a `document.saved` event whose payload adds
  `template_from` and `template_to` (`<id>@v<N>` or `none`). Verification
  requires these keys exactly when the pin differs from the parent revision's, so a
  pin cannot change silently. Ordinary `save` keeps the previous pin.
- **Historical revisions** keep their own pin; `show --revision` and `history`
  expose it. Templates are immutable per version (EQMS-112), so a pinned
  `(id, version, hash)` always resolves to the same template.
- **Precedence for page setup.** Effective value = explicit per-document value
  (EQMS-106) over template `page` defaults over built-in defaults. Template
  `page` seeds size, orientation and margins only at create; afterwards the
  document's stored page setup is authoritative and a template change never
  rewrites it. Header/footer text stays the document's own (3 placeholders);
  template header/footer regions are rendered by #87 and are not copied.
- Wording rule: templates support, never certify, compliance.

## Risk and security rationale

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Document pinned to a wrong/forged template | Pin resolved against the verified template store at create/change; hash must equal the installed content hash | Pin is not re-checked against the store on every read; renderer (#87) must verify |
| Silent template change on a controlled document | Draft-only; journaled `template_from`/`template_to`; verification rejects an unexplained pin change | Journal remains as implemented; no signatures |
| Template change alters past output | Pin lives in hashed content per revision; versions immutable | Only Draft documents can change template |
| Malformed pin injection in Markdown | Strict validation (slug, version >= 1, 64-hex hash, all-or-none) and canonical byte equality | none known |

Risk Management and Security Risk Management apply; no approvals are claimed.

## Limitations

No conformance checking, rendering or UI use of the pin yet. `qms document
import` and module artifacts create unbound documents. Existing Draft
documents are unbound until `set-template`.
