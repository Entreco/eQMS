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

Kotlin presentation lives under `feature/`, not Rust-only `core/`:

| Gradle module | Responsibility | Dependencies |
|---|---|---|
| feature:ui | Shared Jewel fields, dialogs, selectable rows and focus styling | Compose/Jewel only |
| feature:search | Common search contracts/state, JVM query/group/pagination presentation | feature:ui |
| feature:document | Common document contracts and JVM editor/history/diff | feature:search, feature:ui |
| feature:scaffold | Common project capabilities and JVM project shell/chooser/theme/navigation | feature:document, feature:ui |

Each module retains common contracts/state and a Wasm target; Jewel composables
are JVM-only. Public contract dependencies use Gradle `api`; implementation-only
UI dependencies do not leak through the graph. Search accepts injected document
list content and selection callbacks, never depends back on the editor or
scaffold, and owns render timing at its actual result-list layout.
The desktop app depends on the scaffold and its exposed capability contracts.
`apps:desktop` retains filesystem and native process adapters unchanged.
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

## Pre-PR legacy module cleanup

The user requested removal of obsolete Kotlin `core:search` and `core:ledger`
before PR creation. `SearchModule`/`LedgerModule` are placeholders; the only
executed consumer is the Kotlin CLI's SQLite bootstrap. Remove that bootstrap
and JDBC dependency too, leaving the Kotlin CLI an explicit placeholder.
Do not migrate or delete existing database files. Rust search/index, document,
workspace and authoritative native CLI behavior remain unchanged.

The user subsequently approved moving/splitting `core:ui` into the feature
modules above. `core/` now contains only the existing Rust crate, whose
document/search/workspace/index modules remain unchanged. Independent Rust
crates are deferred until dependency boundaries justify a separate change.
PR creation remains paused by the user.

Cleanup risks: dangling Gradle references and accidentally removing native
functionality or user data. Mitigate with all-app compilation, dependency
inspection, CLI placeholder execution and no changes to Rust or databases.
The intended loss of prototype SQLite initialization is explicit, not a
replacement implementation or a successful native operation.

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

## Approved document workspace increment

Issue #50 reuses EQMS-092 for the document lifecycle UI, depending on #16 and
draft PR #60. The user approved stacking `feature/eqms-092-document-workspace`
on lifecycle tip `4be3b76054d283fb885f91b0ce79da2f5afd0d0f`, rather than
main. This supersedes single-active-tab/no-review scope above, not historical
evidence or EQMS-093's unified chrome. The eventual draft PR targets the
lifecycle branch until the user decides to consolidate.

Use the supplied mockup for structure only: selectable left Project/Search
tools, central closable document tabs and selectable right Review/History
tools. Panels collapse/resize with bounded widths; defaults are Project and
Review open. Layout and tabs are session-only. Do not invent a folder hierarchy
or implement docking, rich text, comments, reviewer assignment or traceability.

One project-scoped document state owner holds every tab's loaded snapshot,
expected revision, title/body, dirty/error state and history. Latest tabs
deduplicate by document ID; read-only historical tabs by ID and exact revision.
New tabs have stable local keys until creation. Tab switching retains edits
without prompting; hiding panels/theme changes must not destroy buffers.
Async reads apply only to their source project/tab and must not overwrite
dirty text. UI writes are serialized; external changes fail through Rust's
expected-revision checks. Successful writes refresh search separately: a
failed index refresh is not a failed save/transition.

The scaffold delegates destructive navigation to the document workspace
rather than duplicating dirty state decisions. Closing dirty tabs offers
Save/Discard/Cancel; project switching/closing and quit offer Save all/Discard
all/Cancel. Invalid titles or failed saves stop navigation and retain text.
Partial Save all keeps successful saves and names/focuses the failed tab.
Cancelled/failed project opening retains the prior workspace; discard and
workspace destruction happen only on a successful, confirmed navigation.

The common capability contract carries typed status/events; apps:desktop
strictly decodes all six native statuses and invokes transition/history with
literal arguments and versioned envelopes. Missing/unknown status fails rather
than defaulting to Draft. Rust alone validates the transition policy. UI action
availability is advisory: Draft -> Submit for review, In Review -> Approve,
Approved -> Publish (Effective), Effective -> Mark obsolete/Mark superseded.
All actions require confirmation and a clean, saved, latest tab. Historical
and terminal tabs cannot transition. After submission content is read-only;
rework is unavailable. The Review tool explains these restrictions.

