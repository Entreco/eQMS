---
title: EQMS-127 native test isolation requirements
status: draft
related: [EQMS-127]
---

# EQMS-127 requirements

Issue: #107. Defined before implementation.

| ID | Requirement |
|---|---|
| R127-01 | Ordinary local desktop/scaffold test/check runs exclude all four native-window classes and five audited About-window methods; they do not launch their native windows or manipulate OS input. |
| R127-02 | Dedicated desktop/scaffold integration compilations/tasks retain the native classes/methods, reuse fixtures and preserve internal access without widening production visibility. |
| R127-03 | Local Gradle or direct JUnit/IDE integration execution is rejected before window or Compose-rule initialization; no local GUI override. |
| R127-04 | PR/main CI runs safe desktop and affected feature JVM tests and native integration as separately named checks alongside unchanged Rust coverage. |
| R127-05 | Native CI uses isolated macOS/JBR 25, serial execution, synthetic fixtures and the built release CLI; missing prerequisites, failures and unexpected skips fail visibly. |
| R127-06 | Genuine OS click/drag/minimize/restore coverage is retained with bounded readiness, stage diagnostics and cleanup; the current failure is diagnosed from isolated evidence. |
| R127-07 | CI retains reports and sanitized synthetic captures on failure/success and verifies expected native discovery/execution. |
| R127-08 | Main's effective ruleset requires the successful Kotlin/native contexts and existing Rust check, preserving unrelated rules and bypass settings. |
| R127-09 | Revision-bound evidence records actual results, failures/exclusions and limits; draft PR and human review/testing remain explicit delivery gates. |
