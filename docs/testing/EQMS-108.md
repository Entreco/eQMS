---
title: EQMS-108 product identity tests
status: draft
related: [EQMS-108]
---

# EQMS-108 test specification

Issue: #79. Tests are defined before implementation.

| Test ID | Requirement mapping | Check |
|---|---|---|
| T108-01 | R108-01, R108-04, R108-05 | `ProductIdentityTest`: constants; `windowTitle` keeps "DutchDoxx" for null, blank, hostile or very long project names. |
| T108-02 | R108-02 | `ProductIdentityUiTest`: About content shows wordmark, version, author, licence notice and attribution; no signature/approval claim beyond "Drafts are not approvals." |
| T108-03 | R108-03, R108-05 | `ProductIdentityUiTest`: footer attribution present with no project, with a project named to imitate/contain other brands, and in light and dark. |
| T108-04 | R108-05 | `ProductIdentityUiTest`: with an open project named like another brand the footer attribution still renders exactly once; `AboutContent` has no branding parameter (reviewed in code). |
| T108-05 | R108-06 | Manual doc review of README/BuildAndRun naming rules. |
| T108-06 | R108-07 | `./gradlew :feature:scaffold:jvmTest :apps:desktop:test`; native integration tests run only in CI. |

Manual review/testing (not performed by automation, left unchecked in the PR):
open About on macOS, check light/dark, review wording, licence and trademark
decisions, and the author/licence placeholders.
