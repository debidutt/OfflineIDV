# ADR 0010: Tokenize asynchronous verification operations

- Status: Accepted
- Date: 2026-08-29

## Context

Capture, OCR, MRZ, NFC, selfie, face, and policy work complete asynchronously. Retries and session
reset can cause late callbacks to arrive after the state machine has advanced. Comparing event type
alone cannot distinguish the current result from a stale or duplicate callback.

## Decision

Keep the reducer pure and assign each requested operation a deterministic token containing the
redacted session identifier, verification step, and monotonically increasing generation. Effects
carry the token and completion/step-timeout events must return it. The reducer applies only the
token matching the active operation and returns a safe ignored disposition for every other token.

Session expiry remains a separate matching-session event. Terminal transitions discard opaque
artifact references and always request active/session timeout cancellation, sensitive-session
cleanup, and terminal-result emission.

## Consequences

Late callbacks and stale timeouts cannot advance a newer attempt or session. Identical inputs remain
deterministic without system time, random generation, coroutines, or Android lifecycle state.
Adapters must echo operation tokens accurately, and the transition table and conformance tests must
cover token mismatch behavior on both platforms.