Publish is only the existing internal Effective state: no baseline merge,
immutable release record, external distribution or regulatory acceptance.
Approval is a local lifecycle event, not signing, authenticated reviewer
identity or role authorization. History preserves exact statuses/revisions,
content/event hashes and configured identity/local-time limitations.
Search hits without status use neutral saved-document/revision wording.

Risk Management and Security Risk Management both apply. Lost buffers and
wrong-tab responses are mitigated by project/tab-owned state, source identity
checks and save/close guards. Stale transitions and protocol errors fail
explicitly and cannot change observed status optimistically. Confirmations
and accurate limits mitigate false approval/publication assurance. Existing
direct process boundaries, native verification and local Git trust limitations
remain; no new trusted clock, remote identity or cryptographic signature is
provided. Human usability/accessibility and manual workflow testing remain
required before merge.

## Icon-only tool rails (stacked follow-up)

Issue #50 continues on `feature/eqms-092-tool-rail-icons`, explicitly stacked
from #61 at `c80db99374821535702be29b455dd05119c6aab5` instead of main.
Only the four rail controls change: compact Jewel selectable icon buttons,
themed bundled IntelliJ icons, stable accessible names and hover tooltips.
Project/Search stay left, Review/History right; panel headings remain text.
Use a 44dp rail with 36dp hit targets and 20dp icons. Selection and keyboard
focus remain visible in both themes. Shared JVM UI metadata owns names, icons,
side and shortcut numbers; no dependency or native/domain change is needed.

Tooltips and the workspace preview-key handler share the mapping: macOS
Command+1/2/3/4, Windows/Linux Alt+1/2/3/4, in Project/Search/Review/History
order. Match top-row digits with exactly the designated modifier, key-down
only, suppress repeats until release, and clear held keys on focus loss.
Mouse and keyboard use one live busy/modal-guarded toggle path. Opening a
tool does not steal editor focus; hiding a focused panel returns focus to its
rail button. Scope keys to the document workspace, never global AWT hooks.
Unrelated keys pass through; modal windows retain their own input.

Both risk disciplines apply proportionately: icon-only controls reduce
discoverability, and shortcuts can accidentally hide content or bypass guards.
Mitigate with truthful tooltips, stable labels/selection/focus, exact modifier
matching, repeat suppression and mapped editor/modal/busy tests. Toggling must
not save, mutate lifecycle state, destroy buffers or reset widths/search.
No new filesystem/network capability or trust boundary is introduced.
Cross-platform native runtime, human usability and assistive-technology review
remain manual limitations, not inferred approvals.

### UI/UX review refinement on #62

UI/UX excellence is a core product principle. Tool panels and the central
editor use clipped 10dp rounded surfaces separated by quiet 8dp split gutters.
Both side boundaries drag horizontally with an east/west resize cursor when
enabled; resizing either boundary also resizes the central editor. Retain
bounded widths and keyboard minus/plus controls, with no free-floating docking.
Hover hints anchor beside the icon, vertically centered on its bounds rather
than below the moving cursor, and open inward on both rails.

Replace action-button tabs with compact IntelliJ-style editor tabs: Markdown
glyph, quiet inactive labels, rounded selected surface, integrated close icon,
dirty marker and historical qualifier. Reuse Jewel icon/button/theme tokens.
The pinned Jewel TabStrip hardcodes non-focusable tabs and a generic close
label and does not propagate enabled to tab selection; use a small shared
tab primitive to retain descriptive close names, keyboard focus and real
disabled semantics rather than bypass existing guards to adopt that API.
Selection stays horizontally scrollable and automatically reveals the active
tab. No decorative chevron or separate close-button column.

Both risk disciplines remain applicable: mitigate accidental closing and
content starvation with existing dirty-close guards and bounded split widths;
retain focus, selected/disabled semantics and buffer ownership during cosmetic
changes. Test actual drags, disabled drags, cursor feedback, tooltip geometry
on both sides and tab keyboard/close behavior. Manual visual/accessibility and
physical platform checks are still required.

The subsequent visual review found that clipping alone was insufficient:
surfaces matched the shell, and editor-tab hover tokens did not visibly change
the whole tab. Use a distinct neutral panel fill (darker in dark mode, white
in light mode) against the unchanged shell. Blend a visible theme-foreground
hover overlay over the entire tab, including while its close child is hovered;
disabled tabs do not react. Keep selected styling independently visible.
Pixel-level regressions must verify fill contrast, exposed rounded corners and
hover changes away from the close icon, for selected/inactive tabs in both themes.
