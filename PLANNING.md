# eQMS Planning Tickets

This file defines an initial ticket backlog for implementing the CLI-first, AI-first eQMS core.

## Product premise and architecture

Existing eQMS tools often feel clunky, dated, and slow. We aim to build a modern,
responsive, easy-to-use eQMS that supports compliance work, automation, and AI
without sacrificing accountable human decisions.

- **Rust core and native CLI:** Rust owns domain logic, workspace operations,
  document/process creation, validation, history, and audit verification. The
  native `qms` executable is the primary interface; it must not require a JVM.
- **Kotlin UI shells:** Retain Kotlin desktop and web shells for a smooth human
  experience. They consume versioned core contracts rather than duplicate rules.
  Browser access requires a service adapter or a deliberately limited WASM
  interface; it cannot directly execute the native core or access arbitrary files.
- **Compact canonical documents:** Store presentation-independent Markdown with
  versioned metadata, stable identifiers, deterministic formatting, and explicit
  trace links. DOCX, PDF, and HTML are derived views, not authoritative content.
- **Git plus an audit journal:** Use one Git-backed workspace for snapshots,
  readable diffs, and history, together with a hash-chained audit journal binding
  actions to content hashes and identity references. Do not build separate Git
  and blockchain backends initially or introduce distributed consensus.
- **Storage authority:** Git records canonical content and journal versions.
  SQLite, if retained, is a rebuildable query/search index, not a competing
  source of truth. Define recovery for incomplete Git/journal operations.
- **Trust boundaries:** Local hash chains provide tamper detection against a
  trusted baseline, not immutability against an administrator replacing all
  history. Identity verification, signatures, trusted checkpoints, independent
  verification, and backups are separate controls. Never store private keys or
  credentials in Git.
- **Offline-first and AI-ready:** Core local workflows work offline; network
  services are optional adapters. CLI output, errors, and exit codes form a
  versioned automation contract; MCP initially exposes read-only operations.
- **Modular compliance support:** ISO 13485 and IEC 62304 modules provide
  configurable draft documents, processes, trace rules, and gap checks. Generated
  templates alone do not establish compliance; applicability, execution evidence,
  tailoring, and qualified review remain necessary.
- **Dogfooding:** Manage this project's own needs, designs, processes, and
  evidence through the eQMS as capabilities become available.
- **Measured speed:** Baseline native startup and operation latency on documented
  hardware and representative document sets, then set explicit budgets before
  accepting performance-sensitive features. Rust alone is not proof of speed.

## Status and roadmap conventions

This is a roadmap, not a claim that the described controls are implemented.
Existing IDs remain stable. EQMS-001 delivered the Kotlin bootstrap prototype;
the uncommitted EQMS-002 SQLite first pass remains prototype work, not a complete
ordered migration framework. Preserve that work while transitioning deliberately.
All other tickets below are planned unless explicitly stated otherwise.

## Milestone 0 — Project foundation

### EQMS-001: Initialize Kotlin Multiplatform workspace
**Status:** Completed prototype; retained as UI-shell foundation  
**Type:** Feature  
**Priority:** P0  
**Description:** Create the base KMP project structure for shared core, CLI app, and future desktop/web apps.  
**Acceptance criteria:**
- Repository contains `apps/` and `core/` structure.
- Shared core module compiles.
- CLI module depends on shared core.

### EQMS-002: Add embedded SQLite persistence layer
**Status:** Prototype first pass; role revised to derived indexing  
**Type:** Feature  
**Priority:** P0  
**Description:** Evaluate and adapt the existing JVM SQLite prototype into a Rust-owned, rebuildable query/index layer after the Git/journal contract is established. It must not become the authoritative document or audit store.  
**Acceptance criteria:**
- Rust opens/creates the derived database when indexing is needed.
- Ordered migrations are transactional, versioned, and tested for upgrade, repeat execution, and failure rollback.
- Index schema version is tracked and unsupported future versions are rejected.
- The index can be rebuilt from canonical workspace data without losing history.

### EQMS-003: Define canonical artifact model
**Type:** Feature  
**Priority:** P0  
**Description:** Define Rust core entities (artifact, version, trace link, ledger event, release, signature) and versioned external contracts independent of UI and storage adapters.  
**Acceptance criteria:**
- Shared model types exist and are versioned.
- IDs and timestamps follow one normalized format.
- Model supports plugin-defined artifact types.

### EQMS-004: Establish Rust core and native CLI workspace
**Status:** Implemented on feature branch; manual review/testing pending
**Type:** Feature  
**Priority:** P0  
**Dependencies:** None  
**Description:** Add a Cargo workspace containing a reusable domain library and native `qms` executable; retain Kotlin desktop/web scaffolds and preserve the JVM prototype during transition.
**Acceptance criteria:**
- Core library has no dependency on a UI or CLI presentation framework.
- Native CLI supports help/version and documented structured errors/exit codes.
- Cargo build, unit tests, and CLI smoke tests run through documented commands.
- Kotlin cmd is explicitly documented as a prototype rather than a second authoritative implementation.
- Baseline startup measurements record platform, hardware, build profile, and methodology.

