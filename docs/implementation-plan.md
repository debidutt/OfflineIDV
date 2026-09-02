# Project Atlas implementation plan

## Repository assessment

The Milestone 1 workspace began empty: it had no Git metadata, build files, source modules, architecture, or conventions. The only input was the Project Atlas brief. There is therefore no existing functionality to migrate and no version baseline to preserve.

The local toolchain provides JDK 17 and Android SDK platforms through API 35 (plus a 36.1 preview/minor platform), with Android Build Tools 36.0.0. The new build will use stable, fixed versions compatible with JDK 17 and the installed API 35 platform. A Gradle wrapper is mandatory so no system Gradle installation is required.

Dependency and build-tool freshness lint checks are intentionally disabled. This repository pins reviewed tool versions for reproducibility, and upgrades require a dedicated maintenance change with full gate verification rather than being pulled into a product milestone because a newer release exists. All source, resource, manifest, API-level, and dependency-usage lint checks remain enabled with warnings treated as errors.

## Architectural baseline

Android namespace: `com.ing.offlineidv`

Demo application ID: `com.ing.offlineidv.demo`

Planned module graph:

```text
android:app-demo
  -> android:ui
      -> android:verification
      -> android:accessibility
      -> android:core

android:verification
  -> android:camera
  -> android:ocr
  -> android:mrz
  -> android:nfc
  -> android:face
  -> android:storage
  -> android:analytics
  -> android:core

android:{camera,ocr,mrz,nfc,face,storage,analytics,accessibility}
  -> android:core
```

All modules are Android Gradle modules at foundation time so they can evolve toward native platform integrations without restructuring. `core` and the eventual MRZ domain remain Android-framework-free Kotlin despite using the Android library packaging plugin. The app is the only application module.

Dependency recommendations are deliberately minimal for Milestone 1:

- Android Gradle Plugin with built-in Kotlin support, avoiding a redundant Kotlin Android plugin.
- JUnit 4.13.2 for fast local contract tests supported by the Android Gradle unit-test pipeline.
- Spotless for deterministic Kotlin and Gradle Kotlin DSL formatting checks.
- No Compose, AndroidX, Hilt, CameraX, ML Kit, NFC passport, storage, biometric, coroutine, or crypto dependency until the milestone that needs it.

## Security baseline and known gaps

Milestone 1 establishes opaque sensitive-value handling, safe error codes, redacted string representations, no network permission, private-by-default app configuration, and documentation of the offline/data-lifecycle boundaries.

The foundation is not yet a usable identity-verification SDK. In particular, encrypted temporary persistence, cleanup orchestration, expiry enforcement, screenshot policy, root/debug mitigations, and the complete threat model belong to Milestone 9. No real or realistic identity data may be added while those controls are absent.

## Milestones and gates

### Milestone 1 — Repository foundation (complete)

Deliver:

- `AGENTS.md`, Gradle wrapper and fixed tool versions.
- Android application/library module graph and unique namespaces.
- Framework-free core configuration, session, result, signal, clock, redaction, sensitive-value, and error contracts.
- Initial contract/security tests.
- Initial README, architecture overview, ADRs, and iOS mapping.

Gate:

- Formatting, unit tests, Android lint, full debug assembly, and demo-app debug assembly pass.
- No parser, transition reducer, fake engine, UI screen, or platform integration is present.

### Milestone 2 — MRZ engine (complete)

Delivered constrained normalization, ICAO 9303 check-digit calculation, TD3 models/parser, semantic dates, synthetic fixtures, safe validation results, and comprehensive pure-Kotlin unit tests.

Gate: valid, invalid, ambiguous, and expired synthetic cases pass; no orchestration or UI work has begun.

Decisions:

- Keep the MRZ implementation framework-free and deterministic behind `MrzParser`, `MrzNormalizer`, `MrzCheckDigitCalculator`, and `MrzDateInterpreter` contracts.
- Normalize only line endings, surrounding whitespace, ASCII spaces, and case. Preserve filler characters and reject unsupported characters or shapes rather than guessing.
- Correct `O`/`I` only in numeric birth and expiry fields, and always retain safe ambiguity metadata.
- Resolve two-digit birth and expiry years through documented, configurable windows relative to an injected reference date.
- Return structured validation evidence independently from expiry status; an expired passport can remain structurally and cryptographically self-consistent.
- Retain no raw MRZ lines in the parsed document model and redact domain/result string representations.

