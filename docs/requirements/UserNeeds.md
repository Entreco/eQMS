---
title: User Needs
status: draft
owner: eQMS
last_updated: 2026-10-20
related:
  - EQMS-001
---

# User Needs

This document captures initial user needs for the eQMS baseline and assigns stable IDs for future traceability.

## Scope

- Product posture: CLI-first, AI-first, offline-first
- Domain: ISO 13485, ISO 81001, IEC 62304 Class C oriented quality management
- Stage: early bootstrap and controlled artifact foundation

These are draft needs, not assertions of implemented functionality or verified
compliance. The target is a Rust core/native CLI with Kotlin desktop/web shells;
see [the roadmap](../../PLANNING.md) for delivery sequencing.

## Users

- **Admin**: defines and maintains quality processes and controlled documents
- **Approver**: reviews and signs required artifacts according to role-based rules
- **Software**: creates and updates artifacts with traceable change history
- **Member**: creates and updates documents with traceable change history
- **Reviewer**: inspects evidence and checks process conformance
- **AI**: performs read-only analysis and consistency checks on repository artifacts

## User needs (initial set)

- **UN-001**: As Admin, I need to initialize a new eQMS workspace with baseline procedures, policies, and roles so the team starts from a controlled template.
- **UN-002**: As Software, I need controlled artifact versioning so every change has a clear history and accountable authoring trail.
- **UN-003**: As Member, I need offline-capable operation for core workflows so quality work is reliable without constant network access.
- **UN-004**: As Member, I need machine-readable command outputs so automation and AI-assisted workflows can consume results consistently.
- **UN-005**: As Reviewer, I need immutable audit evidence linking artifacts, approvals, and releases so I can verify compliance quickly.
- **UN-006**: As Reviewer, I need traceability views between requirements, risks, tests, and releases so impact and coverage are explicit.
- **UN-007**: As Approver, I need role-based approval and signature workflows so document releases are auditable and compliant.
- **UN-008**: As AI, I need read-only structured access to eQMS data so I can run gap checks and inspections safely.
- **UN-009**: As Member, I need fast, predictable commands and responsive UI workflows so quality work does not become a bottleneck; performance must be measured on representative workloads.
- **UN-010**: As Software, I need a native CLI with stable structured output and explicit errors so automation does not depend on a UI or JVM installation.
- **UN-011**: As Member, I need compact, presentation-independent documents so large document sets remain manageable and changes are easy to review.
- **UN-012**: As Reviewer, I need readable version diffs and verifiable audit linkage so I can investigate changes and detect inconsistencies within a documented trust model.
- **UN-013**: As Admin, I need installable ISO 13485 and IEC 62304 modules so I can generate applicable draft documents and processes, tailor them, and identify missing evidence without treating templates as proof of compliance.
- **UN-014**: As Member, I need Word, PDF, and HTML views tied to canonical document versions so I can share readable outputs without losing provenance.
- **UN-015**: As Software, I need to manage this eQMS project's own documentation through its CLI so real development work exposes usability and traceability gaps.

## Traceability notes

- User need IDs are intentionally stable (`UN-###`) and should be referenced by future feature tickets, artifacts, and test evidence.
- New user needs should extend this list instead of renumbering existing entries.
