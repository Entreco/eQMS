# eQMS Planning Tickets

This file defines an initial ticket backlog for implementing the CLI-first, AI-first eQMS core.

## Milestone 0 — Project foundation

### EQMS-001: Initialize Kotlin Multiplatform workspace
**Type:** Feature  
**Priority:** P0  
**Description:** Create the base KMP project structure for shared core, CLI app, and future desktop/web apps.  
**Acceptance criteria:**
- Repository contains `apps/` and `core/` structure.
- Shared core module compiles.
- CLI module depends on shared core.

### EQMS-002: Add embedded SQLite persistence layer
**Type:** Feature  
**Priority:** P0  
**Description:** Implement database access, migration framework, and baseline schema management.  
**Acceptance criteria:**
- SQLite DB is created/opened by CLI.
- Migration runner applies ordered migrations.
- Schema version is tracked.

### EQMS-003: Define canonical artifact model
**Type:** Feature  
**Priority:** P0  
**Description:** Define core entities (artifact, version, trace link, ledger event, release, signature).  
**Acceptance criteria:**
- Shared model types exist and are versioned.
- IDs and timestamps follow one normalized format.
- Model supports plugin-defined artifact types.

---

## Milestone 1 — CLI bootstrap and governance setup

### EQMS-010: Implement `qms init` command
**Type:** Feature  
**Priority:** P0  
**Description:** Create initial project bootstrap command with filesystem and DB initialization.  
**Acceptance criteria:**
- `qms init` creates baseline project config.
- Command is idempotent or safely fails with clear message.
- Init logs created resources.

### EQMS-011: Add init profiles (startup-medtech / iec62304-class-c)
**Type:** Feature  
**Priority:** P0  
**Description:** Provide profile-based setup for compliance-oriented defaults.  
**Acceptance criteria:**
- `qms init --profile startup-medtech` works.
- `qms init --profile iec62304-class-c` works.
- Profile output is deterministic and documented.

### EQMS-012: Bootstrap QMS policies and procedures
**Type:** Feature  
**Priority:** P0  
**Description:** Generate initial controlled docs (quality manual, doc control, design control, software, risk, CAPA, release/config).  
**Acceptance criteria:**
- Init creates Markdown artifacts with stable IDs.
- Generated docs are linked to policy/procedure taxonomy.
- Docs are in Draft state by default.

### EQMS-013: Define role model and approval matrix
**Type:** Feature  
**Priority:** P0  
**Description:** Add configurable roles and signature requirements for artifact types and releases.  
**Acceptance criteria:**
- Roles include Author, Reviewer, Approver, QA, Regulatory, Security Officer, Release Authority, Administrator.
- Approval rules are config-driven.
- Validation blocks approval if required roles are missing.

---

## Milestone 2 — Document and signature core

### EQMS-020: Implement controlled Markdown artifact format
**Type:** Feature  
**Priority:** P0  
**Description:** Define front matter schema and canonical formatting rules for controlled docs.  
**Acceptance criteria:**
- Parser validates required metadata.
- Canonical formatter normalizes output.
- Stable content hash is reproducible.

### EQMS-021: Implement document lifecycle workflow
**Type:** Feature  
**Priority:** P0  
**Description:** Add state transitions: Draft -> In Review -> Approved -> Effective -> Obsolete/Superseded.  
**Acceptance criteria:**
- Invalid transitions are rejected.
- Transition events are recorded.
- CLI commands exist for lifecycle operations.

### EQMS-022: Implement signature engine
**Type:** Feature  
**Priority:** P0  
**Description:** Add cryptographic signing over canonical artifact version and approval action.  
**Acceptance criteria:**
- Signature binds identity + role + artifact version hash.
- Signature invalidates on content change.
- Signature verification command reports pass/fail details.

---

## Milestone 3 — Ledger and auditability

### EQMS-030: Implement append-only ledger event store
**Type:** Feature  
**Priority:** P0  
**Description:** Persist immutable audit events for content, trace, signature, and release actions.  
**Acceptance criteria:**
- Events are append-only.
- Event hashes include previous hash reference.
- Tamper attempts are detectable.