Known risks and remaining work:

- Two-digit years are inherently policy-dependent; downstream orchestration must preserve ambiguity and must not treat a preferred interpretation as verified truth.
- TD3 coverage is intentionally strict and does not yet include extended document-number variants or an issuing-state code registry.
- MRZ check digits detect transcription inconsistency but do not authenticate the document or holder.
- Immutable input strings cannot be zeroized; callers remain responsible for capture-buffer lifecycle, while parsed models avoid retaining the original lines.
- Milestone 3 now defines how validation, ambiguity, and expiry evidence drive deterministic transitions before any engine implementation is added.

The Milestone 3 entry criteria were satisfied: Milestone 2 gates remained green, MRZ evidence mapping preserves invalid checksums, unresolved dates, ambiguity, and expiry, and the reducer has no platform dependency.

### Milestone 3 — Verification state machine (complete)

Delivered closed states, events, effects, safe dispositions, structured evidence, configurable policy, deterministic retry/timeout tokens, a pure transition reducer, MRZ evidence mapping, orchestration interfaces, and comprehensive valid/illegal-transition tests.

Gate: the transition table is documented and deterministic; all terminal outcomes request cleanup; no real or fake engines are present.

Decisions:

- Keep evidence separate from terminal outcome and evaluate explicit failures before insufficient or ambiguous evidence.
- Pass only `MrzValidationResult` through `MrzEvidenceMapper`; parsed passport fields and raw MRZ never enter state-machine contracts.
- Represent external work as intent-only effects carrying typed opaque references.
- Tokenize every asynchronous operation with session, step, and monotonic generation so stale and duplicate completions are deterministic no-ops.
- Model step timeout, matching session expiry, and host cancellation as distinct events and outcomes.
- Retain artifact references only in active progress; terminal summaries contain evidence, retry counts, outcome, and safe reason only.
- Define synchronous callback-oriented orchestration interfaces without imposing coroutines, Flow, Android lifecycle, or a concrete orchestrator implementation.

Known risks and remaining work:

- A host must use one immutable `VerificationContext` for a session; changing policy between dispatches would intentionally change deterministic results.
- Future effect handlers must echo operation tokens exactly and serialize dispatch so a stale callback cannot be mistaken for current work.
- `ClearSensitiveSessionData` is only an intent until Milestone 9 supplies and verifies cleanup mechanics.
- Opaque references reduce accidental disclosure but their external registry and lifecycle do not exist yet.
- Policy behavior is a prototype baseline and requires product/legal/security review before any production claim.
- MRZ, chip, passive-authentication, and face evidence remain consistency signals, not a general authenticity or liveness guarantee.

Milestone 4 entry criteria were satisfied: Milestone 3 gates remained green, effect handling and exact-token ownership were reviewed, each scenario maps to safe observations/events/evidence, and explicit Demo Mode composition prevents synthetic engines from becoming a production fallback.

### Milestone 4 — Deterministic demo engines (complete)

Delivered feature-owned camera/OCR/NFC/face contracts and policy-free synthetic engines; an external-behavior-only scenario catalog; a single-session opaque artifact registry; real MRZ parsing/mapping; a deterministic scheduler; a serialized orchestrator; an effect handler with safe exception translation; idempotent cleanup; and a safe end-to-end runner.

Gate: all required synthetic offline scenarios are deterministic and explicitly demo-configured; every terminal outcome cleans memory/scheduling resources; operation/session tokens are echoed exactly; and fake engines contain no verification policy, retry, state, event-dispatch, next-step, or outcome logic.

Formatting, all 243 Gradle tests, Android lint, full debug assembly, demo-app debug assembly, targeted module coverage, and the source audits pass. The generated debug APK and library AAR artifacts were verified after packaging.

Decisions:

