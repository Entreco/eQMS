---
title: EQMS-111 automated evidence
status: draft
related: [EQMS-111]
---

# EQMS-111 automated evidence

Tested revision: `ab144d0` (branch `issue/eqms-111-project-chrome-settings`, stacked on `issue/eqms-110-project-creation-branding`). Environment: macOS, JDK 25, Gradle wrapper. Draft record, not approved evidence.

| Command | Outcome |
|---|---|
| `./gradlew :apps:desktop:test :feature:ui:jvmTest :feature:document:jvmTest :feature:search:jvmTest :feature:scaffold:jvmTest --continue` | passed (T111-01..06, regressions) |
| `./gradlew :feature:ui:compileKotlinWasmJs :feature:scaffold:compileKotlinWasmJs :apps:desktop:compileIntegrationTestKotlin :feature:scaffold:compileIntegrationTestKotlinJvm` | passed (compile only) |

Limitations: no Rust changes, Rust suite not re-run for this revision. CI-only native window tests were updated but not executed locally (T111-07). Window behaviour, header layout on macOS (360 dp title box), accent visuals and the changed-elsewhere refusal are not verified by automated UI tests. No timing measured. Project board (#3) not updated: the `gh` token lacks project scopes. Manual review/testing pending.
