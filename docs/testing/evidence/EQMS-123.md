# EQMS-123 automated test evidence

Draft, generated record; not approved compliance evidence. Manual review and manual testing are still required.

- Tested revision: `105fb82b7c758c2ace44a32faca2c1ffe4043e1e` (branch `issue/eqms-123-wysiwyg-editor`)
- Environment: macOS 26.6.2, OpenJDK 25.0.4, rustc 1.99.1, Gradle wrapper

## Commands and outcomes

| Command | Outcome |
|---|---|
| `cargo test -q` (workspace) | Pass; includes the schema 3 range test in `core/rust/src/render.rs` and CLI render expectations |
| `./gradlew :feature:document:jvmTest` | Pass; includes `WysiwygModelTest` (10), `WysiwygEditorStateTest` (6), `DocumentScreenTest` Edit-mode test and updated Source-mode tests |
| `./gradlew :feature:document:compileKotlinWasmJs` | Compiles |
| `./gradlew :apps:desktop:test` | 1 failure: `DesktopWindowNativeTest.nativeMouseClickReachesTheUnifiedTitleBarAction` timed out (needs a real native window/mouse; unrelated to document editing, not re-run on `main`). All other desktop tests pass, including `DocumentRenderDecoderTest` range validation |

## Requirement mapping

R123-01..11 map to tests T123-01..12 in `docs/testing/EQMS-123.md`. Automated coverage: model round-trips, block patching, undo, Enter/Backspace, marks, headings, link allow-list, read-only for non-Draft, default Edit mode, strict decoder ranges.

## Limitations

- Manual GUI checklist in `docs/testing/EQMS-123.md` is not executed; IME, accessibility, visual paper appearance and Wasm runtime behavior need human testing.
- Table editing covers pipe tables only; no merged cells. Editing sheets are continuous per page-break section; automatic pagination appears in Preview/Split.
- Documents over 1500 top-level blocks fall back to Markdown.
- Project board item could not be added: the available `gh` token lacks `read:project`/`read:org` scopes.