- Keep feature engine contracts and fakes in their owning modules so dependency direction remains `verification -> feature contracts -> core`.
- Put scenario composition, artifact ownership, MRZ translation, effects, scheduling, serialized event dispatch, cleanup, and terminal reporting in `verification`.
- Store synthetic content only in a typed in-memory registry whose references are scoped by issuing-object identity as well as session and kind.
- Queue synchronous completion events FIFO and update reducer state before handling effects, preventing recursive state mutation.
- Keep scenarios limited to external observations and lifecycle stimuli; only the existing reducer and policy evaluator determine retries, progression, and outcomes.
- Use a no-clock scheduler that fires only by explicit token in tests and the safe runner.

Known risks and remaining work:

- Demo artifacts use immutable JVM strings/objects and cannot promise zeroization; they are synthetic, memory-only, redacted, and dereferenced on cleanup.
- The runtime is synchronous and intended for JVM tests. Real asynchronous platform adapters must add cooperative cancellation while retaining FIFO dispatch and exact-token semantics.
- Synthetic chip/passive-auth/face observations are integration signals, not cryptographic, biometric, authenticity, or liveness implementations.
- Production dependency-injection/composition bindings do not exist yet; Milestone 5 may bind only the explicitly labelled Demo Mode path in `app-demo`.
- Secure production persistence, expiry enforcement, and cleanup verification remain Milestone 9.

Milestone 5 readiness criteria are exact: all Milestone 4 formatting, unit-test, lint, full assembly, demo-app assembly, targeted module tests, security audits, and policy-isolation audits pass; all 15 scenarios reach reducer-selected terminal outcomes with cleanup; Production Mode rejects demo construction; safe presentation inputs are limited to state/effect names, evidence, outcome, and cleanup status; and no Compose/UI, ViewModel, navigation, CameraX, ML Kit, `IsoDep`, Hilt, real face model, persistence, network, or real identity fixture has entered the repository.

### Milestone 5 — Compose demo application (complete)

Delivered the launchable Atlas Verify Material 3 application; exhaustive redacted reducer-to-UI mapping; persistent offline/Demo Mode disclosure; welcome, grouped scenario selection, document selection, passport guidance, synthetic capture, MRZ summary, simulated NFC, synthetic selfie, processing, recovery, and six terminal-result presentations; shared TalkBack descriptions; a lifecycle-safe ViewModel adapter; and a single explicitly Demo Mode composition root.

Gate: production and JVM test source compiles; all 278 JVM tests pass; all 25 Compose instrumentation tests compile; formatting, lint, full debug assembly, and demo-app assembly pass; and offline/security/policy-isolation source audits pass. The repository contains 303 tests in total. The launchable debug APK was verified at `android/app-demo/build/outputs/apk/debug/app-demo-debug.apk`. Instrumentation execution requires an attached Android device or emulator and is reported separately from JVM execution.

Decisions:

- Keep `VerificationUiStateMapper` exhaustive and restrict `AtlasUiState` to predefined copy, five-step progress, finite evidence status, reducer retry availability, and terminal outcome.
- Keep welcome/scenario/passport-instruction surfaces presentation-only; dispatch `PassportSelected` only when the user continues from instructions.
- Add default-preserving automatic/host-controlled demo prompts so the interactive host can render existing `AwaitingNfc` and `AwaitingSelfie` states without changing reducer flow.
- Construct the runtime only in `AtlasDemoCompositionRoot`; use a small ViewModel solely as the Android/Compose observation adapter.
- Lock scenario selection after runtime creation and require terminal cleanup before replacing a session.
- Use no Hilt, navigation framework, coroutine runtime, artificial delays, hardware APIs, connectivity detection, persistence, or real identity fixtures.

Known risks and remaining work:

- The UI instrumentation suite is compile-verified but still needs device/emulator execution for rendered semantics and interaction confirmation.
- Synthetic execution is synchronous, so processing states may be brief on a real device; no artificial sleep was added.
- Process death restarts the synthetic demo rather than restoring a sensitive session. Production lifecycle/persistence design remains Milestone 9.
- Screenshot policy, root/debug mitigations, production cleanup guarantees, and full threat modelling remain Milestone 9.
- Material 3/Compose versions are deliberately pinned to versions compatible with the repository's API 35 toolchain; upgrades require a separate reviewed maintenance change.

