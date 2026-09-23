# Android ePassport NFC architecture

## Scope and safety status

Milestone 7 implements the real Android NFC capability, host lifecycle, tag discovery, and `IsoDep` connection boundary. The approved M7.3 extension adds a contained JMRTD 0.8.8 adapter for PACE-first/BAC access and a bounded DG1 read. The approved M7.4 extension adds bounded SOD/DG14 reads, Netherlands residence-permit Passive Authentication, and Chip Authentication. Real Android Mode never substitutes a fake chip read.

Project Atlas does not implement cryptographic primitives or construct protocol APDUs. JMRTD and its reviewed provider own BAC, PACE, secure messaging, LDS decoding, signature primitives, and Chip Authentication behind the NFC adapter. Atlas selects reviewed algorithms, bounds inputs, verifies signed-data relationships, and maps results to finite observations. DG2, AA, EAC-TA, runtime trust retrieval, and revocation checking are not implemented.

## Runtime flow

```text
VerificationStateMachine
  -> StartNfcRead(operation token, opaque access-key reference)
  -> RealVerificationEffectHandler resolves access material in the session store
  -> NfcSessionCoordinator starts one cancellable read and emits payload-free progress
  -> AndroidNfcTagDiscovery keeps reader mode enabled while the host is resumed and the read is active
  -> NfcAdapter.ReaderCallback keeps Tag inside the Android adapter
  -> IsoDep.get(Tag) and AndroidPassportChipSession own connect/timeout/close
  -> IsoDepCardServiceBridge contains APDU transport
  -> JmrtdPassportProtocolReader selects PACE/BAC and reads bounded DG1/SOD/DG14
  -> PassportChipAuthenticity verifies signed data/trust, then requests fresh Chip Authentication
  -> safe NfcReadResult
  -> effect handler echoes the exact operation token as a VerificationEvent
  -> reducer/policy remain the only flow, retry, and outcome authorities
```

## Capability model

`AndroidNfcCapabilityDetector` distinguishes:

- `UNAVAILABLE`: no NFC feature or adapter;
- `DISABLED`: hardware exists but the user disabled NFC; and
- `AVAILABLE`: hardware and adapter are enabled.

The composition root advertises the reducer's NFC capability when hardware exists. Disabled hardware therefore remains a recoverable adapter observation instead of being misrepresented as absent hardware. The host UI can explain that NFC must be enabled, but it cannot change settings automatically or bypass reducer recovery.

## Tag discovery lifecycle

The Activity attaches to the discovery adapter while resumed and detaches while paused. The adapter enables Android reader mode only when all three conditions hold: an Activity is attached, NFC is available, and one read is active. Reader mode remains enabled after a tag is claimed so Android keeps the RF field available through `IsoDep.connect()` and the complete protected chip read. It is disabled only when that read is cancelled, completes, is replaced, the host detaches, or the adapter closes. Detach also closes a claimed physical-tag lease, including the detach/discovery race; an active read that has not discovered a tag can resume discovery when the host reattaches. Activity recreation can reattach the same ViewModel-owned runtime without placing an Activity, `Tag`, or `IsoDep` in reducer or Compose state.

The coordinator accepts one tag session for one active read. Duplicate tags are closed. Callbacks from cancelled or replaced reads are ignored, and their sessions are closed. Terminal cleanup cancels the active coordinator operation before clearing artifacts.

One short, best-effort vibration is requested after a new physical tag callback has been claimed. A per-discovery atomic gate prevents repeated pulses during the same read. The adapter uses `VibratorManager` on API 31 and newer, the compatible `Vibrator` service below API 31, checks `hasVibrator`, and catches platform failures. Demo engines never construct or call this adapter. The manifest declares the normal `VIBRATE` permission; no runtime permission prompt is needed.

## Reactive progress and safe diagnostics