### EQMS-031: Implement ledger verification command
**Type:** Feature  
**Priority:** P0  
**Description:** Add `qms audit verify-ledger` command to verify chain integrity.  
**Acceptance criteria:**
- Full ledger verification available from CLI.
- Command returns non-zero on integrity failure.
- Failure output identifies first broken segment.

---

## Milestone 4 — Traceability and release integrity

### EQMS-040: Implement traceability graph
**Type:** Feature  
**Priority:** P0  
**Description:** Create graph model and storage for artifact links with typed relationships.  
**Acceptance criteria:**
- Link types are validated against registry.
- CLI supports create/remove/list links.
- Graph queries support inbound/outbound traversal.

### EQMS-041: Implement traceability matrix generation
**Type:** Feature  
**Priority:** P0  
**Description:** Generate matrix views and gap reports for requirement coverage.  
**Acceptance criteria:**
- Matrix export supports markdown and JSON.
- Missing links are flagged clearly.
- Report can filter by release baseline.

### EQMS-042: Implement release baseline engine
**Type:** Feature  
**Priority:** P0  
**Description:** Create immutable release manifests over document versions + evidence links.  
**Acceptance criteria:**
- Release creation includes artifact snapshot and manifest hash.
- Release can be signed and verified.
- Release diff command compares two baselines.

---

## Milestone 5 — Search performance

### EQMS-050: Implement `SearchEngine` abstraction
**Type:** Feature  
**Priority:** P0  
**Description:** Define search interface independent of backend implementation.  
**Acceptance criteria:**
- Query contract includes term, filters, pagination, ranking fields.
- CLI search command uses interface only.
- Backends are swappable via config.

### EQMS-051: Implement SQLite FTS5 backend
**Type:** Feature  
**Priority:** P0  
**Description:** Build baseline full-text indexing and query with metadata filtering.  
**Acceptance criteria:**
- Artifact content and metadata are indexed.
- Incremental reindex works on content changes.
- Query latency target defined and measured.

### EQMS-052: Add Rust/Tantivy backend spike
**Type:** Spike  
**Priority:** P1  
**Description:** Evaluate Tantivy integration and benchmark versus FTS5 on representative datasets.  
**Acceptance criteria:**
- Benchmark harness exists.
- Results include indexing speed, query latency, relevance quality.
- Decision doc recommends keep/replace/hybrid.

---

## Milestone 6 — AI-first read access (MCP)

### EQMS-060: Implement read-only MCP server
**Type:** Feature  
**Priority:** P0  
**Description:** Expose core inspection capabilities over MCP for agent consumption.  
**Acceptance criteria:**
- MCP tools expose read-only artifact/search/trace/release/audit operations.
- No write/sign/approve/delete operations are exposed.
- Access policy is explicit and testable.

### EQMS-061: Add compliance check commands for agents
**Type:** Feature  
**Priority:** P0  
**Description:** Provide machine-friendly checks for missing docs, missing approvals, broken traces, and release gaps.  
**Acceptance criteria:**
- Output available in concise JSON mode.
- Each finding has stable code, severity, and artifact references.
- Command supports project-wide and release-scoped checks.

### EQMS-062: Implement minimal serialization mode
**Type:** Feature  
**Priority:** P1  
**Description:** Provide low-noise payload mode optimized for LLM/context efficiency.  
**Acceptance criteria:**
- Redundant prose fields can be omitted in minimal mode.
- IDs, status, links, and signatures remain intact.
- Output schema is documented and versioned.

---

## Milestone 7 — Plugin system

### EQMS-070: Define plugin API v1
**Type:** Feature  
**Priority:** P0  
**Description:** Create extension contracts for artifact types, commands, validators, reports, and migrations.  
**Acceptance criteria:**
- Plugin API is versioned.
- Capability model prevents bypassing core ledger/signature/release controls.
- Plugin load failures are isolated and reported.

### EQMS-071: Build Software module (IEC 62304)
**Type:** Feature  
**Priority:** P0  
**Description:** Add software lifecycle artifacts and checks for Class C evidence generation.  
**Acceptance criteria:**
- Module adds lifecycle templates and trace rules.
- Checks detect missing lifecycle evidence.
- Module integrates with release verification.

