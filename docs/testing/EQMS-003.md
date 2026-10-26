---
title: EQMS-003 test specification
status: draft
related: [EQMS-003]
---

# EQMS-003 tests

Requirements are in [EQMS-003 requirements](../requirements/EQMS-003.md).

| Test ID | Requirement mapping | Method and expected result |
|---|---|---|
| T003-01 | R003-01, R003-02 | Serialize and deserialize representative values for all six entities through the versioned model contract; values and field names round-trip. |
| T003-02 | R003-02 | Confirm serialization emits version 1 and deserialization rejects an unsupported future version and malformed envelope. |
| T003-03 | R003-03 | Verify UUID strings serialize lowercase and hyphenated; invalid UUID input is rejected. |
| T003-04 | R003-04 | Normalize equivalent offsets to the same UTC `Z` value; reject invalid RFC 3339 timestamps and verify round-trip stability. |
| T003-05 | R003-05 | Accept valid namespaced plugin type IDs and nested JSON extension values; reject malformed namespace/type IDs without interpreting the extension. |
| T003-06 | R003-06, R003-07 | Manual review confirms event/signature fields remain descriptive data and documentation makes no persistence, cryptographic, trusted-time, approval, or compliance claim. |

## Manual checklist — pending

- [ ] Human reviews the model boundary, risk assessment, requirements, mappings,
  and automated evidence.
- [ ] Human inspects serialized examples for clarity and compatibility.
- [ ] Reviewer records the manual outcome before merge.
