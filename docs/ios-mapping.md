# iOS architecture mapping

## Principle

Atlas aligns behavior and security semantics across platforms, not source code or framework types. Android is implemented first; iOS should use native Swift protocols, value types, actors, async sequences, and Apple frameworks while preserving the same evidence and outcome distinctions.

## Suggested package structure

```text
AtlasIDV/
├── Core/
├── Camera/
├── OCR/
├── MRZ/
├── NFC/
├── Face/
├── Verification/
├── Storage/
├── Accessibility/
└── UI/
```

`Core` and `MRZ` should be Foundation-only Swift packages where practical. UI and platform integration packages depend inward on protocols and domain values. The host application owns dependency composition; SwiftUI must not construct AVFoundation, Vision, CoreNFC, storage, or face-model adapters directly.

## Contract alignment

Keep these concepts semantically aligned with Android:

- production-versus-explicit-demo configuration and synthetic scenario names;
- opaque session identifier, UTC start/expiry instants, and injected clock;
- typed success/failure and closed, stable error codes with safe descriptions;
- verification requirements, evidence signal types/statuses, warnings, and outcomes;
- deterministic state/event/effect vocabulary, tokenized stale-event handling, and illegal-transition behavior;
- cancellation, retry, timeout, expiry, and cleanup terminal semantics;
- no-sensitive-logging and offline-only runtime boundaries.

Use Swift structs/enums/protocols rather than trying to share Kotlin binaries. Error cases should not use associated values containing MRZ, names, document numbers, APDUs, file URLs, image bytes, raw `Error.localizedDescription`, or biometric scores.

## Native role mapping

| Android | Native iOS role |
| --- | --- |
| CameraX / `ImageProxy` | AVFoundation / `CMSampleBuffer` / `CVPixelBuffer` |
| ML Kit OCR adapter | Vision `VNRecognizeTextRequest` |
| `IsoDep` tag session | CoreNFC `NFCTagReaderSession` and ISO 7816 tags |
| Android Keystore | Keychain; Secure Enclave only where its key capabilities fit |
| Jetpack Compose | SwiftUI |
| `ViewModel` + `StateFlow` | `@MainActor` observable state and Swift concurrency |
| Coroutines | Structured Swift tasks and `AsyncSequence` |
| TalkBack semantics | VoiceOver labels, traits, focus, and announcements |

Camera, OCR, NFC, secure storage, UI, accessibility, permissions, backgrounding, and lifecycle behavior remain platform-native. NFC session invalidation and app backgrounding should map into deterministic orchestration events rather than leaking delegate callbacks into the domain.

## Illustrative native interfaces

These signatures document shape only; no iOS source implementation is part of Milestone 1.

```swift
protocol AtlasClock: Sendable {
    func now() -> Date
}

protocol PassportOCR: Sendable {
    func recognize(_ input: SensitiveImage) async -> Result<OCRResult, AtlasError>
}

protocol PassportNFCReader: Sendable {
    func read(using key: SensitiveAccessKey) async -> Result<PassportChipData, AtlasError>
}

protocol FaceMatcher: Sendable {
    func compare(reference: SensitiveFace, probe: SensitiveFace) async
        -> Result<FaceMatchEvidence, AtlasError>
}
```

Concrete sensitive wrappers should minimize copies and provide explicit cleanup where their backing storage is mutable. Swift `String`, `Data`, `UIImage`, and framework buffers can copy implicitly, so zeroization claims must remain conservative.

## Platform-specific security considerations

- Configure file protection and app-private containers for any unavoidable temporary file.
- Treat Keychain and Secure Enclave as key-protection tools, not automatic payload storage.
- Invalidate CoreNFC sessions on cancellation, timeout, and terminal orchestration states.
- Disable or obscure sensitive UI snapshots where product policy allows, while documenting iOS limitations.
- Keep debug logging, crash metadata, and accessibility values free of identity data.
- Govern offline document-signing certificates separately on each platform and expose trust-store age/status as evidence metadata.

## Effect-handler and Demo Mode parity

iOS should mirror the Milestone 4 separation without sharing Kotlin code: feature fakes return observations only; a Verification-owned actor serializes events, invokes the pure reducer, and handles effects through injected protocols. Reentrant delegate or async completions must be enqueued, echo the exact operation token, and remain harmless after cancellation or expiry.

An iOS synthetic registry should be session-scoped, in-memory, typed, opaque to state, and cleared by the reducer-requested cleanup effect. A test scheduler should fire callbacks explicitly rather than wait on wall time. Production composition must never bind synthetic engines unless Demo Mode and a scenario are explicitly selected. AVFoundation, Vision, CoreNFC, and reviewed face adapters can replace fakes behind the same observation contracts without changing policy semantics.

The final cross-platform contract review should occur before iOS implementation begins. Android Milestones 2 through 6 now provide the MRZ, state-machine, effect-handling, policy-isolation, and real camera/OCR boundary vocabulary to review.

## Milestone 6 conceptual parity

The Android CameraX/ML Kit code is not portable to iOS, but these decisions should remain aligned:

- preserve synchronous deterministic fakes while adding async/cancellation-safe real protocol implementations;
- keep capture buffers in a single-session owner and expose only opaque references to orchestration;
- attach AVFoundation preview in the host UI layer without putting pixel buffers in SwiftUI state;
- use on-device Vision text recognition with no cloud fallback or recognized-text logging;
- keep TD3 candidate ranking separate from ICAO parsing, check digits, dates, and validation;
- echo exact operation tokens through the serialized actor and suppress late completions after cleanup; and
- offer explicit Demo versus Real mode selection before a session with no silent fallback.

An iOS implementation should use its own calibrated image-quality path and lifecycle/permission APIs. It must not copy Android thresholds or process-lifecycle behavior without device-specific review.

## Milestone 7 conceptual parity

The Android `NfcAdapter`, `Tag`, and `IsoDep` code is not portable to iOS. CoreNFC parity should preserve these decisions:

- distinguish device/session unavailability from a disabled or unsupported capability using CoreNFC's own APIs;
- create one `NFCTagReaderSession` only for a reducer-issued NFC operation and invalidate it on cancellation, timeout, terminal cleanup, or host teardown;
- retain `NFCISO7816Tag` and APDU/protocol objects inside the iOS adapter, never SwiftUI or verification state;
- use a separately reviewed BAC/PACE implementation and never copy or translate Android/JMRTD cryptographic code;
- retain MRZ-derived access material, DG1, DG2, portraits, SODs, and certificates behind session-scoped opaque references;
- map printed/chip comparison to `MATCH`, `MISMATCH`, or `INCONCLUSIVE` observations only;
- represent Passive Authentication honestly and require a governed offline CSCA trust store before reporting it valid; and
- keep Chip Authentication unsupported unless a reviewed native implementation is integrated and device-tested.

Android's current protocol boundary is intentionally blocked because the candidate library did not pass exact source/logging/license review. iOS must perform its own library and entitlement review; Android's blocker is neither an endorsement nor a rejection of any iOS-specific library.
