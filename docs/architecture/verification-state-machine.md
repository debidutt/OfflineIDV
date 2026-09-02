# Verification state machine

## Scope and boundary

The `android:verification` module owns a deterministic, Android-framework-free state machine. It
accepts explicit events, returns one immutable next state plus intent-only effects, retains safe
verification evidence, and evaluates configurable policy. It never performs capture, OCR, MRZ
parsing, NFC transport, face comparison, storage, navigation, logging, or time lookup.

External adapters execute effects and return completion events. The reducer sees opaque artifact
references and safe summaries only. Raw MRZ text, passport data, images, chip payloads, face
templates, and comparison scores are prohibited from state-machine contracts.

```text
host -> event -> pure reducer -> state + effects -> external adapter -> completion event
```

Milestone 3 defines orchestration contracts but no engine, effect-handler implementation, Android
lifecycle binding, dependency-injection wiring, or user interface.

## State model

Active states carry a redacted session identifier and `VerificationProgress`: accumulated evidence,
retry counts, opaque artifact references, the current operation token, and deterministic token
generation. Terminal states carry only a `VerificationTerminalSummary`; artifact references are not
copied into terminal state.

| Group | States |
| --- | --- |
| Inactive | `Idle` |
| Processing | `Initializing`, `PreparingCamera`, `CapturingDocument`, `EvaluatingDocumentQuality`, `RunningOcr`, `ExtractingMrz`, `ValidatingMrz`, `ReadingNfc`, `ValidatingChipData`, `ComparingPrintedAndChipData`, `CapturingSelfie`, `EvaluatingSelfie`, `ComparingFaces`, `MakingDecision` |
| Awaiting user | `DocumentSelection`, `CameraPermissionRequired`, `CameraReady`, `AwaitingNfc`, `AwaitingSelfie`, `RecoveryRequired` |
| Terminal | `Verified`, `Rejected`, `Inconclusive`, `TechnicalFailure`, `Cancelled`, `Expired` |

Every active state is cancellable. Terminal states are immutable except that `Reset` returns to
`Idle`; reset never suppresses cleanup because cleanup was already requested on terminal entry.

## Event model

Events are a closed hierarchy:

- Lifecycle: `Start`, `InitializationSucceeded`, `InitializationFailed`, `Reset`, `Cancel`,
  `SessionTimedOut`, `SessionExpired`.
- Document: `PassportSelected`, `CameraPermissionRequired`, `CameraPermissionGranted`,
  `CameraPermissionDenied`, `CameraReady`, `CaptureRequested`, `DocumentCaptured`,
  `CaptureQualityAccepted`, `CaptureQualityRejected`, `CaptureFailed`.
- OCR/MRZ: `OcrStarted`, `OcrSucceeded`, `OcrFailed`, `MrzExtractionSucceeded`,
  `MrzExtractionFailed`, `MrzValidationCompleted`, `MrzValidationFailed`.
- NFC: `NfcRequested`, `NfcStarted`, `NfcReadSucceeded`, `NfcReadFailed`,
  `ChipValidationCompleted`, `ChipValidationFailed`, `PrintedAndChipComparisonCompleted`.
- Selfie/face: `SelfieRequested`, `SelfieCaptured`, `SelfieCaptureFailed`,
  `SelfieQualityAccepted`, `SelfieQualityRejected`, `FaceComparisonCompleted`,
  `FaceComparisonFailed`.
- Decision/recovery: `DecisionRequested`, `DecisionCompleted`, `DecisionFailed`, `Retry`, `Back`,
  `AcknowledgeError`.

Completion events carry an operation token. Artifact-producing events carry only an opaque typed
reference. MRZ completion carries `MrzVerificationSummary`; NFC, passive-authentication, printed/chip,
and face completions carry finite status enums. Failure events carry the existing safe `IdvError`.

## Effect model

Effects describe external intent only:

- `InitializeSession`, `RequestCameraPermission`, `PrepareCamera`, `CaptureDocument`,
  `EvaluateDocumentQuality`, `RunOcr`, `ExtractAndValidateMrz`.
- `PromptForNfc`, `StartNfcRead`, `ValidateChipData`, `ComparePrintedAndChipData`.
- `PromptForSelfie`, `CaptureSelfie`, `EvaluateSelfieQuality`, `CompareFaces`.
- `EvaluateVerificationPolicy`, `ScheduleTimeout`, `CancelTimeout`.
- `ClearSensitiveSessionData`, `EmitTerminalResult`.

