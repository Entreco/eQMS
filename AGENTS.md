# Issue delivery workflow

## Mandatory session startup and delivery gates

Before changing code for an issue, read this file, inspect the issue and its
dependencies, check the project status, and confirm the current branch and
worktree. Treat the workflow below as required, not advisory. Do not report an
issue as complete while a required record, test, evidence item, or draft PR is
missing.

For every issue, perform these steps in order and link records by issue ID:

1. Create a new issue branch from the latest `origin/main`. Fetch the latest
   base first when possible. Preserve unrelated changes; never silently reuse a
   branch for a different issue. If a suitable issue branch already exists,
   verify its base, issue scope, and worktree before continuing. If branch
   creation or fetching is blocked, stop before implementation and report the
   blocker rather than working on an unapproved branch.
2. Refine the ticket, including scope, acceptance criteria, dependencies, and the
   applicability of Risk Management and Security Risk Management. Document the
   rationale, hazards, mitigations, and residual limitations; do not invent approvals.
3. Finalize the design in `docs/architecture/`.
4. Define traceable requirements in `docs/requirements/`.
5. Define tests and requirement mappings in `docs/testing/` before implementation.
6. Implement the feature without expanding scope silently.
7. Execute all applicable tests, including affected regressions. Record failures
   and justify exclusions. Missing tests are not passing functional coverage.
8. Generate reproducible test evidence in `docs/testing/evidence/`, identifying
   the tested revision, environment, commands, outcomes, and limitations.
9. Update documentation to match actual implemented behavior.
10. Create a **draft** PR for the issue branch with an issue reference and links
    to risk assessment, design, requirements, tests, evidence, and a manual
    review/testing checklist. Confirm that the PR targets the intended base and
    is actually marked draft. If a PR already exists, verify that it is the
    correct issue's PR and update it as needed; do not create a duplicate.

If the available GitHub integration cannot create/update a branch, project item,
or draft PR, use an available authenticated mechanism if one is configured and
safe to use. Never expose credentials. If the required write operation still
cannot be performed, leave the issue explicitly incomplete, report the exact
blocked delivery step, and provide the branch/commit and ready-to-use PR
details. Do not silently skip the step or imply that work has been delivered.

## Review and evidence

All PRs require **manual review and manual testing before merge**. Agents must
not self-approve, automatically merge, or claim human tests were executed.
Leave manual review/testing unchecked and the issue open pending merge.
Draft records and generated templates are not approved compliance evidence.
These instructions do not themselves enforce GitHub branch protection.

Use stable requirement/test IDs. Record observed automated results accurately;
never fabricate evidence. Identify the code revision tested before committing
evidence separately. Exclude secrets, personal paths, and confidential logs.

## Project tracking

Every roadmap issue must appear once on
https://github.com/users/Entreco/projects/3. Preserve its EQMS identifier.
Set Todo before work, In progress during implementation and review, and Done
only after completion and manual review/testing. Do not silently change priority.
Keep GitHub issue scope and acceptance criteria aligned with repository records.

## Architecture

Rust core/domain logic must not depend on CLI or UI frameworks. The native
`qms` CLI is authoritative; the Kotlin CLI remains a prototype. Retain Kotlin
desktop/web shells. Canonical documents are compact Markdown; planned Git and
audit-journal controls must not be represented as already implemented.
