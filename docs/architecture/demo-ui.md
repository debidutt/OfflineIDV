# Atlas Verify demo UI architecture

## Scope

Milestone 5 added the launchable Android application; Milestone 6 added a pre-session choice between deterministic Demo Mode and Real Android Mode, and Milestone 7 extends the real host with NFC capability and transport guidance. Demo Mode remains explicitly synthetic and hardware-independent. Real Android Mode uses CameraX, bundled local OCR, and Android NFC/`IsoDep` transport but stops before ePassport APDU/protocol processing; it does not perform face/liveness processing, persist identity data, connect to a network, or make an identity/authenticity claim.

The runtime direction is fixed:

```text
VerificationState
  -> VerificationUiStateMapper
  -> AtlasUiState
  -> Jetpack Compose

user action
  -> AtlasUiAction
  -> AtlasDemoController
  -> existing VerificationEvent
  -> SerializedVerificationOrchestrator
  -> reducer
```

The UI is a projection and event source. It is not an alternative workflow engine.

## Composition root and state observation

`AtlasDemoCompositionRoot` in `android:app-demo` remains the only application composition root. It:

- creates `IdvConfig.demo` with the pre-session scenario selection;
- generates a fresh opaque session identifier;
- creates `DemoVerificationRuntime` through `DemoVerificationFactory`;
- explicitly selects `DemoPromptMode.HOST_CONTROLLED` for interactive NFC and selfie prompts; and
- supplies the runtime to `AtlasDemoController`; and
- when Real Android Mode is explicitly selected, creates the separate `RealAndroidVerificationFactory` binding without demo fallback.

Composables never create a runtime, fake engine, state machine, policy evaluator, storage object, or platform adapter. A composable preview slot is implemented by `MainActivity` with `PreviewView`; no image data or CameraX type enters `AtlasUiState`. The host also supplies only a finite NFC availability enum, while the Android adapter retains Activity, `Tag`, and `IsoDep`. `AtlasDemoViewModel` is an Android-only observation adapter that converts controller callbacks to Compose `mutableStateOf`; no Compose, Android lifecycle, or `ViewModel` type enters `verification`.

The default factory prompt behavior remains automatic for the headless Milestone 4 runner. Host-controlled mode only prevents the demo effect handler from automatically dispatching `NfcRequested` and `SelfieRequested`. The reducer remains in `AwaitingNfc` or `AwaitingSelfie` until the interactive host dispatches the existing user event. This changes no policy, retry, outcome, or next-step rule.

## Presentation states and screens

The mapper exhaustively projects every reducer state:

| Reducer state family | Presentation |
| --- | --- |
| `Idle` | Welcome |
| `DocumentSelection` | Document selection |
| `CameraPermissionRequired`, `PreparingCamera` | Safe preparation status |
| `CameraReady` | Synthetic frame in Demo Mode or live preview in Real Android Mode |
| Capture/quality | Document processing |
| OCR/extraction/validation | MRZ processing |
| `AwaitingNfc` | Safe MRZ summary plus explicit Demo simulation or Real Android placement/capability guidance |
| NFC read/chip validation/comparison | Chip processing |
| `AwaitingSelfie` | Synthetic selfie instructions |
| Selfie/face operations | Face processing |
| `MakingDecision` | Decision processing |
| `RecoveryRequired` | Recovery with reducer-owned retry availability |
| Six terminal states | Outcome-specific result |

Welcome, scenario selection, passport instructions, and residence-permit instructions are presentation-only surfaces. Instructions delay the corresponding selection event until the user presses Continue; they do not alter reducer sequencing. National ID remains disabled. Netherlands residence permits are enabled only in Real Android Mode and are visibly disclosed as three-line TD1 document checks without NFC, face, identity, or authenticity claims.

Progress always uses the five safe stages Document, MRZ, Chip, Face, and Decision. Each stage includes text and a symbol for Complete, Current, Upcoming, or Needs attention, so color is never the only signal.

## Scenario selector

Runtime Mode is available only on Welcome and defaults to `DEMO`. The scenario selector appears only in Demo Mode before runtime creation and defaults to `SUCCESS`. It groups the fifteen Milestone 4 scenarios into Success, Document, NFC, Face, and Lifecycle sections. Rows describe only the synthetic external observation or lifecycle stimulus. They contain no expected outcome or recovery decision.

