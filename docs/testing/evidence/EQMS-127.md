---
title: EQMS-127 native isolation and CI evidence
status: draft
related: [EQMS-127]
---

# EQMS-127 evidence

Issue: #107. Draft PR: #108, verified draft targeting `main`. Automated
observations only; not approved compliance evidence. Manual review/testing
remains pending. Delivery is incomplete while required checks fail.

## Tested revisions and environment

- Base inspected: `0fc8027fde2f55078e42a69b3f52862f8293cf33`.
- Local safe JVM suites: `c75a2153a780467544279c34537cf2ef74aef3af`;
  macOS, JBR 25, Gradle 9.6.1. No native integration was run locally.
- First complete CI coverage: `687e7c693f783af9df978e4b3841273127fe581b`,
  [Kotlin run 43700127756](https://github.com/Entreco/eQMS/actions/runs/43700127756).
  GitHub-hosted Ubuntu/JBR 25 for JVM tests, macOS 15/JBR 25 for native tests.
- Reproducible direct JUnit guard probe:
  `ade52e44f3e853b439f3b902700a64942f7a70c3`, local macOS/JBR 25.
- Follow-up CLI prerequisite implementation:
  `1d214af327a465ffff92361c018a54b547bafba9`;
  [run 43700656440](https://github.com/Entreco/eQMS/actions/runs/43700656440)
  completed with the lifecycle process test passing, six JVM failures and
  four native failures. Native pointer positioning failed with target x=1035.
- Display-aware native fixture:
  `71c40bdf9ee1b8104ca4d41cf2084d9f05a7431f`;
  [run 43701257830](https://github.com/Entreco/eQMS/actions/runs/43701257830)
  completed with six JVM and four native failures. The pointer fixture
  explicitly reported 1024 x 684 usable display space, below the 960 x 720
  minimum. Local compile and all nine direct class guards passed at this
  revision without running native UI.
- Final tested implementation:
  `b557e2552240ef77001d3ee6143b401bcb4ba63d`,
  [run 43701958118](https://github.com/Entreco/eQMS/actions/runs/43701958118).
  Native click/drag/minimize/restore passed with the display configuration
  repaired. Nine native cases executed: three other failures, zero skips.
  JVM coverage: 166 cases, six existing failures, three opt-in performance skips.

## Reproduction

```sh
./gradlew :apps:desktop:compileIntegrationTestKotlin \
  :feature:scaffold:compileIntegrationTestKotlinJvm
./gradlew :apps:desktop:test :feature:scaffold:jvmTest :feature:ui:jvmTest \
  :feature:document:jvmTest :feature:search:jvmTest \
  --continue --rerun-tasks --no-daemon --console=plain
python3 -m unittest discover -s scripts/tests -p 'test_verify_native_reports.py'
./gradlew -I scripts/verify_local_native_guards.gradle \
  :apps:desktop:verifyLocalNativeClassGuards \
  :feature:scaffold:verifyLocalNativeClassGuards
./gradlew :apps:desktop:check :feature:scaffold:jvmTest --dry-run
```

Local native task invocation is expected to fail before starting its test JVM.
Both `:apps:desktop:integrationTest` and `:feature:scaffold:integrationTest`
produced the explicit CI-only error. Do not set CI markers locally.

CI native execution (isolated macOS only) uses the workflow's built `qms`,
synthetic temporary preview directories and:

```sh
cargo build --release --locked -p eqms-cli
./gradlew --continue --no-daemon --console=plain \
  :apps:desktop:integrationTest :feature:scaffold:integrationTest
python3 scripts/verify_native_reports.py \
  apps/desktop/build/test-results/integrationTest \
  feature/scaffold/build/test-results/integrationTest
```

## Local outcomes

| Suite/check | Tests | Failed | Skipped | Outcome |
|---|---:|---:|---:|---|
| Desktop unit | 54 | 0 | 3 | Passed executed cases; no native-window cases in reports |
| Scaffold JVM | 24 | 5 | 0 | Failed existing source-field expectations |
| UI JVM | 6 | 0 | 0 | Passed |
| Document JVM | 74 | 0 | 0 | Passed |
| Search JVM | 8 | 0 | 0 | Passed |
| Report-verifier fixtures | 8 | 0 | 0 | Passed |
| Direct native-class initialization probe | 9 | 0 | 0 | All nine rejected with CI-only errors before UI initialization |

Both integration compilations contain actual sources and compiled successfully.
The targeted IDE build and error-severity inspections passed. An initial full
IDE build encountered the unrelated missing Wasm Binaryen 125 dependency;
targeted JVM builds succeeded. Initial scaffold integration configuration
errors and a NO-SOURCE discovery issue were corrected before the tested commits.

The three unit skips are opt-in rendering/search/responsiveness measurements
requiring native/benchmark environment or fixtures. They are excluded
performance coverage, not passing results. Native integration has no skips.

The initial ordinary JVM run still included About-window methods outside the
four desktop classes first identified. Those methods instantiate native windows;
the audit subsequently isolated all five with user approval. Final unit reports
exclude them. The original Robot pointer test was never executed locally.

## First complete CI outcomes

Run 43700127756 executed 166 JVM cases: 7 failures, 3 skips. Five scaffold
failures reproduce local missing `Markdown body`/`Document title` expectations.
An additional document-mode undo assertion fails on Linux, and the native
lifecycle process test could not find the built release CLI. The workflow now
builds `qms` before JVM tests; this prerequisite failure is not ignored.

All nine expected native cases executed: six passed, three failed, zero skips.

| Native case | Outcome |
|---|---|
| Title-bar native click, drag, minimize/restore | Passed |
| Production window layout/theme/close | Passed |
| Native menus/application handlers | Passed |
| Desktop About artwork/version | Passed |
| Native About lifecycle | Passed |
| Invalid preference/About version | Passed |
| Real-workspace Jewel preview | Failed: missing `Hide Documents panel` node |
| Settings/chooser/About background isolation | Failed: missing `Markdown body` node |
| About editor/exit guard | Failed: missing `Markdown body` node |

Native click diagnostics reported focused window, scale 1.0 and pointer target
matching the observed action bounds. This demonstrates that the native path
works on the isolated runner. It does not identify the precise cause of the
original local timeout: concurrent user input, focus/timing, permissions and
display coordinates were not isolated on the developer desktop.

Both report artifacts were retained and downloaded for XML inspection:
`native-integration-evidence` and `kotlin-test-reports`. The coverage verifier
failed on the native failures as designed; no continue-on-error or skip-based
success was introduced. Partial screenshots are diagnostic, not passing preview
coverage or human visual review.

## Repeated CI and display constraint

Run 43700656440 executed 166 JVM cases: six failures, three opt-in performance
skips. Building the CLI corrected the lifecycle process prerequisite failure;
the five scaffold failures and Linux document undo assertion still fail.

All nine native cases again executed with no skips. This time the pointer
positioning stage failed before clicking: a 1100-pixel default window produced
target x=1035 rather than the earlier x=959. The other three failures remained.
This disproves treating the first successful native run as proof of reliability.
The fixture now uses the existing minimum window size, centers it in the usable
display, waits for resized action layout, and rejects offscreen targets.
Screen bounds and actual-pointer diagnostics distinguish display limitations
from input permission/session failures; no retries or semantics substitutes
were added.

The final isolated CI environment enables Dock autohide to recover usable
vertical space, restarting only the runner user's validated Dock PID. The test
retains its minimum-size, screen-fit and native-pointer assertions. No developer
desktop preferences or input permissions are changed.

Run 43701958118 confirmed screen bounds 1024 x 768, usable bounds
`(0, 25, 1024, 743)`, and a 960 x 720 test window at `(32, 36)`. Its action
target `(927, 56)` was visible and the window focused. Native click, drag and
minimize/restore all passed. This repairs the demonstrated CI display failure,
without asserting that the exact original local timeout mechanism was proven.
The three other native and six JVM failures remain; both required jobs are red.

## CI setup and server policy

Initial run 43699880563 failed before Gradle testing because the wrapper JAR
was ignored/untracked. The restored wrapper matches the published Gradle 9.6.1
SHA-256 recorded in the design; subsequent CI reached tests. JBR provisioning
through setup-java v6.3.0 succeeded on both platforms with distribution checksum
verification support.

Effective main ruleset `20717003` was queried after updating it. It requires
`check`, `kotlin-tests`, and `native-integration`, bound to GitHub Actions.
Strict required checks, deletion/non-fast-forward rules, PR rules, conditions
and bypass actors were compared with the prior record and preserved.
Failures now block merge; rules are not evidence that manual review occurred.

## Remaining limitations and human gates

| Test mapping | Observed result |
|---|---|
| T127-01 | Native-source compilation and exclusion from local unit/task graphs verified |
| T127-02 | Both local task guards and all nine direct JUnit initialization guards verified |
| T127-03 | Failed: applicable scaffold and Linux document regressions remain red |
| T127-04 | All native cases executed; final native click/drag/minimize/restore, production window and menus passed after the display correction |
| T127-05 | Failed: preview reaches a missing `Hide Documents panel` control |
| T127-06 | Eight verifier fixtures pass; actual failed native cases make the CI verifier fail |
| T127-07 | All three required contexts and preservation of unrelated rules verified server-side |
| T127-08 | Draft/main target verified; revision-specific records retained, issue incomplete |
| T127-09 | Five About methods isolated; local initialization guards verified; two scaffold About cases still fail |

Existing scaffold/document/preview failures are not repaired or excluded by
this isolation change. Native click success is not proof of every display
scale, OS version, accessibility condition or concurrent-input scenario.
No local Robot execution, human testing, self-approval or merge is claimed.
Keep #107 open/In progress and #108 draft pending green applicable coverage
and manual review/testing.

- [ ] Review risk/design, requirements, tests and revision-bound evidence.
- [ ] Confirm local unit runs do not interrupt desktop work.
- [ ] Review native interactions, keyboard/focus, accessibility and visuals.
- [ ] Review failing tests, CI artifacts and effective required-check policy.
