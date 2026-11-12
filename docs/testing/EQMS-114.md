---
title: EQMS-114 template structure conformance tests
status: draft
related: [EQMS-114]
---

# EQMS-114 test specification

Issue: #86.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T114-01 | R114-01 | Core unit tests (conformance): conforming body; missing required; missing optional ignored; wrong level; wrong order; heading inside fenced code ignored; extra headings ignored. |
| T114-02 | R114-02 | Core document test: unbound, unknown pinned template, hash mismatch and verified pin each yield the expected metadata findings; historical revision checked against its own pin. |
| T114-03 | R114-03, R114-04, R114-05 | CLI test: pin a template, `create` with a non-conforming body warns in the envelope but succeeds; `document check` lists findings, then `save` of a conforming body gives `conforming: true`; unbound `save` adds no findings; `validate` unchanged. |
| T114-04 | R114-06 | `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test --workspace --locked` with `GIT_CONFIG_GLOBAL=/dev/null`. |

Manual review/testing (left unchecked in the PR): check a real document against
an installed template and review finding wording against the roadmap in #78.
