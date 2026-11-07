---
title: EQMS-104 paginated canvas requirements
status: draft
related: [EQMS-104]
---

# EQMS-104 requirements

| ID | Requirement |
|---|---|
| R104-01 | The document workspace provides a `Canvas` view mode and defaults each tab to Canvas while retaining Source and Split modes. |
| R104-02 | Canvas editing uses the same Markdown-backed tab content and explicit Save behavior as Source; lifecycle, dirty-close, and conflict guards remain unchanged. |
| R104-03 | Project page setup supports page size (A4/US Letter), orientation (portrait/landscape), and independent margins with validated bounds and documented defaults. |
| R104-04 | Canvas renders repeated headers and footers with `{title}`, `{page}`, and `{total}` placeholders and displays automatic page numbering. |
| R104-05 | Page setup is project-scoped presentation configuration and does not by itself alter canonical document revisions, hashes, or lifecycle state. |
| R104-06 | Invalid stored page-setup data fails explicitly, falls back safely, and does not block opening/editing documents. |
| R104-07 | Existing preview rendering, source editing, save/history/lifecycle operations, and provenance/status messaging remain compatible. |

Hazards, mitigations, and limitations are defined in the
[EQMS-104 design](../architecture/EQMS-104.md).
