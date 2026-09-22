# Architecture overview

## Status and boundary

This document defines the architecture through Milestone 7 plus the narrowly scoped TD1, M7.3 protected-access, and M7.4 chip-authenticity follow-ups. The repository/module setup, `core` contracts, pure-Kotlin TD3/TD1 MRZ engine, deterministic reducer, explicitly injected synthetic runtime, launchable Compose app, real Android CameraX/bundled-ML-Kit document path, Android NFC/`IsoDep` transport, contained PACE/BAC plus bounded-DG1 adapter, and bounded Netherlands residence-permit PA/CA path are implemented in source. DG2, real face, production persistence, broad trust coverage, and production trust governance remain unavailable. Milestone 8 has not started.

## System context

Atlas Verify runs a passport identity-verification session entirely on one mobile device. A user, passport identity page, ePassport chip, and device camera/NFC hardware are outside the SDK boundary. Platform camera, OCR, NFC, secure storage, and future face-model libraries sit behind explicit interfaces. No backend, account, or remote analytics system participates in the trust decision.

The product decision must never collapse distinct claims. MRZ checksum validity, chip access, printed/chip consistency, passive authentication, chip authentication, and face similarity are separate evidence signals. A policy interprets completed evidence into `VERIFIED`, `REJECTED`, `INCONCLUSIVE`, `TECHNICAL_FAILURE`, `CANCELLED`, or `EXPIRED` only after the relevant milestones implement that behavior.

## Components and dependency direction

```text
┌─────────────────────────────────────────────────────────────┐
│ app-demo: composition root and Android packaging            │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│ ui + accessibility: Compose presentation and semantics      │
└──────────────────────────────┬──────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│ verification: state machine, orchestration, policy, outcome │
└───────────┬───────────┬───────────┬───────────┬─────────────┘
            ↓           ↓           ↓           ↓
      camera/ocr       mrz         nfc         face
            └───────────┬───────────┴───────────┘
                        ↓
             storage + local analytics
                        ↓
┌─────────────────────────────────────────────────────────────┐
│ core: safe shared contracts; no Android framework types     │
└─────────────────────────────────────────────────────────────┘
```

Dependencies point inward toward contracts. The UI observes state and emits user intents; it never constructs CameraX, ML Kit, `IsoDep`, storage, or biometric implementations. `android:app-demo` explicitly composes either the default Demo Mode runtime or the Real Android camera/OCR runtime before a session. It uses constructor injection without Hilt and never falls back from real to synthetic engines.

Feature contracts should expose domain-owned input/output types. Platform adapters translate at their boundary and must not leak `ImageProxy`, `IsoDep`, ML Kit objects, file paths, APDUs, raw exceptions, or framework lifecycle objects into `core`, `mrz`, or verification policy.

## Core contracts implemented in Milestone 1

- `IdvConfig`: validated production or explicitly selected synthetic-demo configuration.
- `IdvSessionId`, `IdvSession`, `IdvClock`: redacted session identity and deterministic time/lifecycle metadata.
- `IdvResult<T>`: typed success/failure; success rendering is redacted.
- `IdvError`: closed subsystem hierarchy with stable codes, safe descriptions, and recovery hints.
- `VerificationRequirement` and `VerificationSignal`: non-sensitive policy/evidence vocabulary.
- `SensitiveValue<T>` and `Redaction`: scoped access, explicit erasure strategy, and safe display helpers.

These contracts do not constitute an orchestrator, state machine, parser, secure store, or verification result engine.

## Deterministic state machine and Demo Mode

Milestone 3 adds closed states/events/effects, safe evidence and outcomes, policy/retry/timeout contracts, MRZ evidence mapping, deterministic operation tokens, and a pure reducer. The sequence covers initialization, document selection/capture/quality, OCR/MRZ validation, NFC, selfie/face comparison, decision, and the terminal verified/rejected/inconclusive/technical-failure/cancelled/expired outcomes. Illegal, duplicate, and stale transitions are explicit no-ops; Android UI components do not participate in transition logic.

Milestone 4 implements feature-owned camera/OCR/NFC/face contracts and policy-free synthetic engines. Verification owns a session-scoped opaque artifact registry, real-MRZ adapter, deterministic scheduler, serialized orchestrator, effect handler, and safe runner. Production configuration cannot construct this runtime. See `docs/architecture/verification-state-machine.md` for transitions and `docs/architecture/demo-engines.md` for the execution boundary.

