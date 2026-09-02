# ADR 0001: Implement Android first

- Status: Accepted
- Date: 2026-08-02

## Context

Atlas must demonstrate an offline passport flow on Android while retaining a credible iOS architecture. Building both platforms concurrently before domain contracts stabilize would split validation effort and encourage shallow parity.

## Decision

Implement and validate Android milestones first under namespace `com.ing.offlineidv`. Review shared contract semantics for native iOS adoption after the MRZ and state-machine milestones. Do not share UI or platform integration code merely to claim parity.

## Consequences

Android provides the first executable reference and test corpus. iOS delivery lags Android and requires a later native implementation review; `docs/ios-mapping.md` prevents Android framework choices from becoming cross-platform contracts.
