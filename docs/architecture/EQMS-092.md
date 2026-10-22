---
title: EQMS-092 Jewel desktop scaffold
status: draft
related: [EQMS-090, EQMS-023, EQMS-050, EQMS-092]
---

# Jewel desktop scaffold

Issue: #50. Jewel standalone is the desktop presentation toolkit. Its IntelliJ
New UI components suit a compact document workspace without introducing an IDE
platform dependency or changing Rust domain behavior. Jewel is Apache-2.0,
pre-1.0, desktop-only, and requires JetBrains Runtime (JBR). Prefer stable APIs.
See the [upstream setup and compatibility guidance](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md).

Pin a published Jewel/Compose combination and prove resolution/rendering before
migrating screens. Do not infer artifact availability from master release notes.
The verified combination is Jewel `0.41.0-262.10968.63`, Compose `1.12.0`,
Kotlin `2.4.10` and JBR 25. Matching IntelliJ platform icons supply built-in
component glyphs; API/implementation artifacts alone do not include them.
JBR launches the application and UI tests; Gradle compilation can use another
supported JDK. Custom title-bar decoration is deferred.

The controlled text-field adapter narrowly opts into Jewel's experimental
`TextFieldValue` overloads to preserve existing string-based workflow state
and cursor/IME selection without introducing asynchronously mirrored editing
state. Keep the opt-in confined to this adapter and revisit it on Jewel upgrades.
Revision selectors use stable `ListComboBox`, not experimental dropdown menus.

## Boundaries and behavior

`core:ui/commonMain` retains capability contracts, DTOs and reusable search
state. Desktop composables move to `core:ui/jvmMain`, which alone depends on
Jewel. `apps:desktop` retains filesystem and native process adapters unchanged.
Wasm remains buildable; the web bootstrap is not a working browser UI.
This supersedes the common-screen implementation boundary of R090-08, not its
platform capability separation or workflow requirements.

Use a dark-first standalone theme with a session light/dark toggle, native OS
title bar, compact project toolbar, project/document sidebar, one active editor
tab, Markdown editor, optional history/comparison panel, and status bar.
Theme changes do not reconstruct the workspace or discard edits/search state.
Welcome and empty states expose working Create/Open actions.

Preserve native verification, directory/new-folder behavior, explicit saves,
expected-revision conflicts, failed-save text, grouped/paginated saved-draft
search, historical read-only selection, revision diff, busy states, visible
errors, and dirty navigation/close guards. All statuses reflect observed state.
Drafts and configured identity/local timestamp claims retain their warnings.
No review, approval, signatures, publication, multi-buffer editor, docking,
rich-text editing, browser authoring, or runtime fallback is introduced.

## Risk applicability

Risk Management and Security Risk Management both apply proportionately.

| Hazard | Control | Residual limitation |
|---|---|---|
| UI migration loses edits | Preserve state ownership, dirty guards, save failures; test theme changes and navigation | External tools may alter saved drafts |
| Hidden errors or historical records appear editable | Visible errors, busy/read-only states, mapped regressions | Human usability review is still required |
| Decorative status implies approval or authenticated identity | Only observed workspace/draft state; retain history warnings | Local identity/time are claims, not trusted attestations |
| Unsupported runtime/dependency combination | Pin verified artifacts and require JBR for UI launches/tests | Runtime provenance and future library migrations remain operational responsibilities |
| Process or filesystem boundary weakened | Keep existing adapters and Rust validation unchanged | Existing local administrative trust assumptions remain |

Requirements and tests are in [EQMS-092 requirements](../requirements/EQMS-092.md)
and [test specification](../testing/EQMS-092.md). Draft records are not approvals.
