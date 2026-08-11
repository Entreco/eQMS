# eQMS

Blockchain-based eQMS for **ISO 13485**, **ISO 81001**, and **IEC 62304 Class C**.

The project is designed as a **CLI-first, AI-first, offline-first** quality management system built on a shared Kotlin Multiplatform core, with focused Rust integration for performance-sensitive modules such as search.

## Core direction

- **Canonical artifacts** use a minimal Markdown-based format for documents, procedures, policies, and records.
- **Core modules** cover ledger/auditability, signatures, traceability, search, and release management.
- **`qms init`** should bootstrap a configurable eQMS, including roles, approval rules, policies, and procedures.
- **Agent access** should be supported through a read-only MCP-facing interface for compliance inspection and gap analysis.
- **Human-facing UI** comes later, with native desktop first and web/Wasm after the core is stable.

See [`docs/`](docs/) for architecture notes and module decisions.
