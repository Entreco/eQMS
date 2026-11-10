# EQMS-107 automated test evidence

## Tested revision and environment

- Source revision: `160729a6c229e854adb39181a49e187393267fe2` (tests ran on the
  same source; only documentation files were added in that commit)
- Environment: macOS arm64 (Darwin); Gradle 9.6.1; `EQMS_QMS_PATH` set to a
  release `qms` rebuilt from `main` (`7ee8068`).
- Evidence is committed separately from the tested source revision.

## Commands and outcomes

| Command | Outcome |
|---|---|
| `./gradlew :apps:desktop:test --tests '*WorkspaceCliTest' --tests '*DesktopDocumentCapabilitiesTest'` | Passed, including the two new TEST-107-001/002 tests. |
| `./gradlew :apps:desktop:test` | 58 tests, 3 skipped, 1 failed: `DesktopWindowNativeTest.nativeMouseClickReachesTheUnifiedTitleBarAction`. |

The failing native-window test also fails on unmodified `main` in this
environment (verified by stashing the change and running it alone); it is
unrelated to this change and was not fixed here.

## Limitations

Automated results on macOS arm64 only. The fix was not exercised in the running
desktop app; no human review or manual testing is claimed. The desktop still
does not detect a stale `qms` binary.
