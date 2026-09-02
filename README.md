# Project Atlas

Project Atlas is an Android-first, offline identity-verification prototype. The Android demo is named **Atlas Verify** and the planned SDK is the **Atlas Offline Identity Verification SDK** (Atlas IDV SDK). Android and iOS use the same architectural vocabulary and security semantics while retaining native platform implementations.

## Current status

Milestones 1 through 7 establish the repository foundation, pure-Kotlin TD3 MRZ engine, deterministic verification state machine, explicitly injected synthetic Demo Mode, launchable Jetpack Compose application, real Android CameraX/bundled-ML-Kit document processing, and a lifecycle-safe Android NFC/`IsoDep` transport boundary. Demo Mode remains hardware-independent. Real Android Mode never falls back to a fake engine.

The Milestone 7 protocol-security checkpoint did not approve an ePassport library. Real Android Mode can detect NFC hardware, discover tags, recognize/connect `IsoDep`, and close safely, but it deliberately returns `PROTOCOL_UNSUPPORTED` before sending an APDU. It cannot yet read DG1/DG2 or perform BAC/PACE.

Do not use this foundation to make identity, authenticity, biometric, or production-security claims.

## Scope and non-goals

Planned scope is a deterministic offline passport flow: local capture, OCR, TD3 MRZ validation, ePassport NFC reading, face-comparison abstraction, evidence aggregation, and explicit sensitive-data cleanup. The conference demonstration must work in airplane mode and also offer injected synthetic scenarios.

Non-goals include cloud APIs, accounts, server verification, remote analytics, custom cryptography, production liveness claims, broad document-format coverage, and persistence beyond an active session.

## Architecture

```text
app-demo -> ui -> verification -> feature modules -> core
```

- `android/core`: shared configuration, session, result/error, signal, time, and sensitive-data contracts.
- `android/camera`: synchronous demo contracts/fakes plus asynchronous CameraX capture, memory ownership, and basic quality analysis.
- `android/ocr`: synchronous demo contracts/fakes plus bundled on-device ML Kit OCR and policy-free TD3 candidate extraction.
- `android/mrz`: pure-Kotlin TD3 normalization, parsing, date interpretation, and validation engine.
- `android/nfc`: synchronous demo contracts/fakes, asynchronous real-session coordination, Android NFC capability/reader-mode/`IsoDep` containment, protected chip artifacts, and policy-free printed/chip comparison.
- `android/face`: selfie, quality, and face-comparison contracts with synthetic fakes.
- `android/verification`: reducer, policy, session artifact stores, real/demo MRZ pipelines, serialized orchestrator, deterministic scheduler, Demo Mode effect handler, and safe runner.
- `android/storage`: temporary secure-storage/cleanup boundary (implementation deferred).
- `android/analytics`: non-sensitive local-only event boundary (implementation deferred).
- `android/accessibility`: shared TalkBack descriptions and progress announcements.
- `android/ui`: Material 3 screens, safe presentation state, exhaustive reducer mapper, semantics, and UI tests.
- `android/app-demo`: explicit Demo/Real composition, real effect handler, permission bridge, controller, and ViewModel observation adapter.

See [architecture overview](docs/architecture/overview.md), [Android camera/OCR design](docs/architecture/android-camera-ocr.md), [Android ePassport NFC design](docs/architecture/android-epassport-nfc.md), [ePassport library review](docs/security/epassport-library-review.md), [MRZ engine design](docs/architecture/mrz-engine.md), [verification state machine](docs/architecture/verification-state-machine.md), [demo-engine design](docs/architecture/demo-engines.md), [demo UI design](docs/architecture/demo-ui.md), [implementation plan](docs/implementation-plan.md), [ADRs](docs/adr), and [iOS mapping](docs/ios-mapping.md).

## Toolchain and setup

Requirements:

- JDK 17.
- Android SDK platform 35 and Android Build Tools 36.0.0 or a compatible newer installation.
- Internet access for the first Gradle dependency download.

