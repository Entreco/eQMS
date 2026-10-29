# EQMS-021: Document lifecycle workflow

## Status and scope

This design covers the first lifecycle slice for the Git-backed documents
introduced by EQMS-023. It adds review and effective (published) states to an
existing document draft. Cryptographic signatures remain EQMS-022; this
workflow records lifecycle actions but does not prove reviewer identity or
approval authority.

## Lifecycle

The initial transition graph is intentionally one-way:

| Current state | Allowed next state |
|---|---|
| Draft | In Review |
| In Review | Approved |
| Approved | Effective |
| Effective | Obsolete, Superseded |
| Obsolete | None |
| Superseded | None |

An unlisted transition is rejected. Rework/rejection and return-to-draft
transitions are not added implicitly; they require a future scoped decision.
Effective is the internal publication state, not publication to an external
system.

## Persistence and API

Lifecycle changes are metadata events for the existing document identity and
content revision; they do not rewrite canonical Markdown. Each accepted event
records the document ID, prior and target state, and the content hash it
applies to. The current state is reconstructed from verified durable events,
not an in-memory cache. Lifecycle events are committed only to the isolated
document draft ref and its journal; they do not append to the baseline journal
or change the project working tree or index. Existing journal/hash verification
and draft-ref isolation remain authoritative. Content saves are accepted only
while the document is Draft, so later content changes cannot silently bypass
the lifecycle or imply a rework transition.

Every transition requires the revision the caller observed. The operation
checks the current revision while holding the repository's existing mutation
lock and publishes the new draft history only if its expected parent still
matches. A stale request fails without replacing a newer lifecycle state.
Persistence failures are surfaced and must not be reported as a successful
transition.

The native CLI exposes lifecycle transitions and state/history inspection
through `qms document transition`, `status`, and `lifecycle-history`.
Human-readable and versioned JSON output are supported consistently with
existing document commands. Lifecycle history is shown in event order;
document history retains each verified draft revision. CLI, filesystem, and
Git details remain outside Rust domain rules; the Kotlin shells are not the
source of truth.

## Risk Management and Security Risk Management

Both are applicable because these transitions alter quality-record state and
are persisted from local repository input.

| Hazard | Mitigation | Residual limitation |
|---|---|---|
| An invalid or stale action makes a document effective | Explicit transition graph, expected-revision check, mutation lock, compare-and-swap publication | External Git processes can ignore the application lock |
| State and its event disagree after a failed write | Verify the event chain, bind events to document ID and content hash, fail explicitly on incomplete persistence | Arbitrary filesystem damage may require manual recovery |
| Draft approval is mistaken for an authenticated signature | Label lifecycle status accurately; document that approval events are not signatures | No role authorization, cryptographic signature, or trusted identity proof |
| Effective is mistaken for external publication or regulatory release | Define Effective as the internal lifecycle publication state; exclude external release integration | No external distribution or regulatory acceptance is established |
| Local history is rewritten or its author/time is overstated | Reuse existing verification and trust-limit language; do not claim trusted time or authenticated identity | No remote trust anchor or protection against replacement of the full local history |

## Dependencies and limitations

EQMS-023 (#15) provides the existing draft and revision model. EQMS-030 (#18)
remains open; this design may use only event/journal behavior present and
verified in the repository and does not claim that broader work is complete.
EQMS-022 (#17) remains a separate signature engine. No review assignment,
multi-party collaboration, rejection workflow, external publication, or
cross-document reference validation is introduced.