After a runtime exists, runtime-mode and scenario-selection actions are ignored. Starting again requires a terminal reducer state and confirmed cleanup, then creates a new runtime and opaque session identifier using the locked pre-session choice.

## Accessibility

The UI provides:

- headings for screen and evidence section titles;
- at least 48 dp interactive targets and 52–56 dp primary controls;
- text plus symbols for every progress, evidence, and outcome status;
- merged progress semantics such as “Step 3 of 5, Chip”;
- content descriptions and text guidance for document framing, plus NFC and selfie frames;
- polite live-region announcements for processing, recovery, and terminal states;
- native radio-button selection semantics for runtime/scenario selectors; and
- readable light/dark Material 3 color schemes without relying on color alone.

Instrumentation tests validate the primary semantics and actions. They compile in the repository build; execution still requires an attached emulator or device.

## Redaction and privacy

`AtlasUiState` cannot hold `IdvSessionId`, `VerificationArtifactReference`, images, MRZ lines/fields, NFC payloads, portrait/selfie data, face scores, raw errors, or internal tokens. The mapper reads only the state class, finite evidence enum values, reducer `RetryDecision`, and terminal `VerificationOutcome`.

Evidence copy is predefined and safe. It reports facts such as MRZ structure/check-digit status, ambiguity, expiry, synthetic chip availability/consistency, selfie quality, and synthetic face-comparison status. It never renders a name, document number, birth date, expiry value, nationality, image, score, error code, or reference value.

The application manifest has no `INTERNET` or `ACCESS_NETWORK_STATE` permission, disables backup and cleartext traffic, and declares optional `CAMERA` and `NFC` hardware for explicit Real Android Mode. Synthetic artifacts remain in the Milestone 4 registry; real captures, OCR results, access material, and any future NFC data remain in single-session memory stores. Both paths clear their owners on every terminal outcome or host cancellation.

## Conference flow

The shortest deterministic flow is:

1. Launch Atlas Verify and show the persistent OFFLINE and DEMO badges.
2. Start the default Success scenario.
3. Select Passport and review capture guidance.
4. Capture the synthetic passport and review the safe MRZ summary.
5. Simulate the chip read; no NFC hardware is accessed.
6. Capture the synthetic selfie; no liveness claim is made.
7. Show the reducer-selected result and safe evidence.
8. Start again or return home; cleanup is complete before replacement.

There are no artificial sleeps, connectivity checks, hardware dependencies, or remote fallbacks.

## Policy-isolation proof

Production `android:ui` is audited for forbidden policy, validator, engine, fake, and sensitive-payload references. The controller is separately audited to ensure it does not inspect retry counters or map evidence failures to outcomes. Real feature-engine source is audited for policy, outcome, reducer/state, event dispatch, retry, next-step, log, and public-storage references. The app composition root may construct a `VerificationContext`, but only the existing reducer/policy evaluator interprets it. The UI outcome switch provides copy for a reducer-supplied terminal `VerificationOutcome`, and the only retry condition is `RetryDecision.RETRY_AVAILABLE` supplied by `RecoveryRequired`.

Scenario definitions remain observation-only. `DemoVerificationFactory` owns fake composition; the app composition root sees only the runtime factory. Reducer and policy tests continue to prove that different policies can interpret identical evidence differently.

## Runtime selection boundary

Milestones 6, 7, and the approved M7.3/M7.4 extensions select document capture, OCR, NFC transport, the contained PACE/BAC/DG1 adapter, and bounded Netherlands PA/CA behind existing feature/effect boundaries. They do not change the UI-to-event direction or let Compose instantiate CameraX, ML Kit, `NfcAdapter`, `Tag`, `IsoDep`, Scuba, JMRTD, or cryptographic providers. The real binding preserves exact operation tokens, serialized dispatch, and cooperative lifecycle cancellation. It advertises NFC only when hardware exists and never substitutes its fake. The shared mapper renders implementation-neutral `Signed chip data` and `Live chip proof` observations without knowing runtime mode. DG2, face, persistence, broad trust/revocation coverage, and production security hardening remain unavailable.
