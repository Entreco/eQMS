# EQMS-083: Native document rendering

Status: draft. Issue #36. Rust owns one Markdown parser and a versioned semantic
tree. Native Compose and a deterministic HTML adapter consume that same tree.
Canonical Markdown, Git history, content hashes and lifecycle rules do not change.

## Contract

Semantic schema 1 and renderer `eqms-markdown-1` sit inside the existing native
schema-2 envelope. Nodes carry kind, optional text/target/anchor/level and children.
Supported syntax: CommonMark plus tables, strikethrough and footnotes. Heading
anchors use deterministic readable slugs with collision suffixes, including
duplicate/empty/Unicode headings. TOC derives from those same nodes. Raw HTML is literal text, never
executed. Unresolved references retain the parser's literal source behavior.

Saved rendering resolves exact native snapshots and includes ID/revision/hash.
Buffer rendering accepts at most 8 MiB JSON stdin and labels its separate
SHA-256 of exact UTF-8 body bytes as an unsaved input digest, not a canonical
document hash. No saved approval/hash is attributed to modified text. Rendering never
saves, transitions, writes caches to canonical storage or modifies Git/journals.

Body limit: 1 MiB UTF-8; tree depth 64, nodes 100,000, HTML 16 MiB. Over-limit
preview fails explicitly while Source/save remain available. Native parsing and
decoding run off the UI thread; edit updates coalesce for 100 ms and completion
is bound to the exact tab/buffer. Target warm input-to-layout p95: 150 ms for
50 KiB and 500 ms for 1 MiB mixed documents; report actual results, never assume.

## Presentation and resources

Preview/Source/Split default to Preview per tab. Independent source/preview
scroll, TextFieldValue and mode survive tab switches. Source is always reachable
after a rendering error. Split has bounded keyboard/pointer resizing.
Use Jewel typography/colors, visible focus, readable width, themed tables/code,
locally scrolling wide blocks, a collapsible heading TOC and footnote navigation.

Images resolve only within project-root `assets/`, through anchored native
no-follow file reads. Permit PNG/JPEG, at most 10 MiB encoded and 16 megapixels;
check dimensions before decode and bound decoded caching to 64 MiB. Historical
views show unversioned-asset placeholders rather than current mutable files.
Current assets are explicitly not covered by the document hash. No asset import,
versioning, remote loads, data/file URIs, symlinks or arbitrary filesystem access.
HTML uses safe image placeholders, not uncontrolled remote resource requests.
Links allow internal anchors and explicit confirmed HTTP/HTTPS/mailto launch
through the desktop adapter. Unknown targets are visible diagnostics.

## Risk Management and Security Risk Management

Both apply. Misrendering, omitted nodes, stale previews and misleading provenance
can change interpretation of a quality record. Preserve source, share semantics,
show unsupported-content/errors, bind async completions and retain editing guards.
Raw HTML, unsafe URLs, path traversal and oversized images/content can execute
content, leak data or exhaust resources. Escape HTML, restrict URI schemes,
disable automatic networking, use anchored no-follow reads and enforce bounds.
Hashes/identity/time are not authenticated approval. Mutable images are not
historical evidence. Manual UX/accessibility review remains required.

PDF pagination/page numbering/repeated headers and footers belong to #35;
DOCX belongs to #33. No rich-text editing, signature or compliance claims.

The initial secured asset reader is Unix-only. Non-Unix hosts explicitly return
an unsupported-resource error and keep alt text visible; they do not fall back
to weaker filesystem reads. Native Windows/Linux usability and platform-specific
asset integration require separate host verification.

Dependencies: pulldown-cmark 0.13 (MIT) for semantic parsing; CLI image 0.25
(MIT OR Apache-2.0) with PNG/JPEG only for dimensions, and base64 0.22
(MIT OR Apache-2.0) for bounded transport. Cargo.lock pins resolved versions.
No browser runtime or Kotlin parser is introduced. Parser's default HTML helper
and CLI features are disabled; the serializer consumes our semantic contract.
