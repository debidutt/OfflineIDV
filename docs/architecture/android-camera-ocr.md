# Android camera and OCR architecture

## Scope

Milestone 6 adds a real Android document path while retaining the deterministic synthetic path:

```text
Demo Mode
  -> FakeDocumentCaptureEngine -> FakeDocumentQualityEngine -> FakeOcrEngine

Real Android Mode
  -> CameraXDocumentCaptureEngine
  -> CameraXDocumentQualityEngine
  -> MlKitOcrEngine
  -> MrzCandidateExtractor
  -> existing Td3MrzParser / MrzEvidenceMapper
```

Both paths enter and leave the existing `VerificationEffectHandler` boundary. Feature engines return observations only. They do not dispatch verification events, inspect reducer state, select a next step, decide retry, evaluate policy, or create a terminal outcome.

Android NFC/`IsoDep`, face matching, persistence, Hilt, networking, and Milestone 7 behavior were outside Milestone 6. Milestone 7 subsequently adds the separately documented NFC transport boundary without changing this camera/OCR design.

## Contract compatibility

The Milestone 4 synchronous `DocumentCaptureEngine`, `DocumentQualityEngine`, and `OcrEngine` contracts remain unchanged for deterministic fakes. Additive `AsyncDocumentCaptureEngine`, `AsyncDocumentQualityEngine`, `AsyncCameraPreparationEngine`, and `AsyncOcrEngine` siblings let lifecycle-backed adapters complete without blocking. Each asynchronous call returns a cooperative `CancellableOperation`.

The real effect handler retains the reducer-issued `VerificationOperationToken`, registers one cancellation slot for the operation, and sends the eventual observation through the serialized orchestrator. A stale completion retains its original token and is rejected by the existing reducer rules. Terminal cleanup cancels every registered slot before dropping sensitive artifacts.

`CameraPreparationFailed` is an additive event for a CameraX bind failure. It enters the reducer rather than allowing a platform adapter to manufacture a state or outcome. Existing event behavior and policies are unchanged.

## CameraX lifecycle and preview

`CameraXDocumentCaptureEngine` owns `Preview` and `ImageCapture` use cases and binds them to `ProcessLifecycleOwner`. Process lifecycle avoids retaining a destroyed Activity across recreation; the ViewModel-owned runtime survives recreation, and a replacement `PreviewView` surface can attach without creating a second engine operation. Backgrounding the application stops the process lifecycle.

The Compose UI accepts an optional composable preview slot. It knows neither CameraX nor `PreviewView`. `MainActivity` supplies a thin `AndroidView` host, and the composition root connects its surface provider to the camera adapter. Removing the composable detaches the surface. Closing the adapter unbinds all use cases.

Camera permission is requested only for the reducer's `RequestCameraPermission` effect. An Activity Result launcher reports granted or denied through the existing events. Camera preparation does not start before a granted result. The launcher-facing gateway is rebindable across Activity recreation and never enters domain modules.

Still capture uses CameraX's in-memory callback. `ImageProxy` bytes, dimensions, and rotation metadata are copied into the active-session store and the proxy is closed in `finally`. No `Bitmap`, `ImageProxy`, URI, file, byte array, or path enters verification state or UI.

## Artifact ownership

Two single-session, memory-only owners are used:

- `InMemoryCapturedImageStore` in `camera` owns encoded captured bytes by positive opaque source token. It rejects another session, unknown tokens, and all access after cleanup. Cleanup overwrites the owned byte arrays before removal and is idempotent.
- `SessionArtifactStore` in `verification` owns document handles, OCR text artifacts, and MRZ/NFC-bound wrappers behind identity-scoped `VerificationArtifactReference` values. Terminal cleanup drops all references and prevents reuse.

The OCR bridge makes a short-lived defensive byte copy, decodes it locally, and zeroes it on ML Kit task completion. JVM strings and ML Kit/bitmap internals cannot promise zeroization; the design instead minimizes retention, prevents rendering/logging, and drops references promptly.

No file, MediaStore entry, external directory, public storage, backup payload, or long-term persistence is used.

## Image-quality heuristic

`ImageQualityAnalyzer` is pure Kotlin. The Android adapter decodes the capture and downsamples luma analysis to at most 256 pixels on the longest edge while preserving the original dimensions for resolution checks.