Milestone 6 readiness criteria are exact: all Milestone 5 formatting, JVM tests, lint, full assembly, demo-app assembly, test-source compilation, manifest/offline audits, raw-data exclusion, and UI policy-isolation audits pass; the APK exists and launches `MainActivity`; the default Success path reaches the reducer's `VERIFIED` outcome with cleanup; all failure/recovery scenarios remain reducer-owned; UI code references no feature fake or policy evaluator; and Milestone 6 work starts only behind existing camera/OCR contracts with lifecycle-safe, cancellable adapters and no direct UI construction.

### Milestone 6 — Real Android camera and OCR (complete)

Delivered explicit Demo and Real Android runtime modes; CameraX preview and still capture behind asynchronous feature contracts; reducer-driven camera permission handling; session-owned in-memory capture and OCR artifacts; deterministic resolution, exposure, and edge-energy quality analysis; bundled on-device ML Kit Latin text recognition; pure policy-free TD3 candidate extraction; reuse of the Milestone 2 parser and Milestone 3 evidence mapper; exact-token asynchronous effect translation; late-callback suppression; and terminal cleanup. Demo Mode remains the default and is unchanged at its feature boundary. Real Android Mode never falls back to synthetic engines and advertises only the camera capability, so unavailable NFC and face requirements remain honest reducer/policy results.

Gate: all 319 JVM tests pass; all 28 instrumentation tests, including the existing 25 Compose tests and three real-adapter packaging/OCR tests, compile; formatting, lint, all-module debug assembly, and the separate demo-app assembly pass; the APK exists; and manifest, sensitive-data, raw-payload, fake-engine policy-isolation, real-engine policy-isolation, UI policy-isolation, and Milestone 7 scope audits pass. No compatible camera device or emulator was available in the execution sandbox: ADB could not bind its local server socket, so device-only permission, preview, capture, bundled-recognizer execution, and airplane-mode behavior remain compiled but not executed.

Decisions:

- Preserve existing synchronous fake contracts and add backward-compatible asynchronous siblings for CameraX and ML Kit; neither the reducer nor verification policy switches on runtime mode.
- Pin CameraX 1.5.3 to retain the reviewed compile SDK 35 baseline. CameraX 1.6.x requires compile SDK 36, and that migration is separate maintenance scope.
- Bind `Preview` and `ImageCapture` to process lifecycle from the Android composition root and pass only a preview surface provider across the UI/platform boundary.
- Keep encoded captures in a single-session in-memory owner and recognized text in a separate verification-owned opaque artifact store; clear both idempotently on terminal cleanup.
- Use configurable minimum dimensions, mean-luma bounds, and horizontal/vertical edge energy as a deliberately basic quality gate. It reports observations only and makes no retry decision.
- Bundle `com.google.mlkit:text-recognition:16.0.1` so OCR has no first-use model download and works without the `INTERNET` permission; accept its documented APK-size cost.
- Keep candidate ranking in pure Kotlin and separate from normalization, ICAO field parsing, check digits, date interpretation, validation, evidence, and outcomes.

Known risks and remaining work:

- Camera permission grant/denial, preview binding/unbinding, rotation, capture, activity recreation, device-vendor behavior, low-memory behavior, bundled OCR execution, and airplane-mode operation still require physical-device/emulator execution.
- The quality thresholds are deterministic prototype heuristics, not calibrated document-quality assurance; device-specific tuning requires a representative, non-production test corpus.
- Encoded buffers are zeroed where Atlas owns mutable arrays, but JVM strings, decoded bitmaps, CameraX internals, and ML Kit internals cannot provide a complete zeroization guarantee.
- Process-lifecycle camera ownership avoids retaining an Activity but may keep use cases bound across Activity recreation; foreground/background behavior needs device validation.
- Bundled ML Kit increases APK size, while the CameraX 1.5.3 pin defers compile SDK 36 and CameraX 1.6.x review.
- No secure persistence is present. Real artifacts are memory-only, so process death intentionally loses the active session.