Operation effects carry their deterministic token and only the opaque references required by an
adapter. Effects never name a platform API or implementation.

## Happy-path transition table

Permission preparation is shown explicitly even though the concise state sequence may omit it.

| Current state | Event | Next state | Principal effects |
| --- | --- | --- | --- |
| `Idle` | `Start` | `Initializing` | initialize, schedule initialization timeout |
| `Initializing` | `InitializationSucceeded` | `DocumentSelection` | cancel initialization timeout, schedule session expiry |
| `DocumentSelection` | `PassportSelected` | `CameraPermissionRequired` | request camera permission |
| `CameraPermissionRequired` | `CameraPermissionGranted` | `PreparingCamera` | prepare camera, schedule preparation timeout |
| `PreparingCamera` | `CameraReady` | `CameraReady` | cancel preparation timeout |
| `CameraReady` | `CaptureRequested` | `CapturingDocument` | capture document, schedule capture timeout |
| `CapturingDocument` | `DocumentCaptured` | `EvaluatingDocumentQuality` | cancel capture timeout, evaluate quality, schedule quality timeout |
| `EvaluatingDocumentQuality` | `CaptureQualityAccepted` | `RunningOcr` | cancel quality timeout, run OCR, schedule OCR timeout |
| `RunningOcr` | `OcrSucceeded` | `ExtractingMrz` | cancel OCR timeout, extract/validate MRZ, schedule MRZ timeout |
| `ExtractingMrz` | `MrzExtractionSucceeded` | `ValidatingMrz` | retain same MRZ operation token |
| `ValidatingMrz` | `MrzValidationCompleted` | `AwaitingNfc` | cancel MRZ timeout, map evidence, prompt for NFC |
| `AwaitingNfc` | `NfcRequested` | `ReadingNfc` | start NFC read, schedule NFC timeout |
| `ReadingNfc` | `NfcReadSucceeded` | `ValidatingChipData` | cancel read timeout, validate chip data, schedule validation timeout |
| `ValidatingChipData` | `ChipValidationCompleted` | `ComparingPrintedAndChipData` | cancel validation timeout, compare printed/chip data, schedule comparison timeout |
| `ComparingPrintedAndChipData` | comparison match | `AwaitingSelfie` | cancel comparison timeout, prompt for selfie |
| `AwaitingSelfie` | `SelfieRequested` | `CapturingSelfie` | capture selfie, schedule capture timeout |
| `CapturingSelfie` | `SelfieCaptured` | `EvaluatingSelfie` | cancel capture timeout, evaluate quality, schedule quality timeout |
| `EvaluatingSelfie` | `SelfieQualityAccepted` | `ComparingFaces` | cancel quality timeout, compare faces, schedule comparison timeout |
| `ComparingFaces` | accepted comparison | `MakingDecision` | cancel comparison timeout, evaluate verification policy, schedule decision timeout |
| `MakingDecision` | `DecisionCompleted` | `Verified` | cancel timeouts, clear sensitive session data, emit terminal result |

If NFC or face comparison is optional and unavailable, routing skips that stage, records
`CAPABILITY_UNAVAILABLE`, and continues. A required unavailable capability routes to policy
evaluation and normally produces `INCONCLUSIVE`.

## Evidence and MRZ mapping

Evidence is a finite set and remains distinct from `VerificationOutcome`.

| MRZ validation input | Verification evidence |
| --- | --- |
| `structurallyValid == true` | `MRZ_STRUCTURE_VALID` |
| `structurallyValid == false` | `MRZ_STRUCTURE_INVALID` |
| all applicable check digits valid | `MRZ_CHECK_DIGITS_VALID` |
| any mismatch or malformed check digit | `MRZ_CHECK_DIGITS_INVALID` |
| `AMBIGUOUS_CHARACTER` or recorded correction | `MRZ_CHARACTER_AMBIGUITY` |
| `AMBIGUOUS_CENTURY` | `MRZ_CENTURY_AMBIGUITY` |
| expiry `EXPIRED` | `DOCUMENT_EXPIRED` |
| expiry `UNKNOWN` | `DOCUMENT_EXPIRY_UNKNOWN` |

`MrzEvidenceMapper` consumes `MrzValidationResult` only. It never accepts `Td3PassportMrz` or raw
MRZ text. Checksum consistency is named consistency evidence, never authenticity.