Default thresholds are:

| Observation | Threshold |
| --- | --- |
| Insufficient resolution | width below 1,000 px or height below 600 px |
| Too dark | mean luma below 45 |
| Too bright | mean luma above 220 |
| Too blurry | mean squared horizontal adjacent-pixel difference below 180 |

The blur value is a basic edge-energy proxy, not full image-quality assurance. It may reject low-texture pages or accept sharp glare/edges. Thresholds are configurable and deterministic. Structured internal findings are mapped to the established accepted/rejected quality observation; the reducer alone decides whether recovery is offered.

## Bundled ML Kit OCR

The OCR adapter uses `com.google.mlkit:text-recognition:16.0.1`, the statically bundled Latin recognizer. The model is packaged with the application and is available immediately in airplane mode; no first-use Play Services download gate or `INTERNET` permission is required. The app manifest explicitly removes the dependency's optional `INTERNET` and `ACCESS_NETWORK_STATE` contributions. At Milestone 6 the merged app manifest contained only `CAMERA`; Milestone 7 subsequently adds the expected `NFC` permission while retaining the network removals. Google's published guidance estimates roughly 4 MB per script architecture for the bundled option; actual APK size is reported from the assembled artifact.

`MlKitOcrEngine` decodes the session-owned image locally, creates an `InputImage` with CameraX rotation metadata, and processes it asynchronously. Success returns `OcrTextArtifact`; failure maps to a predefined safe `IdvError`. It never logs text. Cancellation suppresses delivery even where the underlying ML Kit task cannot be forcibly interrupted. Completion always recycles the bitmap and clears the adapter-owned byte copy. Closing the runtime closes the recognizer.

## MRZ candidate extraction

`MrzCandidateExtractor` is pure Kotlin in the OCR module. It:

- uppercases and retains only `A-Z`, `0-9`, and `<` after tolerating OCR whitespace;
- joins at most three adjacent line fragments;
- considers TD3-like lines from 38 to 48 characters;
- requires a passport-like first line and numeric content in the second;
- prefers `P<`, lengths nearest 44, and stable source order; and
- exposes the selected pair only through scoped access with a redacted `toString`.

It does not parse ICAO fields, calculate check digits, interpret dates, correct ambiguous fields, or declare validity. `RealMrzPipeline` passes the candidate to the existing Milestone 2 `Td3MrzParser`, maps its `MrzValidationResult` through the existing Milestone 3 `MrzEvidenceMapper`, and keeps derived printed/access material in the session artifact store.

## Runtime composition

`AtlasRuntimeMode` is selectable only on Welcome:

- `DEMO` remains the default and exposes the existing fifteen deterministic scenarios.
- `REAL_ANDROID` constructs CameraX, quality, bundled ML Kit, and the real MRZ pipeline. It never falls back to fakes.

The real verification context advertises only the camera capability. The existing default policy still requires NFC and face evidence, so a structurally valid real MRZ proceeds to the existing reducer decision and reports required unavailable capabilities honestly. No NFC or face implementation is smuggled into this milestone.

The same mapper, capture action, processing screens, recovery presentation, and terminal presentation are used for both modes. UI models structurally cannot contain image or OCR payloads.

## Privacy and limitations

- The manifest adds only `CAMERA`; `INTERNET` and NFC permissions remain absent.
- No image, OCR text, MRZ, path, platform exception message, or sensitive metadata is logged or added to analytics.
- Backup remains disabled and cleartext traffic remains disabled.
- Real capture requires a physical/emulated back camera and a granted runtime permission.
- The quality heuristic is deliberately basic and requires calibration across representative devices before production assessment.
- OCR is Latin TD3-oriented and does not support other document formats.
- Process death restarts the active session; persistence is intentionally absent.
- ML Kit recognition and CameraX preview/capture require device testing across vendors, orientation changes, low memory, and denied/permanently-denied permissions.
- This milestone makes no document authenticity, NFC, face, biometric, or liveness claim.

## Extension path

Milestone 7 may add a real NFC effect adapter without changing camera/OCR engines, the MRZ parser, UI payload boundaries, or reducer authority. Later lifecycle/security work may replace the memory owner with reviewed private expiring storage only if capture retention becomes unavoidable. Camera quality algorithms may be replaced behind the same observation contract after calibration; they must remain policy-free.
