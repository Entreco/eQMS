# EQMS-021: Lifecycle requirements

These requirements refine issue #16. `Effective` is the internal publication
state; lifecycle approval events are not cryptographic signatures.

| ID | Requirement |
|---|---|
| REQ-021-001 | The lifecycle shall expose Draft, In Review, Approved, Effective, Obsolete, and Superseded states for an existing document. |
| REQ-021-002 | The lifecycle shall allow only Draft -> In Review, In Review -> Approved, Approved -> Effective, and Effective -> Obsolete or Superseded. It shall reject every other transition. |
| REQ-021-003 | Each accepted transition shall persist a verifiable event bound to the document identity and content hash, and current state shall be recoverable after restart. |
| REQ-021-004 | A transition shall be conditional on the revision observed by the caller and shall fail explicitly if the document has changed. |
| REQ-021-005 | Lifecycle operations shall preserve the canonical document bytes and shall not change the controlled baseline, project working tree, or Git index. |
| REQ-021-006 | The native `qms` CLI shall provide lifecycle operations with human-readable and versioned JSON output; invalid operations and persistence failures shall return nonzero errors. |
| REQ-021-007 | Existing create, save, show, history, diff, initialization, and verification behavior shall remain compatible. |
| REQ-021-008 | User-visible lifecycle state shall not claim cryptographic signature, authenticated reviewer identity, trusted time, or external publication. |
| REQ-021-009 | Content saves shall be accepted only in Draft; lifecycle transitions shall not rewrite canonical document content or add implicit rework paths. |