### EQMS-005: Define Git/journal workspace and consistency contract
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-004  
**Description:** Define and implement the minimal versioned workspace layout, Git adapter, canonical journal encoding, and genesis integrity checks needed by initialization.
**Acceptance criteria:**
- Config, documents, module manifests, and journal have documented locations and schema versions.
- Journal entries hash canonical event data and the previous event hash; they bind content hashes without a circular dependency on their containing Git commit.
- Initial commit records config and genesis journal entry; no signing or approval is implied.
- Mutation serialization, partial-write recovery, and independent integrity verification are specified and tested.
- Threat model explicitly covers whole-history replacement, untrusted timestamps/identity claims, and trusted checkpoint limitations.

---

## Milestone 1 — CLI bootstrap and governance setup

### EQMS-010: Implement `qms init` command
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-004, EQMS-005, minimal EQMS-101 output contract  
**Description:** First user-facing vertical slice: `qms init <path>` creates a standalone Git-backed eQMS workspace, without requiring a database, standards templates, signatures, or UI.  
**Acceptance criteria:**
- Creates versioned config, document/module directories, a genesis journal event, and an initial Git commit.
- Explicitly handles Git author identity; does not invent an accountable user or imply verified identity.
- Paths containing spaces work; existing non-empty directories and initialized workspaces are rejected with clear non-zero errors without overwriting data.
- I/O failures report their cause and clean up only resources created by this attempt, or leave an explicitly recoverable state.
- Human-readable and JSON output identify the workspace and created resources.
- Integration tests cover creation, reopen/genesis verification, repeated init, existing data, and I/O failure.

### EQMS-011: Add init profiles (startup-medtech / iec62304-class-c)
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-010, EQMS-014, EQMS-015  
**Description:** Provide optional profile-based setup composed from explicit module selections; plain initialization stays minimal.  
**Acceptance criteria:**
- `qms init --profile startup-medtech` works.
- `qms init --profile iec62304-class-c` works.
- Profile output is deterministic and documented.

### EQMS-012: Bootstrap QMS policies and procedures
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-020, EQMS-014, EQMS-015  
**Description:** Generate configurable draft docs (quality manual, doc control, design control, software, risk, CAPA, release/config) from selected modules, without asserting completeness or approval.  
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

### EQMS-014: Add declarative module installation
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-010, EQMS-020  
**Description:** Implement `qms module add <module>` using bundled, versioned declarative manifests/templates before introducing executable plugins.
**Acceptance criteria:**
- Manifests declare version, dependencies, artifact types, templates, and validation rules.
- Installation creates deterministic draft artifacts and records module/version provenance in history and journal.
- Repeat installation and upgrades never silently overwrite user edits; conflicts and incompatible versions are explicit.
- Installing modules cannot bypass document lifecycle or audit controls.

### EQMS-015: Build ISO 13485 module
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-014  
**Description:** Provide `qms module add iso13485` with configurable QMS document and process scaffolds.
**Acceptance criteria:**
- Applicable quality manual, document/record control, design control, supplier, training, and CAPA scaffolds are documented and generated as drafts.
- Module identifies its standards edition, applicability decisions, tailoring needs, and gaps.
- Reviewed clause-to-artifact mappings reference standards without reproducing licensed text.
- Output explicitly distinguishes scaffolding from implemented processes and compliance evidence.

### EQMS-016: Dogfood project documentation
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-010, EQMS-020, EQMS-023, EQMS-031  
**Description:** Use the native CLI to manage this repository's own User Needs, design decisions, and process documents.
**Acceptance criteria:**
- Existing Markdown is imported without losing content or stable need IDs.
- Creation, validation, history/diff, and integrity verification have a documented end-to-end workflow.
- Imported documents retain draft status until a real review/approval process occurs.
- CI checks canonical documents and integrity against the declared trust baseline.

---

## Milestone 2 — Document and signature core

### EQMS-020: Implement controlled Markdown artifact format
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-003  
**Description:** Define front matter schema and canonical formatting rules for controlled docs.  
**Acceptance criteria:**
- Parser validates required metadata.
- Canonical formatter normalizes output.
- Stable content hash is reproducible.