NFC evidence distinguishes chip-read success/failure, DG1/DG2 availability, printed/chip
consistency, and passive authentication as valid, failed, or not performed. Face evidence
distinguishes selfie quality, accepted/rejected matching, and inconclusive comparison. Operational
evidence records retries, skipped required work, and unavailable capabilities.

## Policy semantics

`VerificationPolicy` explicitly configures required MRZ structure/check digits, expiration
rejection, NFC read, printed/chip consistency, face match, passive authentication, and retry.
Printed/chip consistency or passive authentication cannot be required without NFC.

- `VERIFIED`: every required signal has explicit satisfying evidence.
- `REJECTED`: a completed required signal explicitly fails, including required MRZ/checksum,
  printed/chip, face, passive-authentication, or expiration policy.
- `INCONCLUSIVE`: required evidence is missing, ambiguous, unavailable, skipped, or unknown without
  an explicit rejecting signal.
- `TECHNICAL_FAILURE`: a required operation cannot complete after its permitted retries, or
  initialization/decision execution fails unrecoverably.
- `CANCELLED`: active flow receives host/user cancellation.
- `EXPIRED`: the matching active session expires.

Optional failed evidence is retained but does not by itself reject. The reducer, not a completion
event payload, computes the final policy outcome when `DecisionCompleted` acknowledges evaluation.

## Retry and recovery

`RetryPolicy` defines positive maximum attempt counts for document capture, OCR, NFC, and selfie/
face work. `RetryCounter` records attempts by retryable step. An initial request consumes attempt
one; each accepted retry increments the corresponding count and adds `STEP_RETRIED` evidence.

Only `RecoveryRequired` accepts `Retry`. It identifies the failed step, safe reason, and retry
decision. Retry never clears evidence or unrelated counters. Exhausted quality/inconclusive work
produces `INCONCLUSIVE`; exhausted required technical work produces `TECHNICAL_FAILURE`; exhausted
optional NFC/face work records evidence and continues. Terminal states ignore retry.

`AcknowledgeError` declines recovery and produces the defined non-retry outcome. `Back` is supported
only at documented user boundaries: camera permission/readiness, optional NFC, and optional selfie.

## Timeout and expiry

The reducer never reads a clock. Each external operation receives a monotonically increasing
`VerificationOperationToken` containing the redacted session identifier, step, and generation.
`ScheduleTimeout` carries that token and a configured duration. Completion cancels that timeout.

`SessionTimedOut` is a step-timeout event. It applies only when its token equals the state's current
operation. Older generations are duplicate/stale and newer or foreign-session tokens are stale.
Retryable timeouts enter recovery; unrecoverable timeouts terminate as technical failure.

Session expiry uses a separate session token scheduled after successful initialization.
`SessionExpired` applies only to the matching active session and always produces `EXPIRED`. Host
`Cancel` always produces `CANCELLED`; neither is conflated with a step timeout.

## Illegal, duplicate, and stale events

`TransitionResult.disposition` is one of `APPLIED`, `IGNORED_ILLEGAL_EVENT`,
`IGNORED_DUPLICATE_EVENT`, or `IGNORED_STALE_EVENT`.

- An event with no transition from the current state is illegal.
- Replayed lifecycle/start or already-consumed completion events are duplicate.
- A completion/timeout for another session or an unknown/future operation generation is stale.
- Ignored events return the identical state, emit no effects, and never throw.
- Terminal states accept only `Reset`; all other events leave the terminal state unchanged.

## Cancellation and terminal cleanup

Every transition into `Verified`, `Rejected`, `Inconclusive`, `TechnicalFailure`, `Cancelled`, or
`Expired` emits, in order:

1. `CancelTimeout` for the active operation when present.
2. `CancelTimeout` for scheduled session expiry when present.
3. `ClearSensitiveSessionData`.
4. `EmitTerminalResult`.

Cleanup is an intent only in Milestone 3. Storage and cleanup mechanics remain deferred. Artifact
references are removed from the terminal state regardless of outcome.

## Security and non-goals

States, events, effects, transition results, summaries, and opaque references have safe string
representations. No contract contains raw MRZ, names, document numbers, dates of birth, nationality,
image bytes, APDUs, chip data, templates, scores, file paths, or platform exceptions.

This milestone does not implement effect handlers, fake engines, Compose/ViewModel/navigation,
CameraX, ML Kit, `IsoDep`, CoreNFC, face engines, Hilt, persistence, analytics, networking, or
Android lifecycle behavior.
