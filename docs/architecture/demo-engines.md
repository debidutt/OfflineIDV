# Deterministic demo engines

## Purpose and boundary

Milestone 4 provides an explicitly selected, offline Demo Mode that drives the Milestone 3 reducer with deterministic external observations. It validates orchestration and failure handling without claiming that a real passport, chip, portrait, selfie, or identity was processed.

Demo Mode is not a production fallback. `IdvConfig.production()` remains the default, and the demo runtime factory rejects any configuration whose mode is not `DEMO`. A scenario must also be selected explicitly.

The reducer remains the sole authority for transitions, retry availability, required capabilities, evidence interpretation, and terminal outcomes. Fake engines return only the observation that a future platform engine would return.

```text
host event
  -> serialized orchestrator
  -> pure reducer
  -> intent effect
  -> demo effect handler
  -> feature engine observation
  -> verification event with the exact operation token
  -> serialized orchestrator
```

## Engine boundaries

- `camera` owns document capture and quality contracts plus deterministic synthetic implementations.
- `ocr` owns OCR input/output contracts and synthetic TD3 OCR fixtures. OCR does not validate MRZ data.
- `nfc` owns access-key, chip-read, chip-validation, and printed/chip-comparison observations.
- `face` owns selfie capture, selfie quality, portrait input, and face-comparison observations.
- `verification` owns scenario composition, artifact registration, MRZ parsing/mapping, effect-to-event translation, scheduling, cleanup, orchestration, and the safe runner.

Feature engines do not import verification states, events, effects, outcomes, policies, retry models, or reducer types. Replacing a fake feature engine with a real platform adapter therefore does not change verification semantics.

## Scenario catalog

`DemoScenarioCatalog` maps an explicitly selected `DemoScenario` to one immutable `DemoScenarioDefinition`. The definition contains only external behavior:

- capture and quality observations;
- OCR fixture or OCR technical failure;
- NFC read sequence and chip observations;
- printed/chip comparison observation;
- selfie and face observations;
- supported capabilities;
- an optional host cancellation or deterministic expiry trigger.

It never contains an expected outcome, retry instruction, next state, or rejection decision. The same definition can be evaluated with different `VerificationPolicy` values.

## Session-scoped artifact registry

`DemoArtifactRegistry` is an in-memory, single-session owner for synthetic content. It generates opaque `VerificationArtifactReference` values and resolves only the exact reference instances it issued. Reference text never contains artifact content, and `toString()` remains redacted.

The registry stores compact synthetic objects, including OCR text and simulated access/chip/portrait/selfie material. State and events contain only opaque references or safe result summaries. Unknown, cleared, and cross-session references return one predefined safe verification error.

The registry is not persistence. It performs no file, database, Keystore, encryption, or network work.

## Real MRZ integration

The fake OCR engine returns a redacted in-memory OCR artifact containing a conspicuously synthetic TD3 fixture. The verification MRZ adapter resolves that artifact, invokes the real Milestone 2 `Td3MrzParser` with an injected reference date, and maps the resulting `MrzValidationResult` through `MrzEvidenceMapper`.

The fake OCR engine never emits MRZ evidence or an outcome. Invalid checksum, character ambiguity, century ambiguity, and expiry are produced by the real parser and interpreted by the existing policy evaluator.

## Serialized dispatch and effect handling

`SerializedVerificationOrchestrator` owns one immutable `VerificationContext`, a FIFO event queue, and the current state. Reducer state is updated before effects are handled. Completion events dispatched synchronously from an effect handler are appended to the queue and processed only after the current event and its effects finish, avoiding recursive state mutation.

The demo effect handler:

- routes each effect to the appropriate engine;
- echoes the exact operation token and session identifier;
- converts feature failures or caught exceptions to predefined `IdvError` values;
- registers artifacts before dispatching their references;
- never mutates state or chooses a next step;
- delegates timeout effects to the deterministic scheduler;
- clears session resources only when requested by `ClearSensitiveSessionData`.

Late, duplicate, and stale callbacks still pass through the reducer and retain the Milestone 3 ignored-event semantics. A late success cannot advance a terminal cancelled state.

## Deterministic scheduler

`DeterministicVerificationScheduler` stores timeout callbacks by operation token without reading a clock, starting a thread, or sleeping. Tests and the demo runner explicitly fire a token. Cancelled tokens cannot fire. Session tokens dispatch `SessionExpired`; step tokens dispatch `SessionTimedOut`.

## Cleanup model

Handling `ClearSensitiveSessionData` performs an idempotent cleanup:

1. clear the session artifact registry;
2. cancel every scheduled token for the session;
3. reset per-session call counters in fake engines;
4. retain the reducer's safe terminal evidence and outcome.

Cleanup is verified for verified, rejected, inconclusive, technical-failure, cancelled, and expired terminal paths. This is Demo Mode memory cleanup only; secure persistence and production lifecycle hardening remain Milestone 9 work.

## Policy isolation

Fake engines are deliberately not smart. They cannot:

- inspect `VerificationPolicy` or retry counters;
- return `VerificationOutcome`, `VerificationState`, `VerificationEffect`, or `RetryDecision`;
- dispatch `VerificationEvent`;
- call the reducer or orchestrator;
- decide whether an operation is retried or a flow continues;
- decide the next state or terminal result.

For example, the same passive-authentication `FAILED` observation verifies when passive authentication is optional and is rejected when it is required. The same expired MRZ fixture is rejected or allowed to continue depending on policy, while its OCR output and real-parser MRZ evidence remain identical.

## Safe demo runner

`DemoVerificationRunner` supplies only host/user stimuli required by awaiting states, such as passport selection, capture, NFC presentation, selfie request, and an offered retry. It obtains the final outcome exclusively from the reducer. Its report contains state names, effect names, evidence codes, outcome, and cleanup status—never artifact contents.

## Future Android and iOS replacement

Future Android CameraX, ML Kit, `IsoDep`, and reviewed face adapters—and equivalent native iOS adapters—implement the same feature contracts. Asynchronous adapters must preserve operation tokens, enqueue completions through the orchestrator, honor cancellation, and keep platform objects and raw data behind their artifact boundary. They do not change reducer or policy semantics.