### EQMS-023: Add document creation and history commands
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-003, EQMS-010, EQMS-020, EQMS-030  
**Description:** Create and inspect compact documents/process definitions through the native CLI.
**Acceptance criteria:**
- `qms document create`, `qms document history`, and `qms document diff` have documented human/JSON contracts.
- Creation validates metadata, assigns stable artifact IDs, and defaults to Draft.
- Each recorded revision binds canonical content to Git history and the audit journal.
- Diff/history identify exact versions; invalid paths and conflicting edits fail explicitly without data loss.

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
**Dependencies:** EQMS-005  
**Description:** Extend the genesis journal into a hash-chained audit trail for content, trace, signature, and release actions, under the documented trust model.  
**Acceptance criteria:**
- Core operations append events and never silently rewrite recorded events.
- Event hashes include previous hash reference.
- Verification detects changed, reordered, or missing events against a trusted baseline; documentation explains the limits of detecting full-history replacement.

### EQMS-031: Implement ledger verification command
**Type:** Feature  
**Priority:** P0  
**Dependencies:** EQMS-030  
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
**Description:** Evaluate Tantivy as a Rust-core search backend and benchmark versus Rust-accessed SQLite FTS5 on representative datasets; Rust is no longer an optional search-only bridge.  
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
**Dependencies:** EQMS-014, EQMS-015  
**Description:** Provide `qms module add iec62304` with software lifecycle scaffolds and checks, including configurable Class C evidence needs.  
**Acceptance criteria:**
- Module adds lifecycle templates and trace rules.
- Checks detect missing lifecycle evidence.
- Module integrates with release verification.
- Manifest states standards edition and applicability/classification decisions; templates start in Draft and do not imply completed activities or compliance.

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

### EQMS-083: Implement HTML rendering adapter
**Type:** Feature  
**Priority:** P1  
**Dependencies:** EQMS-020  
**Description:** Render canonical documents as HTML without coupling core content to presentation.
**Acceptance criteria:**
- Output preserves document IDs, version references, and source hashes.
- Rendering is reproducible for fixed content, template, and renderer versions.
- Untrusted document content is handled safely; rendered output cannot change canonical records or imply approval.

---

## Milestone 9 — Desktop and web clients

### EQMS-090: Build desktop shell (offline-first)
**Type:** Feature  
**Priority:** P2  
**Description:** Evolve the Kotlin desktop shell to consume versioned Rust-core capabilities through an explicit adapter, preserving offline-first UX.  
**Acceptance criteria:**
- Users can browse/search artifacts offline.
- Users can review traceability matrix and release status.
- No business logic duplicated outside shared core.

### EQMS-091: Build web/Wasm client
**Type:** Feature  
**Priority:** P3  
**Description:** Evolve the Kotlin web/Wasm shell for read and guided workflows through a versioned service adapter or explicitly limited WASM interface. Browser code must not assume direct native filesystem access.  
**Acceptance criteria:**
- Web client uses shared contracts.
- Performance baseline documented.
- Feature scope aligns with read-first agent/human needs.
- Core controls are enforced at the adapter boundary rather than trusted to the browser.

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
**Description:** Baseline native startup, initialization, document operations, search, trace queries, ledger verification, and release checks before agreeing operation-specific performance budgets.  
**Acceptance criteria:**
- Benchmark dataset definitions are versioned.
- Baseline performance report can be regenerated.
- Regression thresholds are defined.
- Reports identify hardware, platform, release build settings, data size, and warm/cold conditions; targets are not inferred from language choice.

### EQMS-103: Security hardening baseline
**Type:** Feature  
**Priority:** P0  
**Description:** Add baseline controls for cryptography choices, key handling, auditability, and local data protection.  
**Acceptance criteria:**
- Cryptographic primitives and parameters are documented.
- Secret/key material handling rules are enforced.
- Security checks are integrated into compliance commands.
- Identity assurance, trusted checkpoints, independent verification, and backup/restore address risks a local hash chain alone cannot solve.

---

## Execution sequence and first feature

1. **Foundation:** EQMS-004 and the minimal EQMS-101 contract, then EQMS-005.
2. **First user-facing feature:** EQMS-010 (`qms init <path>`). Accept only with
   the documented creation/failure tests and a verifiable genesis event.
3. **Document core:** EQMS-003, EQMS-020, EQMS-030/031, then EQMS-023 and EQMS-016
   for real document creation, diff/history, and dogfooding.
4. **Standards scaffolding:** EQMS-014, EQMS-015, EQMS-071; compose profiles and
   generated policies through EQMS-011/012.
5. **Governance and evidence:** EQMS-013, EQMS-021/022, EQMS-040/041/042 and
   reviewed EQMS-100 mappings. Security/trust work in EQMS-103 begins with the
   storage contract and grows alongside these controls.
6. **Scale and integrations:** Derived SQLite/indexing EQMS-002, search
   EQMS-050/051/052, MCP EQMS-060/061/062, and executable plugin API EQMS-070
   after declarative modules establish requirements.
7. **Human experience:** Rendering EQMS-080/081/082/083 and Kotlin shells
   EQMS-090/091 consume established contracts.

EQMS-102 measurements begin with native CLI foundations and expand with each
operation. No UI, rendering adapter, full plugin runtime, or standards module is
a prerequisite for the first `qms init` feature.
