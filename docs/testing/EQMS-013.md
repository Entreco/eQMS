# EQMS-013: Role model and approval matrix test specification

## Requirement mapping

| Test ID | Requirements | Planned verification |
|---|---|---|
| TEST-013-001 | REQ-013-001 | Core test parses all eight role identifiers, round-trips them, and rejects an unknown or differently cased name. |
| TEST-013-002 | REQ-013-002 | Core tests reject unknown fields, wrong schema version, empty list, duplicate role, invalid artifact type name, too many artifact types and oversized input; a valid matrix loads. |
| TEST-013-003 | REQ-013-003 | Core tests: all roles present is permitted; missing roles are listed in canonical order; extra held roles do not block; release rule is evaluated separately. |
| TEST-013-004 | REQ-013-004 | Core test: unknown artifact type is denied. |
| TEST-013-005 | REQ-013-005 | CLI integration tests run `approval validate-matrix` and `approval check` (permitted, blocked, unknown role, bad file, `--json`) and assert exit codes and output. |
| TEST-013-006 | REQ-013-006 | Review of module imports plus compilation of the core crate with no new dependencies. |
| TEST-013-007 | REQ-013-007 | CLI output test asserts the asserted-roles/no-authentication disclaimer; docs reviewed. |
| TEST-013-008 | Regression | Full workspace tests, rustfmt and clippy. |

## Execution and evidence

Run `cargo test --workspace --locked`, `cargo fmt --all -- --check` and
`cargo clippy --workspace --all-targets --locked -- -D warnings`. Record
results in `docs/testing/evidence/EQMS-013.md`. Automated checks do not
replace manual review and manual testing.
