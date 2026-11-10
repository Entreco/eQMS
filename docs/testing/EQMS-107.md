# EQMS-107: Test specification

| Test ID | Requirements | Verification |
|---|---|---|
| TEST-107-001 | REQ-107-001 | `DesktopDocumentCapabilitiesTest.usageErrorsWithoutCommandShowCliMessageButOtherMismatchesAreRejected` and `WorkspaceCliTest.usageErrorWithoutCommandSurfacesCliMessage` assert the CLI message is shown. |
| TEST-107-002 | REQ-107-002 | Same tests assert wrong-command, non-usage null-command and exit-0 cases are rejected. |
| TEST-107-003 | REQ-107-003 | Existing `:apps:desktop:test` suite. |

Run `EQMS_QMS_PATH=<release qms> ./gradlew :apps:desktop:test`. Automated checks
do not replace manual review/testing.