Milestone 7 readiness criteria are exact: the Milestone 6 formatting, 319-JVM-test, lint, all-module assembly, separate demo-app assembly, instrumentation-compilation, manifest/offline, artifact-cleanup, raw-payload, fake/real/UI policy-isolation, Demo regression, and scope audits remain green; `android/app-demo/build/outputs/apk/debug/app-demo-debug.apk` exists; the existing reducer and verification policy remain unchanged except for the additive camera-preparation failure event; CameraX and ML Kit remain behind feature/effect contracts; and Milestone 7 begins only with separately reviewed NFC capability/tag/session contracts, a lifecycle-safe `IsoDep` boundary, an approved maintained BAC strategy, no custom cryptography, no Demo fallback, and no camera/OCR policy expansion. Device validation of the compiled Milestone 6 permission, preview, capture, OCR, cleanup, and airplane-mode tests must be completed on suitable hardware before any production-readiness claim; its absence is a documented environment limitation rather than authorization to weaken Milestone 7 boundaries.

### Milestone 7 — Android NFC integration (complete at the safe transport boundary; protocol blocked)

Delivered Android NFC capability checks, resumed-host/pending-operation reader mode, recreation-safe Activity rebinding, one-session tag coordination, `Tag`/`IsoDep` containment, connection timeout/close/error translation, exact-token real-effect routing, clearable opaque access/DG/portrait artifacts, six-state Passive Authentication observations, policy-free TD3 printed/chip comparison, Real Mode NFC guidance, and source/manifest/security tests. Demo Mode, CameraX, ML Kit, the reducer, and policy semantics remain unchanged.

The mandatory ePassport-library checkpoint did not approve a packaged protocol implementation. JMRTD 0.8.8 is actively maintained and supports Android/BAC/PACE, but its exact source/transitive artifacts were unavailable to the restricted build environment, historical source contains a BAC-key logging path, and LGPL distribution has not received product/legal approval. Atlas therefore opens/closes `IsoDep`, transmits no APDU, never opens the MRZ-derived key, and reports `PROTOCOL_UNSUPPORTED`. Real Android Mode never falls back to fake NFC. DG1, DG2, BAC, PACE, Passive Authentication, and Chip Authentication are not claimed as implemented.

Gate: all 358 JVM tests pass with no failures or skips; all 31 instrumentation tests compile; formatting, warnings-as-errors lint across all 12 Android modules, all-module debug assembly, and the separate demo-app assembly pass. The source, packaged, and binary manifests retain optional camera/NFC hardware, package only `CAMERA`, `NFC`, and the signature-scoped AndroidX dynamic-receiver permission, and contain neither `INTERNET` nor `ACCESS_NETWORK_STATE`. The final `0.7.0-milestone7` debug APK exists at `android/app-demo/build/outputs/apk/debug/app-demo-debug.apk` (55,557,232 bytes; SHA-256 `953ae6fdf8aeb026d3ac36e05660d92c8dd6ad41206f6394bac8f8e6c599deb2`). Real-NFC policy isolation, no-APDU/identity-log, no-custom-crypto, opaque-artifact, Demo regression, Camera/OCR regression, and Milestone 8 scope audits pass. Device execution remains separate and unavailable because ADB cannot bind its local smart-socket listener in the sandbox.

Known risks and remaining work:

- Protocol-level ePassport reading remains blocked until an exact-version source/logging/dependency/license review passes. The current build cannot read DG1/DG2 or perform BAC/PACE.
- The app has no governed offline CSCA trust store; Passive Authentication is unavailable/not performed, never valid, and chip access alone is not an authenticity claim.
- Chip Authentication is unsupported and no clone-resistance claim is made.
- NFC reader mode, disabled-state recovery, tag positioning/removal, connection timeout, Activity recreation, and airplane-mode operation require representative NFC-capable device execution.
- Milestone 6 camera permission, preview, capture, bundled OCR, and airplane-mode device validation remains outstanding; Milestone 7 does not hide or supersede that gap.
- Mutable Atlas-owned NFC arrays are overwritten on cleanup, but Android/JVM/chip-library internals may retain copies once a protocol library is eventually integrated.

