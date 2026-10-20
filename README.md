# eQMS

Modern eQMS supporting quality-management work for **ISO 13485**, **ISO 81001**, and **IEC 62304 Class C**.

The target architecture is a **CLI-first, AI-first, offline-first** quality management system with a **Rust core and native CLI**, plus Kotlin desktop/web UI shells. Speed and usability are product requirements, with performance budgets based on measured workloads rather than language choice.

The Rust foundation provides a native `qms` binary with help/version, workspace
operations, and structured errors. Build it with
`cargo build --workspace --release --locked`, then run `./target/release/qms --help`.
`qms init`, `qms verify`, `qms recover`, and
`qms index rebuild` manage a Git-backed workspace and its rebuildable Rust-owned
SQLite index. The Kotlin CLI/JVM SQLite bootstrap remains a prototype;
desktop/web scaffolds are retained. See [PLANNING.md](PLANNING.md) for the roadmap,
[EQMS-005 workspace contract](docs/architecture/EQMS-005.md), and
[Build and Run](docs/operations/BuildAndRun.md) for commands.

## Core direction

- **Canonical artifacts** use a minimal Markdown-based format for documents, procedures, policies, and records.
- **History and auditability** combine Git-backed documents and readable diffs with a hash-chained audit journal. Local hashes alone do not prevent complete history replacement or prove signer identity.
- **Core modules** cover ledger/auditability, signatures, traceability, search, and release management.
- **`qms init`** should bootstrap a configurable eQMS, including roles, approval rules, policies, and procedures.
- **Standards modules** should add configurable ISO 13485 and IEC 62304 draft scaffolds and checks after minimal workspace initialization. Templates support compliance work; they do not establish compliance by themselves.
- **Presentation adapters** should derive DOCX, PDF, and HTML from canonical content without making rendered files authoritative.
- **Dogfooding** means using our own CLI to manage project documentation as controls become available, without inventing historical approvals.
- **Agent access** should be supported through a read-only MCP-facing interface for compliance inspection and gap analysis.
- **Human-facing UI** comes later, with native desktop first and web/Wasm after the core is stable.

See [`docs/`](docs/) for architecture notes and module decisions, including:
- [`docs/requirements/UserNeeds.md`](docs/requirements/UserNeeds.md)
- [`docs/operations/BuildAndRun.md`](docs/operations/BuildAndRun.md)
