# ADR 0012: Project reducer state into an interactive Compose demo

Status: Accepted

Date: 2026-08-30

## Context

Milestone 4 provides a synchronous headless runtime whose prompt effects automatically dispatch NFC and selfie requests. Milestone 5 needs visible instructions and explicit user actions without copying verification flow or policy into Compose. Android observation types must not enter the framework-free verification module, and composables must not gain access to fake engines.

## Decision

Use four boundaries:

1. `VerificationUiStateMapper` exhaustively converts immutable reducer state to a redacted `AtlasUiState`.
2. `AtlasVerifyApp` renders that state and emits only `AtlasUiAction` values.
3. `AtlasDemoController` maps actions to existing `VerificationEvent` values and observes the serialized orchestrator.
4. `AtlasDemoCompositionRoot` is the only place that creates an explicitly configured demo runtime.

Add a default-preserving `DemoPromptMode`. The headless runner keeps `AUTOMATIC`; Atlas Verify selects `HOST_CONTROLLED`, leaving the reducer in its existing waiting state until the host dispatches `NfcRequested` or `SelfieRequested`.

Use a small Android `ViewModel` with Compose state only as the lifecycle/observation adapter. Do not add Hilt, a navigation framework, coroutines, or a second state machine.

## Consequences

- UI tests can render every safe domain state without feature engines or identity fixtures.
- NFC/selfie instructions are interactive while reducer transitions and outcomes remain unchanged.
- Scenario selection is immutable after runtime creation, and restart can require terminal cleanup.
- The UI model structurally excludes artifact references, session identifiers, raw fields, images, scores, and error codes.
- Real camera/OCR adapters can replace demo effects behind contracts without rewriting screen flow.
- Process death currently restarts the synthetic demo rather than persisting a session; production restoration and persistence remain outside Milestone 5.