The NFC feature emits only `READER_ACTIVE`, `TAG_DETECTED`, `CONNECTING`, and `READING`. The UI initially shows Starting NFC reader; only Android's successful `enableReaderMode` call advances it to Ready to scan. The real effect handler translates later engine observations into token-bound verification events, the reducer owns monotonic `NfcReadPhase`, and the shared mapper projects Chip detected, Connecting, Scan in progress, and Scan complete. Connection loss, access denial, unsupported chip/access control, timeout, and other errors remain distinct predefined reducer recovery observations. Compose never interprets APDUs, authentication evidence, or product outcomes, and the progress indicator is indeterminate rather than a fabricated percentage.

Debuggable builds can receive `NFC_DIAG` lines for closed stages such as reader mode, tag discovery, `IsoDep`, CardAccess, PACE/BAC, DG1, Passive Authentication, DG14, and Chip Authentication. Diagnostics contain only stage/status enums, predefined safe error codes, and closed authenticity observations. Raw MRZ values, access keys, tag identifiers, APDUs, status payloads, certificates, exception messages, and LDS data cannot be represented by the diagnostic contract. Diagnostic failures never alter verification behavior.

## IsoDep boundary

`AndroidNfcTagDiscovery` calls `IsoDep.get(Tag)` and creates `AndroidPassportChipSession`; neither Android type leaves the NFC platform package. The session owns the connection timeout, connection-loss translation, and idempotent `close`. Transport close is allowed to run concurrently with a blocked APDU so cancellation or host detachment can interrupt `IsoDep`; a close-requested I/O failure retains `TAG_LOST` instead of being reclassified as authentication denial. Atlas exposes no public `transceive` or APDU-byte API.

`IsoDepCardServiceBridge` is the only production source allowed to mention Scuba command/response APDU types or call `IsoDep.transceive`. It registers no listener, keeps no transcript, caps command and response frames at the smaller of the device maximum and 64 KiB, clears Atlas-owned temporary command/response copies, and emits only finite transport classifications. `JmrtdPassportProtocolReader` contains JMRTD file access and BAC/PACE calls; `PassportChipAuthenticity` contains JMRTD/Java-provider signature, certificate, hash, and Chip Authentication calls. Third-party JUL namespaces are disabled before protocol use, and raw exceptions, messages, keys, document data, APDUs, status payloads, certificates, and library object strings never leave the adapter.

## BAC and PACE

EF.CardAccess is read under a 64 KiB bound before access selection. The closed PACE allowlist contains ECDH generic-mapping with AES-CBC-CMAC 128/192/256 and standardized EC parameter identifiers 8 through 18. Selection is deterministic and prefers the strongest reviewed AES suite.

PACE is attempted whenever a compatible reviewed suite is advertised. Any PACE authentication, transport, integrity, provider, parameter, timeout, or technical failure terminates that read; the PACE path contains no BAC call. BAC is eligible only when CardAccess is absent or contains no compatible reviewed suite and the adapter's explicit BAC permission is enabled. Malformed, unreadable, or oversized CardAccess always fails closed and never enables BAC.

## DG1, DG2, and comparison boundary

The NFC contracts retain parsed DG1 identity material only inside a sensitive `ChipDataArtifact`. The artifact's string form is always redacted. `MrzPrintedChipComparisonEngine` compares only document number, date of birth, expiry date, and nationality for both TD3 and TD1 and returns `MATCH`, `MISMATCH`, or `INCONCLUSIVE`; it returns no identity values and makes no product decision.

EF.DG1 is read under a 4 KiB bound. The adapter extracts the four fields and discards the raw DG1 after authentication processing. DG2 is never requested. The TD3 and TD1 OCR pipelines store the same minimal four printed fields plus only the MRZ-derived access fields, rather than retaining raw MRZ lines for NFC comparison.

The Netherlands residence-permit profile requires NFC, printed/DG1 consistency, Passive Authentication, and Chip Authentication and does not require face comparison. This is a host composition choice; the reducer, policy evaluator, verification states, and shared state-to-UI mapper do not know JMRTD, CameraX, ML Kit, Android NFC, TD1 transport details, or runtime mode.

## Passive and Chip Authentication

