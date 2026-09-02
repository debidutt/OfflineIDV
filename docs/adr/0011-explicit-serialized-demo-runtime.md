# ADR 0011: Use an explicit serialized demo runtime

Status: Accepted

Date: 2026-08-30

## Context

Milestone 3 deliberately stopped at pure state-machine and orchestration contracts. Milestone 4 needs repeatable end-to-end execution without coupling fake engines to verification policy, leaking synthetic identity data into state, or introducing Android lifecycle and concurrency behavior.

Feature modules cannot depend on `verification` without reversing the accepted module graph. Synchronous fake completions can also become reentrant if an effect handler dispatches directly while the orchestrator is still applying the preceding transition.

## Decision

Use an explicitly configured Demo Mode with four boundaries:

1. Feature modules own policy-free engine contracts, synthetic observations, and deterministic fake implementations.
2. Verification owns a session-scoped in-memory artifact registry and translates feature observations into existing verification events.
3. A FIFO orchestrator updates reducer state before processing effects and queues any completion events produced during effect handling.
4. A deterministic scheduler fires token callbacks only when explicitly requested and is cleared with all other demo-session resources.

Scenario definitions contain external behavior and host lifecycle stimuli only. They cannot contain expected outcomes, retry instructions, next steps, or terminal states. Production configuration is rejected by the demo runtime factory.

## Consequences

- Fake engines can later be replaced without changing reducer or policy semantics.
- Raw synthetic OCR/chip/face material stays outside state and is removed on cleanup.
- Synchronous tests exercise the same token and event boundary required by asynchronous platform adapters.
- Demo orchestration remains deterministic and needs no threads, wall clock, network, persistence, Hilt, or Android APIs.
- The artifact registry is intentionally Demo Mode memory ownership, not a production storage design.
- The runner may simulate host actions, but it cannot infer or assign a verification outcome.
