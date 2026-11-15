---
title: EQMS-127 CI-only native desktop integration
status: draft
related: [EQMS-127, EQMS-093, EQMS-105]
---

# EQMS-127 design and risk assessment

Issue: #107. Defined before implementation; not approved compliance evidence.

## Decision

Native application windows and OS input are integration concerns.
`DesktopWindowNativeTest`, `DesktopWindowTest`, `DesktopNativeMenuTest`, and
`DesktopJewelPreviewTest` move from desktop `test` to `integrationTest`.
The Kotlin integration compilation associates with main for internal access.
Local `test`/`check` do not depend on integration execution. A task guard and
pre-initialization class guard reject execution outside GitHub Actions before
constructing windows or Compose rules; no local override is provided.

Approved audit refinement: five methods that open the production About window
also move into integration classes: the desktop logo dialog test, two scaffold
document/About tests, and two scaffold chrome/About tests. Scaffold gets its own
integration compilation associated with test for shared fixtures, without
moving non-window regressions or introducing test dependencies into production.
Both modules enforce the same CI-only policy before Compose-rule construction.

Keep safe unit and offscreen Compose component tests. Distinct `kotlin-tests`
and `native-integration` checks run on PRs and main pushes alongside Rust
`check`. Native tests run serially on an isolated macOS runner with JBR 25,
synthetic workspaces, a built release CLI and explicit preview destinations.
Report discovery, failures and unexpected skips; missing prerequisites are
failures, never passing native coverage. Upload diagnostic reports/artifacts
even on failure. Main's existing ruleset must require all three check contexts.

## Native reliability

The existing failure report times out waiting for appearance to change after
the Robot click; it does not prove a permission, focus or coordinate defect.
Observe layout and active-window readiness, native pointer positioning and
action coordinates in CI; retain genuine click/drag/minimize/restore assertions.
Use bounded waits with stage-specific errors and unconditional button/window
cleanup. Production behavior changes require evidence of a production defect.
Hosted macOS alone does not establish GUI/input capability; unsupported CI
must fail visibly and be reported as blocked.

## Risk Management and Security Risk Management

Both apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Shared pointer or foreground windows affect user work | Separate source set; task and class guards before UI initialization; CI-only native execution | Environment markers are an execution policy, not a security sandbox |
| Green Rust CI conceals Kotlin/native defects | Distinct required checks; expected test coverage; reject skips/prerequisite failures | New required contexts must be verified and configured server-side |
| Timing/focus/permissions cause false results | Dedicated macOS session, supported JBR, bounded observable readiness and diagnostics | Hosted runner capability must be demonstrated; no permission bypass |
| CI artifacts expose user data | Synthetic temporary workspaces; no local captures; minimal permissions | Generated artifacts still require review before external sharing |
| Test failure leaves input pressed or windows running | Finally cleanup; serial native tests and isolated test JVMs | Human OS interaction/accessibility review is still necessary |

No Rust/core dependency or production architecture change is planned. No
approval, human test result or completed native fix is implied by this design.