Milestone 8 readiness criteria are exact: all Milestone 7 formatting, JVM tests, lint, all-module assembly, separate demo-app assembly, instrumentation-compilation, source/merged/binary manifest, offline, artifact-cleanup, APDU/identity-log, real-NFC policy-isolation, Demo regression, Camera/OCR regression, and scope audits pass; the debug APK exists with only expected `CAMERA` and `NFC` sensitive permissions and no network permission; representative-device gaps remain explicit; Project Atlas contains no custom passport cryptography; Real Mode has no fake NFC fallback; and the protocol blocker is either resolved through an approved exact-version library review plus authorized DG1/DG2 device validation or explicitly accepted as a transport-only product limitation before Milestone 8 begins. Milestone 8 must not be used to bypass the NFC security checkpoint.

### Milestone 7.1 — ePassport protocol investigation (complete; implementation not authorized)

Completed an exact-version research, source/security, privacy/logging, license, provider, dependency/permission, Android, offline-trust, architecture, supply-chain, and alternatives review in [`docs/security/epassport-protocol-investigation.md`](security/epassport-protocol-investigation.md). The preferred future candidate is `org.jmrtd:jmrtd:0.8.8`, classified **ACCEPT WITH LIMITATIONS**. The project recommendation is **PROCEED ONLY AFTER SPECIFIC APPROVALS**; the current runtime remains transport-only and returns `PROTOCOL_UNSUPPORTED`.

M7.1 added no production dependency or protocol code. BAC, PACE, secure messaging, DG1/DG2 reads, Passive Authentication, Chip Authentication, and a CSCA trust store remain unimplemented. Written security, legal/open-source/product, privacy, architecture, Android/release, PKI/trust-governance, and product-risk approvals plus the documented supply-chain/build/device acceptance conditions are required before a separately authorized implementation milestone. If they are not satisfied, Atlas keeps the transport-only fallback. Milestone 8 has not started.

### Milestone 7.2 — JMRTD approval preparation (complete; human approvals pending)

Converted the M7.1 investigation into a reviewable implementation-approval package without adding or integrating a dependency. The package freezes the published JMRTD 0.8.8 runtime graph and checksums, defines supply-chain and LGPL/distribution review controls, proposes the Atlas-owned `PassportProtocolEngine` boundary and Kotlin-like contract, specifies fail-closed PACE/BAC selection, sensitive logging/exception containment, bounded memory/LDS/DG/portrait handling, structured Passive Authentication evidence, offline CSCA governance, an optional Chip Authentication extension, an ePassport threat model, and a concrete real-device/passport validation matrix.

Documents:

- [`docs/security/jmrtd-approval-package.md`](security/jmrtd-approval-package.md)
- [`docs/security/epassport-threat-model.md`](security/epassport-threat-model.md)
- [`docs/testing/epassport-device-validation-matrix.md`](testing/epassport-device-validation-matrix.md)
- [`docs/architecture/passport-protocol-engine-proposal.md`](architecture/passport-protocol-engine-proposal.md)
- [`docs/security/offline-csca-trust-strategy.md`](security/offline-csca-trust-strategy.md)

M7.2 is approval preparation only. No approval box is marked, the production runtime still stops at `PROTOCOL_UNSUPPORTED`, and JMRTD, Scuba, Bouncy Castle, EJBCA, BAC, PACE, secure messaging, DG1/DG2 reads, Passive Authentication, Chip Authentication, and a CSCA trust store remain absent/unimplemented. M6 camera/OCR and M7 NFC hardware-validation debt remains open. M7.3 has not started and must not start until every blocking human review is approved or approved with explicitly accepted conditions. Milestone 8 has not started.

### Milestone 8 — Face module

Deliver selfie/quality/template/comparison contracts, deterministic fake behavior, and a documented extension point for a reviewed on-device matcher.

Gate: no production identity or liveness claim is made.

### Milestone 9 — Security hardening

Deliver Keystore-backed private temporary persistence where unavoidable, cleanup on all terminal paths, expiry enforcement, screenshot/debug/root considerations, a full threat model, and cleanup/redaction tests.

