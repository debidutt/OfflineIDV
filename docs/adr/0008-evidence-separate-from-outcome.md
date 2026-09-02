# ADR 0008: Separate verification evidence from product outcome

- Status: Accepted
- Date: 2026-08-02

## Context

MRZ checksum validity, chip access, printed/chip consistency, passive authentication, chip authentication, and face similarity prove different things. A Boolean erases limitations and can lead to false authenticity claims.

## Decision

Record structured signal type/status values, evaluate explicit configurable requirements, and return evidence and warnings alongside the final outcome. Never label a document genuine from a checksum or chip read alone, and never return verified before every required signal completes successfully.

## Consequences

UI and audit behavior can communicate what was and was not checked. Policy tests become essential and terminology must remain precise. Milestone 1 defines signal vocabulary only; the policy/decision implementation comes with later verification milestones.
