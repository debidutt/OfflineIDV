# ADR 0003: Use an explicit deterministic state machine

- Status: Accepted
- Date: 2026-08-02

## Context

Identity verification includes asynchronous capture, OCR, NFC, selfie, policy, retry, cancellation, timeout, cleanup, and terminal behavior. Ad hoc ViewModel flags make illegal ordering and incomplete evidence difficult to reason about.

## Decision

Milestone 3 will define closed states, events, effects, and a pure transition reducer independent of Android UI. Illegal transitions will have documented deterministic behavior and tests. Side effects execute outside the reducer and feed results back as events.

## Consequences

Flow behavior is reproducible and portable to iOS, with strong retry/timeout coverage. The model adds explicit types and transition-table maintenance; Milestone 1 intentionally defines no transition implementation.