Gate: sensitive data lifecycle is verified and residual risks are documented.

### Milestone 10 — Conference polish

Deliver airplane-mode presentation, non-sensitive timing, recovery UX, backup demo path, five-minute demo script, and final documentation audit.

Gate: a clean install completes all deterministic scenarios in airplane mode and confirms cleanup.

## Exact Milestone 1 file groups

- Root/build: `AGENTS.md`, `README.md`, `.gitignore`, `.editorconfig`, Gradle settings/build/properties, version catalog, wrapper files.
- Modules: `android/app-demo` plus `core`, `camera`, `ocr`, `mrz`, `nfc`, `face`, `verification`, `storage`, `analytics`, `accessibility`, and `ui` Gradle/manifest foundations.
- Core source/tests: configuration, session/clock, result, error, verification signal/requirement, sensitive-value, and redaction contracts with initial tests.
- Architecture: `docs/architecture/overview.md`, `docs/ios-mapping.md`, and ADRs 0001–0008.

## Exact Milestone 2 file groups

- MRZ source: normalization, check digits, models, validation, date interpretation, and TD3 parser under `android/mrz/src/main/kotlin`.
- MRZ tests: conspicuously synthetic fixtures and focused calculator, normalizer, date, parser/validator, and redaction suites under `android/mrz/src/test/kotlin`.
- Architecture: `docs/architecture/mrz-engine.md` and ADR 0009 for date and ambiguity policy.

## Exact Milestone 3 file groups

- Verification source: state/event/effect/disposition models, evidence, policy, retry/timeout tokens, opaque references, MRZ mapper, policy evaluator, pure reducer, and orchestration interfaces under `android/verification/src/main/kotlin`.
- Verification tests: synthetic fixtures plus happy-path, policy, MRZ mapping, capture/MRZ failures, NFC/face, lifecycle, matrix, and redaction suites under `android/verification/src/test/kotlin`.
- Core errors: backward-compatible safe verification failure reasons in `android/core`.
- Architecture: `docs/architecture/verification-state-machine.md` and ADR 0010 for tokenized asynchronous operations.

Explicitly deferred after Milestone 3: concrete orchestrator/effect handlers, demo fakes, Compose, CameraX, ML Kit, NFC transport/BAC, face implementation, dependency-injection wiring, temporary storage implementation, the complete threat model, and the demo script.

## Exact Milestone 4 file groups

- Feature source: policy-neutral contracts and deterministic fake observations under `android/camera`, `android/ocr`, `android/nfc`, and `android/face`.
- Verification source: scenario catalog, artifact registry, real-MRZ adapter, effect handler, safe runner, deterministic scheduler, and serialized orchestrator under `android/verification/src/main/kotlin`.
- Tests: fake-engine unit suites in each feature module plus registry, MRZ pipeline, effect routing, scheduler, serialized dispatch, policy isolation, and 15-scenario end-to-end suites in `android/verification/src/test/kotlin`.
- Core compatibility: additive explicit-demo and invalid-artifact error reasons plus expanded synthetic scenario vocabulary.
- Architecture: `docs/architecture/demo-engines.md`, ADR 0011, overview, iOS parity, README, and this plan.

Explicitly deferred after Milestone 4: Compose/screens/navigation/ViewModels, Android lifecycle integration, CameraX, ML Kit, `IsoDep`/BAC, a real face engine, Hilt composition, persistent storage, production cleanup hardening, and conference demo UX/script.

## Exact Milestone 5 file groups

- UI source: redacted screen models, exhaustive verification-state mapper, Material 3 theme/screens, progress/evidence components, test tags, and accessibility semantics under `android/ui/src/main/kotlin`.
- App source: `MainActivity`, `AtlasDemoViewModel`, `AtlasDemoController`, and the sole Demo Mode composition root under `android/app-demo/src/main/kotlin`.
- Accessibility source: shared safe TalkBack descriptions under `android/accessibility/src/main/kotlin`.
- Verification compatibility: default-preserving `DemoPromptMode` and a generic configured lifecycle-stimulus helper under `android/verification`.
- Tests: mapper/redaction/policy-isolation JVM tests, controller integration/cleanup tests, prompt-mode tests, and Compose instrumentation source under the owning modules.
- Architecture: `docs/architecture/demo-ui.md`, ADR 0012, overview, README, and this plan.

