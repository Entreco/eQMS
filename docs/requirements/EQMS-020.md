---
title: Controlled Markdown requirements
status: draft
related: [EQMS-020, UN-002, UN-004, UN-010, UN-011, UN-012, UN-015]
---

# EQMS-020 requirements

| ID | Requirement |
|---|---|
| R020-01 | Expose a Rust-only reusable v1 codec with required schema/ID/title/Draft metadata; reject malformed UTF-8/header/JSON, duplicate/unknown/missing fields and unsupported values. |
| R020-02 | Canonical formatting uses fixed compact metadata order and LF line endings, trims titles under existing rules, and preserves all other Markdown body whitespace and final newline presence. |
| R020-03 | Strict validation rejects noncanonical encodings; hashes are reproducible SHA-256 over exact complete canonical bytes in existing `sha256:` representation. |
| R020-04 | Authoring/history reuse the codec without changing legacy bytes, IDs, hashes, journal linkage, strict expected-ID checks, save semantics or baseline isolation. |
| R020-05 | Native validate/format commands read files or explicit `-` stdin without workspace/identity requirements, never modify sources/history, and fail explicitly without partial success on invalid input/I/O errors. |
| R020-06 | Human formatting emits exact bytes; JSON uses the existing version-2 envelope with ID/schema/canonical/hash and formatting content. Usage exits 2; operation failures exit 1 on stderr. Existing command output remains compatible. |
| R020-07 | Documentation distinguishes format validation from history/identity/approval/compliance, retains Draft status, and excludes import, migrations, general artifact metadata and lifecycle controls. |

Design/risk: [EQMS-020](../architecture/EQMS-020.md). These draft requirements
support compact, stable documents and native automation; they are not approvals.
