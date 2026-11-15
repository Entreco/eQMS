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

The Gradle wrapper JAR must be tracked despite the generic JAR ignore rule.
Initial CI demonstrated that scripts/properties alone could not launch Gradle
in a fresh checkout. The restored JAR matches Gradle 9.6.1's published SHA-256
`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`;
CI's setup-gradle wrapper validation remains enabled.

## Native reliability

The existing failure report times out waiting for appearance to change after
the Robot click; it does not prove a permission, focus or coordinate defect.
Observe layout and active-window readiness, native pointer positioning and
action coordinates in CI; retain genuine click/drag/minimize/restore assertions.
Use bounded waits with stage-specific errors and unconditional button/window
cleanup. Production behavior changes require evidence of a production defect.
Hosted macOS alone does not establish GUI/input capability; unsupported CI
must fail visibly and be reported as blocked.

Use the existing minimum native window size for the pointer fixture, centered
in the runner's usable display, and await matching action layout after resizing.
Fail explicitly if the display cannot fit that minimum or the click target is
outside the usable screen. A second CI run computed x=1035 for the original
1100-pixel default window and failed to position the pointer; real screen/pointer
diagnostics distinguish this display constraint from input permission failures.
CI confirmed 1024 x 684 usable space with the Dock visible, below the 960 x 720
minimum. The disposable native CI session enables Dock autohide and restarts
only its own validated Dock PID before testing. This changes no developer
desktop preferences and does not bypass OS input permissions.

## CI regression repair refinement

Run 43702620205 exposed six Kotlin and three native failures after separation.
Repair test contracts, not production behavior: the shared scaffold source
helper must select the current `View Markdown` mode; undo input must use Meta
on macOS and Ctrl elsewhere; the real-workspace preview must activate current
`Toggle Documents panel`/`Toggle Review panel` controls and verify selected to
deselected state before capture. Keep exact editor/navigation/undo assertions,
all nine native cases, genuine OS-input coverage and CI-only guards unchanged.
Verify the undo path in Linux CI and About/preview paths only in isolated macOS
CI. Newly reached failures require diagnosis, not skips or weaker assertions.

## Risk Management and Security Risk Management

Both apply proportionately.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| Shared pointer or foreground windows affect user work | Separate source set; task and class guards before UI initialization; CI-only native execution | Environment markers are an execution policy, not a security sandbox |
| Green Rust CI conceals Kotlin/native defects | Distinct required checks; expected test coverage; reject skips/prerequisite failures | New required contexts must be verified and configured server-side |
| Stale selectors or platform input hide UI regressions | Current production semantics, exact state assertions, Linux JVM and isolated macOS native checks | Green automation does not establish human usability or approval |
| Timing/focus/permissions cause false results | Dedicated macOS session, supported JBR, bounded observable readiness and diagnostics | Hosted runner capability must be demonstrated; no permission bypass |
| CI artifacts expose user data | Synthetic temporary workspaces; no local captures; minimal permissions | Generated artifacts still require review before external sharing |
| Test failure leaves input pressed or windows running | Finally cleanup; serial native tests and isolated test JVMs | Human OS interaction/accessibility review is still necessary |

No Rust/core dependency or production architecture change is planned. No
approval, human test result or completed native fix is implied by this design.
