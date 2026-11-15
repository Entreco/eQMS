---
title: EQMS-127 native isolation and CI evidence
status: draft
related: [EQMS-127]
---

# EQMS-127 evidence

Issue: #107. Draft PR: #108, verified draft targeting `main`. Automated
observations only; not approved compliance evidence. Manual review/testing
remains pending. The final remediation below has green automated checks;
historical failed and cancelled runs remain recorded, not rewritten as passes.

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

## Historical limitations and human gates before remediation

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

## Approved CI repair refinement

Historical failures above remain observations at their recorded revisions.
Run [43702620205](https://github.com/Entreco/eQMS/actions/runs/43702620205)
tested `68e103822341878809c21a100c2ae9488e196c74` and repeated six JVM and
three native failures. The user approved repairing these in the same draft PR
without production UI changes, new skips or local native execution.

Revision `695cd309eff0e0233647fc72d0ac22fc0a7966bf` corrected the shared
`View Markdown` helper, platform undo modifier and obsolete panel selectors.
Its code was tested locally before committing: macOS/JBR 25/Gradle 9.6.1.
No native integration execution or CI environment overrides occurred locally.

```sh
./gradlew --console=plain :feature:scaffold:jvmTest \
  --tests '*ScaffoldDocumentTest' :feature:document:jvmTest \
  --tests '*DocumentScreenTest.threeModesKeepLongSourceEditableAndPageBreakInsertionIsUndoable' \
  :apps:desktop:compileIntegrationTestKotlin \
  :feature:scaffold:compileIntegrationTestKotlinJvm
./gradlew --quiet --continue :apps:desktop:test :feature:scaffold:jvmTest \
  :feature:ui:jvmTest :feature:document:jvmTest :feature:search:jvmTest
./gradlew --quiet -I scripts/verify_local_native_guards.gradle \
  :apps:desktop:verifyLocalNativeClassGuards \
  :feature:scaffold:verifyLocalNativeClassGuards
python3 -m unittest discover -s scripts/tests -p 'test_verify_native_reports.py'
./gradlew --console=plain :apps:desktop:test :feature:scaffold:jvmTest --dry-run
```

| Local suite/check | Cases | Failures | Skips |
|---|---:|---:|---:|
| Desktop unit | 54 | 0 | 3 |
| Scaffold JVM | 24 | 0 | 0 |
| UI JVM | 6 | 0 | 0 |
| Document JVM | 74 | 0 | 0 |
| Search JVM | 8 | 0 | 0 |
| Report-verifier fixtures | 8 | 0 | 0 |
| Direct native-class guards | 9 | 0 | 0 |

Unit XML contained no native integration cases. The dry-run included only
ordinary test execution, not integration tasks. Both integration sources
compiled; targeted IDE builds and error-severity inspections passed with
existing Compose test-rule deprecation warnings. The nine guard cases were
explicit initialization rejections before native UI, not native feature passes.

[Run 43708326777](https://github.com/Entreco/eQMS/actions/runs/43708326777)
tested that revision on hosted Ubuntu/macOS 15 with JBR 25. Linux Kotlin:
166 cases, zero failures/errors, three unchanged opt-in performance exclusions.
Native: nine executed cases, eight passed, one failure, zero skips. Both
scaffold About cases now pass; the preview reached its new toggle control but
an immediate deselected assertion preceded native recomposition. Rust checks
passed. Reports were downloaded and their XML inspected; no failures were
excluded. This run is not passing native preview evidence.

Revision `02f406a612c9eff8573c52ff403f6ebd8ef7da48` adds a bounded wait for
the actual deselected native state before the unchanged collapse assertions.
It compiled locally through Gradle and the IDE, with no error-severity
inspection findings. Its native behavior must be verified in isolated CI.

[Run 43709234737](https://github.com/Entreco/eQMS/actions/runs/43709234737)
tested `02f406a612c9eff8573c52ff403f6ebd8ef7da48`: Kotlin and Rust checks
passed; native XML again contained nine cases, one failure and zero skips.
The preview passed collapse and subsequent theme/mode/tab assertions, reaching
the final image scroll for the first time. It failed to find decoded-image
semantics while scanning lazy pages. Production exposes a loading Text before
async decoding completes; image content-description semantics exist only
after decoding. This is a readiness prerequisite, not passing image coverage.

The next repair accepts the exact loading placeholder or decoded-image
semantics only as the scroll target, then retains the decoded-image wait,
repositioning, viewport bounds and final capture assertions. No production
rendering changes, exclusions or timeout increases were introduced.

Run [43710293154](https://github.com/Entreco/eQMS/actions/runs/43710293154)
tested `216ee52a9a996338bfc4333ae0452787239664bc`. Kotlin and Rust passed,
but desktop integration stalled and the run was cancelled after more than
23 minutes. Available scaffold XML records four passing cases with zero skips;
desktop XML is absent. The verifier failed on missing native reports as
required. Synthetic captures exist through both themes/modes, but the final
decoded-image capture is absent. These partial artifacts are not passing
desktop integration coverage. The exact blocked stack was not captured.

Revision `073e0641f0ec216fafb569a959bf542ad682cfd0` bounds the native workflow
execution step to ten minutes, both Gradle integration tasks to five minutes,
and logs individual case starts/results. The image search now traverses the
finite reported lazy item count through indexed scroll actions, avoiding
content-driven searching during asynchronous pagination changes; exact
placeholder/image, decoded-image and viewport assertions remain. Stage logs
identify image readiness. Gradle integration compilation, targeted IDE build,
and all nine direct local initialization guards passed before committing.
An initial build-script attempt used a shadowed `java.time` reference and failed;
explicit Duration imports corrected it, and both builds subsequently passed.
No native tests were launched locally. Timeout failure is not passing coverage.

[Run 43712402563](https://github.com/Entreco/eQMS/actions/runs/43712402563)
tested that revision. Rust and Kotlin passed, but native failed after 9m41s.
The Gradle log explicitly reports desktop integration exceeding its five-minute
deadline; the step finished below ten minutes, retaining available artifacts.
Case-start logs identify DesktopJewelPreviewTest. Buffered stdout records both
"locating workflow image across 3 lazy items" and "workflow image decoded".
The final image capture is absent: the subsequent `performScrollTo` helper
stalled, not image decoding. This demonstrates task-timeout containment, not
passing native coverage.

Revision `fa13afac747d523393bcb31038e21b869eaf629b` uses one measured ScrollBy
action to reposition the decoded image and retains the bounded full-viewport
assertion. It also streams case diagnostics immediately. IDE and Gradle
integration compilation passed before committing. An initial compile attempt
used nonexistent DpRect height properties; explicit bottom-minus-top
measurements corrected them. No production changes or local native execution.

## Final automated remediation outcome

Tested implementation: `fa13afac747d523393bcb31038e21b869eaf629b`.
[Kotlin/native run 43713403808](https://github.com/Entreco/eQMS/actions/runs/43713403808)
is successful on hosted Ubuntu/macOS 15, JBR 25, Gradle 9.6.1.
Native job duration: 3m20s, within the configured task/step limits.
Rust checks [43713399350](https://github.com/Entreco/eQMS/actions/runs/43713399350)
and [43713403857](https://github.com/Entreco/eQMS/actions/runs/43713403857)
also passed at that revision.

Downloaded `native-integration-evidence` and `kotlin-test-reports` XML confirms:

| CI coverage | Cases | Failures/errors | Skips | Result |
|---|---:|---:|---:|---|
| Linux Kotlin JVM | 166 | 0 | 3 | All executed cases pass; unchanged opt-in performance exclusions |
| macOS native integration | 9 | 0 | 0 | All required cases pass |

The native report verifier also passed on the downloaded artifact directories.
The final `native-document-image-dark.png` is present after the decoded image
and full-viewport assertions. Screenshots are synthetic diagnostic artifacts,
not human visual approval.

| Test mapping | Final observed result |
|---|---|
| T127-03, T127-10 | All five scaffold source/navigation regressions and both native About cases pass |
| T127-11 | Exact page-break undo body equality passes on Linux with Ctrl; local macOS Meta path also passed |
| T127-05, T127-12 | Complete native preview passes panel collapse, tab retention, both-theme modes and decoded-image viewport checks |
| T127-04, T127-06 | All nine native cases pass without skips, including genuine title-bar click/drag/minimize/restore; report gate passes |
| T127-13 | Five-minute Gradle timeout failed explicitly in run 43712402563; final normal native run completes within limits; ten-minute step ceiling remains configured |

No test skips, relaxed native coverage or production UI changes were introduced.
Historical timing/failure observations are preserved above. Evidence is committed
separately from the tested implementation. Main requires all three check names;
manual review/testing remains unchecked, #107 open/In progress and #108 draft.
