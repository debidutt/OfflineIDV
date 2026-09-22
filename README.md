# Project Atlas

Project Atlas is an Android-first, offline identity-verification prototype. The Android demo is named **Atlas Verify** and the planned SDK is the **Atlas Offline Identity Verification SDK** (Atlas IDV SDK). Android and iOS use the same architectural vocabulary and security semantics while retaining native platform implementations.

## Current status

Milestones 1 through 7 establish the repository foundation, pure-Kotlin MRZ engine, deterministic verification state machine, explicitly injected synthetic Demo Mode, launchable Jetpack Compose application, real Android CameraX/bundled-ML-Kit document processing, and a lifecycle-safe Android NFC/`IsoDep` boundary. The approved M7.3 extension adds standard three-line TD1 Netherlands residence-permit checks, PACE-first/BAC protected chip access, bounded DG1 reading, and printed/DG1 consistency. The approved M7.4 extension adds bounded SOD/DG14 reads, a fingerprint-pinned Netherlands residence-permit trust snapshot, Passive Authentication, and PA-bound Chip Authentication without starting Milestone 8. Demo Mode remains hardware-independent. Real Android Mode never falls back to a fake engine.

JMRTD 0.8.8 and its reviewed exact dependency graph are contained inside `android:nfc`; only BAC/PACE, DG1/SOD/DG14, signature/provider primitives, and Chip Authentication are used. PA and CA are separate evidence, not one aggregate authenticity flag. DG2, face matching, revocation checking, broad trust coverage, and holder-identity/liveness claims remain unavailable. Legal/open-source approval, governed current trust/revocation material, strict resolved dependency verification/locking, release packaging, independent security/PKI review, and representative physical-device validation remain blockers for distribution or production-readiness claims.

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
- `android/ocr`: synchronous demo contracts/fakes plus bundled on-device ML Kit OCR and policy-free TD3/TD1 candidate extraction.
- `android/mrz`: pure-Kotlin TD3/TD1 normalization, parsing, date interpretation, and validation engine.
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

Select **Real Android Mode** on Welcome before starting a session. Atlas requests `CAMERA` only when the reducer requests permission, binds a live CameraX preview, captures into active-session memory, applies basic resolution/brightness/blur checks, and runs the bundled ML Kit Latin recognizer locally. Choose Passport for the TD3 route or Netherlands residence permit for strict three-line TD1 MRZ checks followed by protected chip DG1 consistency, signed-data authentication, and live-chip proof.

This mode never falls back to synthetic engines. Both supported document profiles advertise NFC only when hardware exists. Android reader mode is active only for a pending reducer-owned NFC operation while the host is resumed; `Tag`, `IsoDep`, APDUs, certificates, and JMRTD objects never reach verification state or Compose. The residence-permit profile requires NFC read, printed/DG1 consistency, PA, and CA but not face matching. Completion supports only the explicit evidence shown; it is not holder identity, liveness, revocation status, entitlement, or a production document-validity claim. See [Android camera/OCR architecture](docs/architecture/android-camera-ocr.md) and [Android ePassport NFC architecture](docs/architecture/android-epassport-nfc.md).

## Security baseline and limitations

- The app requests `CAMERA` and `NFC` for explicit Real Android Mode, requests no network permission, and disallows cleartext traffic and Android backup.
- Success wrappers and session identifiers redact their string representation.
- Errors expose stable codes and predefined safe descriptions, never raw input or platform exception text.
- Byte/character sensitive holders own a copy and overwrite it on explicit cleanup.
- Verification states retain only safe evidence and opaque references; every terminal transition requests timeout cancellation, sensitive-session cleanup, and terminal-result emission.
- Demo artifacts remain in a single-session in-memory registry and are cleared idempotently on every terminal outcome.
- No real identity fixture, portrait, selfie, or biometric implementation is present. The real NFC implementation is limited to protected access, bounded DG1/SOD/DG14 handling, and the Netherlands residence-permit trust/CA profile.

The current code does not implement encrypted temporary persistence, production lifecycle cleanup, screenshot handling, root/debug mitigations, DG2 reading, revocation checking, broad CSCA coverage, Active/Terminal Authentication, or face comparison. Demo evidence is synthetic and must not support production claims. M7.3/M7.4 dependency, trust, legal, release, security, and device gates and these controls are not optional for a production assessment.

## Roadmap

1. Repository foundation — complete.
2. Pure-Kotlin TD3 MRZ engine and tests — complete; TD1 residence-permit support added as a later validation extension.
3. Deterministic verification state machine and orchestration contracts — complete.
4. Injected synthetic demo engines and integration tests — complete.
5. Accessible Compose demo application — complete.
6. CameraX and bundled ML Kit document path — complete.
7. Android NFC integration — transport, limited PACE/BAC plus DG1 M7.3 integration, and bounded Netherlands PA/CA M7.4 integration implemented with trust/release/device gates still open.
8. Face module and reviewed-engine extension point.
9. Secure storage, cleanup, expiry, and threat-model hardening.
10. Airplane-mode conference polish and final documentation.
