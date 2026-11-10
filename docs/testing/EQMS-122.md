# EQMS-122: Test specification

| Test ID | Requirements | Verification |
|---|---|---|
| TEST-122-001 | REQ-122-001 | `DesktopDocumentCapabilitiesTest.usageErrorsWithoutCommandShowCliMessageButOtherMismatchesAreRejected` and `WorkspaceCliTest.usageErrorWithoutCommandSurfacesCliMessage` assert the CLI message is shown. |
| TEST-122-002 | REQ-122-002 | Same tests assert wrong-command, non-usage null-command and exit-0 cases are rejected. |
| TEST-122-003 | REQ-122-003 | Existing `:apps:desktop:test` suite. |

Run `EQMS_QMS_PATH=<release qms> ./gradlew :apps:desktop:test`. Automated checks
do not replace manual review/testing.
