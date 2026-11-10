# EQMS-122: Desktop usage-error display requirements

| ID | Requirement |
|---|---|
| REQ-122-001 | When `qms` exits nonzero with a valid v2 error envelope having null command and code `INVALID_ARGUMENT`, desktop document and workspace operations shall report the envelope's error message. |
| REQ-122-002 | Desktop shall continue to reject responses naming a different command, error envelopes with null command and another code, and null-command errors with exit code 0, as an unexpected command. |
| REQ-122-003 | Existing successful and operational-error handling shall be unchanged. |
