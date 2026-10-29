# EQMS-021: Lifecycle test specification

## Requirement mapping

| Test ID | Requirements | Planned verification |
|---|---|---|
| TEST-021-001 | REQ-021-001, REQ-021-002 | Core transition table tests cover every allowed edge and reject all unlisted edges, including transitions out of terminal states. |
| TEST-021-002 | REQ-021-003, REQ-021-005 | Integration test executes the full lifecycle, reopens the project, verifies state/event history and content hash, and checks baseline, working tree, and index isolation. |
| TEST-021-003 | REQ-021-004 | Competing or stale transition requests fail without replacing the winning state or changing canonical document bytes. |
| TEST-021-004 | REQ-021-003, REQ-021-006 | CLI tests verify lifecycle event persistence, human output, versioned JSON shape, and nonzero error outcomes. |
| TEST-021-005 | REQ-021-005, REQ-021-007 | Regression tests cover document creation, save, reopen, history, diff, workspace initialization, and journal verification. |
| TEST-021-006 | REQ-021-008 | Output/documentation tests assert that lifecycle approval is not represented as a cryptographic signature, trusted identity/time, or external release. |
| TEST-021-007 | REQ-021-009 | Core and CLI tests verify that content cannot be saved after leaving Draft and that failed attempts preserve the current revision and document bytes. |

## Execution and evidence

Run the narrow Rust core and native CLI test targets first, then all affected
Rust workspace tests and formatting/lint checks. Record exact commands, target
revision, environment, outcomes, and limitations in
`docs/testing/evidence/EQMS-021.md` after implementation. Automated checks do
not replace the required manual review and manual testing.