At Milestone 5 completion, CameraX, ML Kit, real image capture/OCR, `IsoDep`/BAC, real NFC hardware, a real face engine or liveness, Hilt production bindings, persistence, screenshot/root/debug hardening, and Milestone 10 timing/script polish were explicitly deferred. CameraX, bundled ML Kit, and real image capture/OCR were subsequently delivered in Milestone 6; all other items remain deferred.

## Exact Milestone 6 file groups

- Camera source: backward-compatible asynchronous capture, preparation, and quality contracts; `CameraXDocumentCaptureEngine`; `CameraXDocumentQualityEngine`; `InMemoryCapturedImageStore`; and the pure luma quality analyzer under `android/camera`.
- OCR source: backward-compatible asynchronous OCR/image contracts, bundled `MlKitOcrEngine`, and pure `MrzCandidateExtractor` under `android/ocr`.
- Verification source: a session-scoped opaque text artifact store, real MRZ pipeline, and additive camera-preparation failure translation under `android/verification`.
- App/UI source: explicit pre-session runtime-mode selection, permission gateway, Android-only preview host, real effect handler/factory, lifecycle cleanup, and redacted presentation updates under `android/app-demo` and `android/ui`.
- Core/build source: cancellable-operation contract, CameraX/ML Kit dependencies, app permission/feature metadata, and Milestone 6 version metadata.
- Tests: candidate, quality, artifact ownership, real MRZ, async routing/staleness/cleanup, runtime selection, raw-payload, manifest, opaque capture, bundled OCR, and fake/real/UI policy-isolation coverage in the owning modules.
- Architecture: `docs/architecture/android-camera-ocr.md`, ADR 0013, overview, demo UI, iOS conceptual parity, README, and this plan.

Explicitly deferred after Milestone 6: Android NFC/`IsoDep`, BAC/PACE and chip reads, real selfie capture, face matching or liveness, secure persistence, Hilt, network services, production analytics, screenshot/root/debug hardening, and conference timing/script polish. Android NFC/`IsoDep` transport was subsequently delivered in Milestone 7; BAC/PACE and chip reads remain blocked by the documented library-security checkpoint. Milestone 8 has not started.

## Exact Milestone 7 file groups

- NFC source: asynchronous engine/capability/tag-session contracts, single-read coordinator, protected chip artifacts, chip-validation/comparison observations, Android capability/reader-mode/`IsoDep` adapter, and unchanged deterministic fakes under `android/nfc`.
- Verification/app source: closeable session-artifact cleanup, additive Passive Authentication statuses, exact-token real NFC effect translation, real capability composition, Activity rebinding, and no reducer/policy outcome logic under `android/verification` and `android/app-demo`.
- UI/accessibility source: Real Android chip guidance and disabled-hardware copy supplied from the host while Demo-only NFC disclosure remains explicit and the shared `VerificationUiStateMapper` remains runtime-mode agnostic.
- Core/build source: additive predefined NFC failures, `CAMERA`/`NFC` manifest declarations with network removals, explicit app-to-NFC composition dependency, and Milestone 7 version metadata.
- Tests: capability, session, cancellation, duplicate/stale callback, artifact, comparison, passive-status, effect integration, policy variation, source isolation, logging/crypto, manifest, UI, and instrumentation-compilation coverage in owning modules.
- Architecture/security: `docs/architecture/android-epassport-nfc.md`, `docs/security/epassport-library-review.md`, ADRs 0014–0015, overview, iOS mapping, README, and this plan.

Explicitly deferred after Milestone 7: an approved ePassport protocol dependency, BAC/PACE, DG1/DG2 production reads, governed CSCA trust material, Passive/Chip Authentication execution, real selfie capture, face matching or liveness, secure persistence, Hilt, network services, production analytics, screenshot/root/debug hardening, and conference timing/script polish. Milestone 8 has not started.
