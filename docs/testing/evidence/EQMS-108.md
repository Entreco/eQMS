---
title: EQMS-108 product identity evidence
status: draft
related: [EQMS-108]
---

# EQMS-108 evidence

Issue: #79. Automated observations only; not approved compliance evidence.
Manual review/testing remains pending.

## Tested revision and environment

- Revision: `4cea3a5acf2cd710e902ef2d1e92b8a0738b1204` (base `82c5303`).
- macOS 26.6, OpenJDK 25.0.4, Gradle wrapper (local, non-CI).
- `qms` binary: a previously built `target/release/qms` from the main
  checkout (revision not recorded) via `EQMS_QMS_PATH`.

## Commands and outcomes

```sh
./gradlew :feature:scaffold:jvmTest :apps:desktop:test --continue --console=plain
EQMS_QMS_PATH=<path-to-qms> ./gradlew :apps:desktop:test --rerun-tasks
```

| Test ID | Outcome |
|---|---|
| T108-01..T108-04 | `ProductIdentityTest`, `ProductIdentityUiTest` passed. |
| T108-06 | `:feature:scaffold:jvmTest`: 29 cases, 0 failures. `:apps:desktop:test` passed when `EQMS_QMS_PATH` was set; without it `DesktopLifecycleProcessTest` failed only because no `qms` binary existed in the new worktree. |
| T108-05 | Not automated; manual review pending. |

## Limitations

- Native integration tests (`integrationTest`, which include the existing
  "DutchDoxx <version>" About assertion) are CI-only and were not run locally.
- The About window layout (height raised to 480 dp) was not visually reviewed.
- Attribution is UI-level and not tamper-proof against a modified fork.
- Licence and trademark questions are open (see design).
