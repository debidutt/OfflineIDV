# Android ePassport NFC architecture

## Scope and safety status

Milestone 7 implements the real Android NFC capability, host lifecycle, tag discovery, and `IsoDep` connection boundary. The mandatory library review did not approve a BAC/PACE dependency, so the adapter closes the transport and returns the predefined `PROTOCOL_UNSUPPORTED` observation after identifying an `IsoDep` tag. Real Android Mode never substitutes a fake chip read.

No Project Atlas source constructs passport APDUs or implements BAC, PACE, secure messaging, ASN.1, LDS decoding, passive-authentication signature verification, or Chip Authentication.

## Runtime flow

```text
VerificationStateMachine
  -> StartNfcRead(operation token, opaque access-key reference)
  -> RealVerificationEffectHandler resolves access material in the session store
  -> NfcSessionCoordinator starts one cancellable read
  -> AndroidNfcTagDiscovery enables reader mode only while the host is resumed and a read is pending
  -> NfcAdapter.ReaderCallback keeps Tag inside the Android adapter
  -> IsoDep.get(Tag) and AndroidPassportChipSession own connect/timeout/close
  -> reviewed protocol boundary (blocked in Milestone 7)
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

The Activity attaches to the discovery adapter while resumed and detaches while paused. The adapter enables Android reader mode only when all three conditions hold: an Activity is attached, NFC is available, and one read is pending. Detach, cancellation, completion, and close disable reader mode. Activity recreation can reattach the same ViewModel-owned runtime without placing an Activity, `Tag`, or `IsoDep` in reducer or Compose state.

The coordinator accepts one tag session for one active read. Duplicate tags are closed. Callbacks from cancelled or replaced reads are ignored, and their sessions are closed. Terminal cleanup cancels the active coordinator operation before clearing artifacts.

## IsoDep boundary

`AndroidNfcTagDiscovery` calls `IsoDep.get(Tag)` and creates `AndroidPassportChipSession`; neither Android type leaves the NFC platform package. The session owns the connection timeout, `connect`, connection-loss translation, and idempotent `close`. Atlas exposes no public `transceive` or APDU-byte API.

Because no library passed review, the session does not transmit an APDU. After a successful `IsoDep` connection it returns `PROTOCOL_UNSUPPORTED` and closes the transport. This proves lifecycle and resource ownership without weakening the no-custom-crypto rule.

## BAC and PACE

BAC and PACE are not implemented. The intended strategy is PACE first when a reviewed library reports compatible CardAccess parameters, with BAC fallback only when the document/library combination safely supports it. Downgrade must never happen after an authentication or integrity failure and must be represented as an explicit protocol observation.

See `docs/security/epassport-library-review.md` for the blocked selection checkpoint.

## DG1, DG2, and comparison boundary

The NFC contracts can retain parsed DG1 identity material and optional DG2 portrait bytes only inside a sensitive `ChipDataArtifact`. The artifact's string form is always redacted. `Td3PrintedChipComparisonEngine` compares only document number, date of birth, expiry date, and nationality and returns `MATCH`, `MISMATCH`, or `INCONCLUSIVE`; it returns no identity values and makes no product decision.

The blocked protocol adapter produces no DG1 or DG2 in Real Mode. DG1/DG2 success is therefore not claimed. The prepared artifact/validation/comparison boundary is covered with conspicuously synthetic unit inputs so a reviewed protocol reader can be connected without changing the reducer or policy evaluator.

## Passive and Chip Authentication

Passive Authentication has an explicit six-state observation: `NOT_PERFORMED`, `VALID`, `FAILED`, `UNAVAILABLE`, `UNSUPPORTED`, and `TECHNICAL_ERROR`. Atlas has no governed CSCA trust store, so Real Mode can only report `UNAVAILABLE`/`NOT_PERFORMED`; it cannot report `VALID`. Passive Authentication, even when valid, proves signature/hash relationships under the configured trust material, not the holder's identity and not liveness.

Chip Authentication is unsupported. Atlas makes no clone-resistance claim and does not simulate success.

## Artifact ownership and cleanup

MRZ-derived access material, printed data, chip data, and portrait material are registered in the single-session `SessionArtifactStore`. Reducer state contains only opaque references. Identity-based reference ownership prevents an equal-looking reference from another store/session from resolving. Store cleanup closes clearable NFC artifacts, removes all references, and is idempotent. Cancellation and every terminal reducer path trigger transport cancellation and store cleanup.

No raw APDU, DG, access key, document number, name, date, nationality, portrait, SOD, or certificate is placed in UI state, navigation, errors, logs, or persistence.

## Offline and manifest boundary

The source manifest declares optional NFC hardware and `android.permission.NFC`. It continues to remove `INTERNET` and `ACCESS_NETWORK_STATE` contributions. The runtime makes no network request. Final verification audits the source manifest, merged manifest, binary APK permissions, and packaged dependencies.

## Device-test strategy and limitations

JVM tests cover capability mapping, duplicate/stale/cancelled callbacks, connection-loss/timeout/error translation, artifact isolation/cleanup/redaction, neutral comparison, exact-token effect translation, and policy isolation. Instrumentation source covers Android capability lookup, `IsoDep` recognition/session wiring, lifecycle cleanup, permission packaging, and offline declarations where a device permits.

An NFC-capable Android device plus an authorized non-production/test passport is required to execute reader mode, tag removal, airplane mode, Activity recreation, and—after a library is approved—BAC/PACE and DG reads. Runtime-only identity input must never enter committed fixtures or assertions. The existing Milestone 6 camera/OCR device-validation gap remains open as well.
