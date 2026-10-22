---
title: EQMS-092 Jewel scaffold tests
status: draft
related: [EQMS-092]
---

# EQMS-092 test specification

Issue: #50. Define mappings before implementation. Preserve existing test IDs.

| ID | Requirements | Planned check |
|---|---|---|
| T092-01 | R092-01, R092-07 | Resolve pinned Jewel/Compose artifacts, compile JVM and Wasm, verify no Material UI imports and no Jewel in Wasm dependencies. |
| T092-02 | R092-02, R092-03 | Compose tests: welcome, real create/open callbacks, dark/light toggle retains project, dirty editor and search. |
| T092-03 | R092-04 | Existing ProjectScreenTest and desktop project/process adapter tests: selection, creation, folder errors, cancellation and busy guards. |
| T092-04 | R092-05 | Existing DocumentScreenTest: save/conflict retention, historical read-only, comparison, navigation/close guards; add comparison failure regression. |
| T092-05 | R092-06 | Existing DocumentSearchTest, DocumentScreenTest and desktop search adapter tests: groups, pagination, latest/history selection and exactly-once render timings. Preserve benchmark opt-in; measure real prepared fixture if available. |
| T092-06 | R092-08 | Automated keyboard focus/activation check; human review of focus, readability, status honesty, both themes, scrolling and native dialog integration remains pending. |
| T092-07 | R092-01, R092-09 | Build native CLI, launch real app under JBR against isolated sample drafts, capture dark/light views if available. Record environment and exact limitations; never claim human review from automation. |

Run focused selectors while iterating, then:

```sh
./gradlew :core:ui:jvmTest :apps:desktop:test :core:ui:compileKotlinWasmJs :apps:web:compileKotlinWasmJs
cargo build --workspace --release --locked
./gradlew :apps:desktop:run
```

Evidence: [EQMS-092 observed results](evidence/EQMS-092.md).
Human review/testing must remain unchecked until actually performed.