Milestone 5 adds `VerificationUiStateMapper`, safe `AtlasUiState` models, Material 3 screens, accessibility semantics, an Android ViewModel observation adapter, and a single demo composition root. Interactive prompt mode leaves the reducer in `AwaitingNfc` and `AwaitingSelfie` until the host dispatches the existing user event; it adds no progression or outcome rule. See `docs/architecture/demo-ui.md`.

Milestone 6 adds asynchronous sibling feature contracts, CameraX preview/still capture, session-owned in-memory images, basic deterministic quality analysis, bundled on-device ML Kit OCR, a pure TD3 candidate extractor, a real MRZ pipeline using the existing parser/mapper, and exact-token effect translation. See `docs/architecture/android-camera-ocr.md`.

Milestone 7 adds finite Android NFC capability detection, Activity-rebindable reader mode, one-operation tag-session coordination, `Tag`/`IsoDep` containment, safe connection/error translation, clearable NFC artifacts, policy-free printed/chip comparison, and real-effect routing. M7.3 contains the approved exact JMRTD graph behind a PACE-first/BAC adapter and reads only bounded DG1 fields. M7.4 adds bounded SOD/DG14 handling, fingerprint-pinned Netherlands residence-permit signer trust, PA, and PA-bound CA while keeping signed-data authenticity and fresh chip-key possession separate. The reducer, policy evaluator, and presentation mapper remain library/platform unaware. Distribution remains blocked by revocation/trust governance, legal/open-source, strict dependency-verification/lockfile, release packaging, independent security/PKI review, and representative-device gates. See `docs/architecture/android-epassport-nfc.md`, ADRs 0016–0017, and `docs/security/jmrtd-approval-package.md`.

## Sensitive-data lifecycle

```text
capture into private memory
  -> local feature processing
  -> minimal non-sensitive evidence
  -> offline decision
  -> terminal cleanup confirmation
```

Images, MRZ strings, NFC payloads, portraits, selfies, and templates are sensitive from acquisition. Implementations should use owned mutable buffers where possible and minimize copies. `SensitiveValue` reduces accidental logging and supports explicit buffer erasure, but the runtime/JVM may still make copies that cannot be guaranteed erased. No secret-bearing object may be placed in an error, analytics event, navigation argument, saved UI state, or log.

Persistence is prohibited unless unavoidable. Milestone 4 handles `ClearSensitiveSessionData` for synthetic artifacts, while Milestones 6 and 7 cancel real callbacks, zero owned capture/NFC buffers where possible, drop session artifacts, close camera/OCR/NFC resources, and cancel timers. Milestone 9 must still provide app-private, Keystore-backed encryption, expiry, and verified production cleanup mechanics if persistence becomes necessary. There is no persistence implementation.

## Offline boundary

The application has no `INTERNET` permission. Runtime identity processing, trust decisions, and local events must remain functional in airplane mode. The ML Kit Latin OCR model is bundled in the APK and available without a first-use download. Build-time dependency resolution is the only network-dependent activity. Any future model or certificate material needed at runtime must ship with the application, have version/provenance documentation, and never download on demand.

Offline passive authentication depends on an appropriately governed, current local certificate master list. Successful chip access alone is not passive authentication; unsupported or stale trust material must produce explicit warnings rather than authenticity claims.

## Android and iOS alignment

Shared semantics include configuration, session lifecycle, state/event names, error taxonomy, requirements, evidence, outcomes, data-lifecycle rules, and test scenarios. Implementations remain native:

| Role | Android | iOS |
| --- | --- | --- |
| Camera | CameraX | AVFoundation |
| OCR | ML Kit adapter | Vision |
| NFC | `IsoDep` | CoreNFC |
| Secure storage | Android Keystore + private storage | Keychain/Secure Enclave where appropriate |
| UI state | Compose + `ViewModel`/`StateFlow` | SwiftUI + Swift concurrency observable state |
| Accessibility | TalkBack semantics | VoiceOver/UIAccessibility |

See `docs/ios-mapping.md` for package boundaries and native protocol guidance.