### EQMS-072: Build CAPA module
**Type:** Feature  
**Priority:** P1  
**Description:** Add CAPA workflows, linked evidence, and closure criteria.  
**Acceptance criteria:**
- CAPA records support root cause and action tracking.
- CAPA links to requirements/releases/incidents.
- Closure requires configured approvals.

### EQMS-073: Build STRIDE/security module
**Type:** Feature  
**Priority:** P1  
**Description:** Add threat modeling artifacts and security traceability checks for ISO 81001 evidence.  
**Acceptance criteria:**
- Threat entries link to mitigations and tests.
- Security findings impact release readiness checks.
- Module supports minimal JSON compliance output.

---

## Milestone 8 — Interoperability and human output

### EQMS-080: Implement DOCX export adapter
**Type:** Feature  
**Priority:** P1  
**Description:** Export controlled Markdown artifacts to DOCX for human workflows.  
**Acceptance criteria:**
- Export preserves artifact IDs and version metadata.
- Exported docs include source hash/reference.
- Export is deterministic for same input.

### EQMS-081: Implement DOCX import/conversion adapter
**Type:** Feature  
**Priority:** P2  
**Description:** Convert DOCX into canonical Markdown artifacts with validation warnings.  
**Acceptance criteria:**
- Import maps metadata into front matter.
- Non-conforming sections are flagged.
- Imported artifact receives new controlled version.

### EQMS-082: Implement PDF module
**Type:** Feature  
**Priority:** P2  
**Description:** Generate controlled PDF outputs for approvals/submissions.  
**Acceptance criteria:**
- PDF includes artifact ID/version/signature summary.
- Rendered output is traceable to source hash.
- Batch export for release manifest is supported.

---

## Milestone 9 — Desktop and web clients

### EQMS-090: Build desktop shell (offline-first)
**Type:** Feature  
**Priority:** P2  
**Description:** Create native desktop client consuming core APIs.  
**Acceptance criteria:**
- Users can browse/search artifacts offline.
- Users can review traceability matrix and release status.
- No business logic duplicated outside shared core.

### EQMS-091: Build web/Wasm client
**Type:** Feature  
**Priority:** P3  
**Description:** Provide browser client for read and guided workflows.  
**Acceptance criteria:**
- Web client uses shared contracts.
- Performance baseline documented.
- Feature scope aligns with read-first agent/human needs.

---

## Cross-cutting tickets

### EQMS-100: Compliance mapping matrix
**Type:** Feature  
**Priority:** P0  
**Description:** Map implemented controls/features to ISO 13485, ISO 81001, IEC 62304 Class C clauses.  
**Acceptance criteria:**
- Mapping exists in version-controlled artifact form.
- Each control maps to evidence-producing commands/modules.
- Gaps are tracked as explicit backlog items.

### EQMS-101: CLI JSON output standard
**Type:** Feature  
**Priority:** P0  
**Description:** Define one consistent machine-readable output schema across commands.  
**Acceptance criteria:**
- Shared envelope includes status, findings, references, timestamps.
- `--json` output is stable and documented.
- Error states use non-zero exit code with structured payload.

### EQMS-102: Benchmark suite for core operations
**Type:** Feature  
**Priority:** P1  
**Description:** Create repeatable benchmarks for search, trace queries, ledger verification, and release checks.  
**Acceptance criteria:**
- Benchmark dataset definitions are versioned.
- Baseline performance report can be regenerated.
- Regression thresholds are defined.

### EQMS-103: Security hardening baseline
**Type:** Feature  
**Priority:** P0  
**Description:** Add baseline controls for cryptography choices, key handling, auditability, and local data protection.  
**Acceptance criteria:**
- Cryptographic primitives and parameters are documented.
- Secret/key material handling rules are enforced.
- Security checks are integrated into compliance commands.

---

## Suggested first sprint (P0 execution order)

1. EQMS-001, EQMS-002, EQMS-003  
2. EQMS-010, EQMS-011, EQMS-012, EQMS-013  
3. EQMS-020, EQMS-021, EQMS-022  
4. EQMS-030, EQMS-031  
5. EQMS-040, EQMS-041, EQMS-042  
6. EQMS-050, EQMS-051  
7. EQMS-060, EQMS-061  
8. EQMS-100, EQMS-101, EQMS-103