Passive Authentication has an explicit six-state observation: `NOT_PERFORMED`, `VALID`, `FAILED`, `UNAVAILABLE`, `UNSUPPORTED`, and `TECHNICAL_ERROR`. EF.SOD is capped at 1 MiB. The adapter accepts SHA-256/384/512, reviewed RSA/ECDSA/PSS signatures, RSA keys of at least 2048 bits, and EC keys of at least 256 bits. It verifies the DG1 hash, signed attributes/SOD signature, SOD signer identifier, DSC validity and purpose, and a direct DSC signature under the bundled anchor. A missing, stale, uncovered, unsupported, malformed, or invalid prerequisite never becomes `VALID`.

The read-only trust snapshot contains only Netherlands residence-permit CSCA serial 4, loaded from the official Netherlands PKD export and pinned by SHA-256. It is usable from 2026-09-22 inclusive until 2027-01-22 exclusive and then fails closed. Runtime network retrieval is forbidden. Revocation is not evaluated, so `VALID` means hash/signature/direct-chain/current-validity checks succeeded under this bounded snapshot; it does not mean the DSC was proven unrevoked. Broader Master List, link-certificate, rollover, historical-anchor, CRL, rollback, distribution, and governance work remains blocking.

Chip Authentication is attempted only after valid Passive Authentication and an authenticated DG14 hash. EF.DG14 is capped at 64 KiB. The selector accepts exactly one matching ECDH key of at least 256 bits and Chip Authentication version 1 with AES-CBC-CMAC 256/192/128, strongest first. DH, 3DES, version 2, unknown suites, mismatched key identifiers, and ambiguity fail closed. JMRTD executes the fresh key agreement and secure-messaging transition; Atlas reports `SUCCEEDED`, explicit authentication failure, or a non-success finite category without exposing protocol values.

Signed-data authenticity and live chip-key possession remain separate evidence rows. Their combination supports a bounded clone-resistance statement for the authenticated key, not holder identity, liveness, entitlement, revocation status, or complete document validity.

## Artifact ownership and cleanup

MRZ-derived access material, printed data, chip data, and portrait material are registered in the single-session `SessionArtifactStore`. Reducer state contains only opaque references. Identity-based reference ownership prevents an equal-looking reference from another store/session from resolving. Store cleanup closes clearable NFC artifacts, removes all references, and is idempotent. Cancellation and every terminal reducer path trigger transport cancellation and store cleanup.

No raw APDU, DG, access key, document number, name, date, nationality, portrait, SOD, DG14, public key, or certificate is placed in UI state, navigation, errors, logs, or persistence.

## Offline and manifest boundary

The source manifest declares optional NFC hardware plus `android.permission.NFC` and the normal `android.permission.VIBRATE`. It continues to remove `INTERNET` and `ACCESS_NETWORK_STATE` contributions. The runtime makes no network request. Final verification audits the source manifest, merged manifest, binary APK permissions, and packaged dependencies.

## Device-test strategy and limitations

JVM tests cover capability mapping, duplicate/stale/cancelled callbacks, connection-loss/timeout/error translation, fail-closed PACE/BAC selection, LDS byte limits and zeroization, fingerprint-pinned snapshot freshness, fail-closed Chip Authentication suite/key selection, distinct PA/CA evidence and policy, artifact isolation/cleanup/redaction, TD1/TD3-neutral comparison, dependency configuration, exact-token effect translation, and policy isolation. Instrumentation source covers Android capability lookup, `IsoDep` recognition/session wiring, lifecycle cleanup, permission packaging, and offline declarations where a device permits.

An NFC-capable Android device plus an authorized runtime-only Netherlands residence permit and representative passport are required to execute reader mode, tag removal, airplane mode, Activity recreation, PACE/BAC, DG1/SOD/DG14 reads, PA, and CA. The permit must establish actual DSC issuer, CSCA generation, algorithms, and CA version/suite coverage. Runtime identity input must never enter committed fixtures, logs, screenshots, or assertions. Revocation policy/material, dependency locking/verification, legal/open-source approval, independent crypto/PKI review, debug/release D8/R8, fuzzing, final packaging audits, and the complete device matrix remain release blockers. The existing Milestone 6 camera/OCR device-validation gap remains open as well.
