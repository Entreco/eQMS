---
title: Ledger verification requirements
status: draft
related: [EQMS-031, UN-004, UN-010, UN-012, UN-015]
---

# EQMS-031 requirements

| ID | Requirement |
|---|---|
| R031-01 | Verify every baseline journal in chronological linear Git ancestry; reject invalid encoding/schema/sequence/hash, rewrites/truncation and historical content-binding mismatches. |
| R031-02 | Verify all captured saved draft histories independently, including their creation baseline's membership in verified baseline ancestry; never combine divergent journals. |
| R031-03 | Accept an optional literal full trusted commit checkpoint only in baseline ancestry; report local consistency when absent and explicitly describe trust limitations. |
| R031-04 | Reject unsupported shallow/replacement/graft/merge histories; verification must not repair/rewrite canonical content, refs or index. |
| R031-05 | Report the first encountered broken baseline/draft segment with commit/document and available line/sequence/path context, returning nonzero without partial success output. |
| R031-06 | Expose human and schema-version-2 JSON `audit.verify-ledger` contracts with existing usage/operation exit conventions, without changing existing workspace/draft/search commands. |
| R031-07 | Reject promisor/partial repositories before object traversal, disable lazy fetching in shared Git readers, and reject detectable broken/dangling refs instead of silently omitting them. |
| R031-08 | Batch historical tree/blob reads and cache content hashes by object ID while checking every commit's paths/modes; growing bindings must not cause quadratic subprocess work. |

Design/risk: [EQMS-031](../architecture/EQMS-031.md).
These are draft requirements; identity, signatures, publication, compliance,
deleted-ref inventory and future event producers are not delivered here.