Open the repository root in a compatible Android Studio release or use the checked-in Gradle wrapper. Do not create `local.properties` in source control; Android Studio or the `ANDROID_HOME`/`ANDROID_SDK_ROOT` environment variable should locate the SDK.

## Build and quality commands

```shell
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew :android:app-demo:assembleDebug
```

Apply formatting with `./gradlew spotlessApply`. Run the checks as separate Gradle invocations so Android generated-resource output tracking cannot overlap Spotless source inputs:

```shell
./gradlew spotlessCheck
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

## Demo Mode

Run `./gradlew :android:app-demo:installDebug` with an attached device/emulator, or open the repository in Android Studio and launch `android:app-demo`. The launcher activity is `com.ing.offlineidv.demo.MainActivity` and the application ID remains `com.ing.offlineidv.demo`.

Callers must create `IdvConfig.demo(DemoScenario)` explicitly; `DemoVerificationFactory` rejects production configuration. The 15 required scenarios cover success, MRZ failure/ambiguity/expiry, NFC retry/exhaustion/unavailability, chip mismatch, passive-auth failure, selfie quality, face mismatch/inconclusive, technical failure, cancellation, and expiry.

Atlas Verify defaults to Success and offers the scenario selector only before a session starts. It renders only safe reducer projections, dispatches existing user events, and requires cleanup before creating a replacement session. The headless `DemoVerificationRunner` remains available for JVM integration tests and is not a production fallback.

## Real Android Mode

Select **Real Android Mode** on Welcome before starting a session. Atlas requests `CAMERA` only when the reducer requests permission, binds a live CameraX preview, captures into active-session memory, applies basic resolution/brightness/blur checks, and runs the bundled ML Kit Latin recognizer locally. A pure candidate extractor forwards likely TD3 text to the existing parser and evidence mapper.

This mode never falls back to synthetic engines. It advertises NFC only when hardware exists. Android reader mode is active only for a pending reducer-owned NFC operation while the host is resumed; `Tag` and `IsoDep` never reach state or Compose. Because the protocol review is blocked, an authorized chip reaches a safe unsupported result rather than fake data. Face capability remains unavailable. See [Android camera/OCR architecture](docs/architecture/android-camera-ocr.md) and [Android ePassport NFC architecture](docs/architecture/android-epassport-nfc.md).

## Security baseline and limitations

- The app requests `CAMERA` and `NFC` for explicit Real Android Mode, requests no network permission, and disallows cleartext traffic and Android backup.
- Success wrappers and session identifiers redact their string representation.
- Errors expose stable codes and predefined safe descriptions, never raw input or platform exception text.
- Byte/character sensitive holders own a copy and overwrite it on explicit cleanup.
- Verification states retain only safe evidence and opaque references; every terminal transition requests timeout cancellation, sensitive-session cleanup, and terminal-result emission.
- Demo artifacts remain in a single-session in-memory registry and are cleared idempotently on every terminal outcome.
- No real identity fixture, portrait, selfie, or biometric implementation is present. The real NFC implementation stops before protocol/APDU processing.

The current code does not implement encrypted temporary persistence, production lifecycle cleanup, screenshot handling, root/debug mitigations, BAC/PACE, DG1/DG2 reading, real passive authentication, chip authentication, or a complete threat model. Demo evidence is synthetic and must not support production claims. These controls are not optional for a production assessment.

## Roadmap

1. Repository foundation — complete.
2. Pure-Kotlin TD3 MRZ engine and tests — complete.
3. Deterministic verification state machine and orchestration contracts — complete.
4. Injected synthetic demo engines and integration tests — complete.
5. Accessible Compose demo application — complete.
6. CameraX and bundled ML Kit document path — complete.
7. Android NFC/`IsoDep` transport integration — implemented; ePassport protocol selection blocked by security review.
8. Face module and reviewed-engine extension point.
9. Secure storage, cleanup, expiry, and threat-model hardening.
10. Airplane-mode conference polish and final documentation.
