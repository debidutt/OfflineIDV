# PassportProtocolEngine proposal (Milestone 7.2)

Status: historical M7.2 proposal. Its limited protected-access/DG1 subset was implemented under ADR 0016 and its bounded Netherlands residence-permit PA/CA subset under ADR 0017. Proposed DG2, broad trust, and remaining phases are not implemented. The pseudocode and phase labels below remain design history, not the exact production API.

## 1. Boundary and ownership

```text
Verification reducer
        ↓ VerificationEffect.StartNfcRead + exact operation token
RealVerificationEffectHandler
        ↓ opaque access/transport references
PassportProtocolEngine                         Atlas protocol boundary
        ↓ internal JMRTD adapter
JMRTD 0.8.8 + private Bouncy Castle provider   protocol/crypto boundary
        ↓ internal Scuba CardService bridge
existing Atlas IsoDep transport                Android boundary
        ↓
passport chip
```

Atlas continues to own:

- Android Activity lifecycle, NFC capability, reader mode, `Tag` discovery, and `IsoDep` open/timeout/close;
- one active session, exact verification operation tokens, callback serialization, stale/late-event suppression, cancellation, and deadlines;
- session artifact storage, reference identity, size accounting, cleanup, and cross-session isolation;
- allowlisted requests, fail-closed suite policy, safe protocol observations, stable errors, and redaction;
- the verification reducer, `VerificationPolicyEvaluator`, retries, user flow, final evidence aggregation, and terminal outcomes.

JMRTD should own:

- eMRTD APDU sequencing and protocol mechanics;
- BAC, PACE, secure messaging, and protocol/session-key derivation;
- CardAccess, LDS, DG1, DG2, DG14, and SOD parsing;
- DG/SOD cryptographic primitives exposed through the adapter; and
- EAC/Chip Authentication mechanics only if separately approved.

Bouncy Castle should own the required cryptographic primitives through a privately constructed provider instance. Atlas must not globally register, reorder, shade, relocate, fork, or reproduce provider/protocol cryptography.

The protocol boundary must not expose Android `Tag`, `IsoDep`, a raw APDU API, raw MRZ/BAC/PACE/session keys, secure-messaging state, raw DG/SOD bytes, platform exceptions, JMRTD/Scuba/Bouncy Castle/EJBCA classes, or sensitive library `toString()` output to verification state, UI, analytics, errors, or callers.

## 2. Proposed Kotlin-like contract

This pseudocode describes responsibilities and shapes only. It must not be copied into production until M7.3 is explicitly authorized and reviewed.

```kotlin
// All types are Atlas-owned, finite, redacted, and framework/library-free.
internal interface PassportProtocolEngine {
    fun read(
        request: PassportProtocolRequest,
        cancellation: ProtocolCancellation,
        observer: (ProtocolObservation) -> Unit,
    ): PassportProtocolResult
}

internal data class PassportProtocolRequest(
    val sessionId: IdvSessionId,
    val operation: VerificationOperationToken,
    val accessMaterialRef: PassportAccessMaterialRef,
    val transportRef: PassportTransportRef,
    val requestedFiles: Set<RequestedPassportFile>,
    val accessPolicy: PassportAccessPolicy,
    val limits: PassportReadLimits,
    val trustSnapshotRef: TrustSnapshotRef?,
)

internal enum class RequestedPassportFile {
    DG1_MINIMUM_COMPARISON_FIELDS,
    DG2_PORTRAIT,
    SOD,
    DG14_FOR_SEPARATELY_APPROVED_CA,
}

internal data class PassportAccessPolicy(
    val compatiblePaceSuites: Set<ReviewedPaceSuite>,
    val allowBacWhenNoCompatiblePace: Boolean,
    val chipAuthentication: ChipAuthenticationPolicy,
)

internal interface ProtocolCancellation {
    val isCancelled: Boolean
    fun throwIfCancelled()
}

internal data class PassportReadLimits(
    val maxCardAccessBytes: Long,
    val maxDg1Bytes: Long,
    val maxDg2Bytes: Long,
    val maxSodBytes: Long,
    val maxGenericLdsFileBytes: Long,
    val maxEncodedPortraitBytes: Long,
    val maxDecodedPortraitPixels: Long,
    val maxDecodedPortraitBytes: Long,
    val maxSessionNfcArtifactBytes: Long,
    val maxSingleResponseBytes: Int,
    val deadline: MonotonicDeadline,
)

internal sealed interface PassportProtocolResult {
    data class Completed(
        val observation: PassportReadObservation,
        val dg1ComparisonRef: Dg1ComparisonArtifactRef?,
        val portraitRef: PortraitArtifactRef?,
        val passiveAuthentication: PassiveAuthenticationEvidence,
        val chipAuthentication: ChipAuthenticationObservation,
    ) : PassportProtocolResult

    data class Failed(val failure: PassportProtocolFailure) : PassportProtocolResult
    data object Cancelled : PassportProtocolResult
}

internal sealed interface PassportProtocolFailure {
    data object AccessControlUnsupported : PassportProtocolFailure
    data object AccessAuthenticationFailed : PassportProtocolFailure
    data object SecureMessagingFailed : PassportProtocolFailure
    data object MalformedCardAccess : PassportProtocolFailure
    data object MalformedLds : PassportProtocolFailure
    data class LimitExceeded(val kind: PassportLimitKind) : PassportProtocolFailure
    data object TransportLost : PassportProtocolFailure
    data object TimedOut : PassportProtocolFailure
    data object CryptoUnavailable : PassportProtocolFailure
    data object TrustEvidenceUnavailable : PassportProtocolFailure
    data object InternalTechnicalFailure : PassportProtocolFailure
}
```

The engine is constructed with internal collaborators that resolve `PassportAccessMaterialRef` and `PassportTransportRef` only inside the adapter, bridge the existing Atlas `IsoDep` lease to JMRTD's `CardService`, and commit bounded artifacts atomically to the existing session store. Those collaborators are intentionally omitted from the outward contract so they cannot become general secret or APDU access APIs.

Every public `toString()` of a future Atlas-owned type must be constant/redacted. `Completed` is a protocol observation, not `VerificationOutcome`. The effect handler translates it into existing/additive safe events; only the reducer and policy decide retry, progression, or final outcome.

## 3. PACE/BAC fail-closed policy

### Invariants

1. CardAccess is parsed under a byte limit before access-control selection.
2. Only explicitly reviewed PACE OIDs, mapping types, key agreements, ciphers, digests, parameters, curves, and access-key types are compatible.
3. If at least one compatible PACE suite is advertised, Atlas selects deterministically from the reviewed allowlist and attempts PACE.
4. Once PACE is attempted, no PACE authentication, integrity, malformed-data, transport, timeout, provider, or technical failure may invoke BAC in that operation or retry generation.
5. BAC can be considered only when no compatible PACE suite is advertised and product/security policy explicitly permits BAC for that document/context.
6. A malformed or unreadable CardAccess does not prove that no compatible PACE exists. It fails closed and never enables BAC.
7. Unknown key types/algorithms/parameters are unsupported; JMRTD's best-effort/fallback branches must be precluded by adapter validation.
8. Access success establishes a protected channel/access observation only. It is not authenticity, signer trust, clone resistance, holder identity, or liveness.

### Decision table

The table defines protocol action and safe protocol observation only. It deliberately defines no final verification outcome.

| Case | Protocol action | Safe protocol observation |
| --- | --- | --- |
| CardAccess absent; BAC policy allowed | Record no compatible PACE evidence; attempt BAC once within the same deadline. | `CARD_ACCESS_ABSENT`, then `BAC_ATTEMPTED`. |
| CardAccess absent; BAC forbidden | Do not attempt BAC or PACE. | `CARD_ACCESS_ABSENT`, `BAC_FORBIDDEN`. |
| CardAccess malformed/truncated/over limit | Stop; do not infer BAC eligibility. | `CARD_ACCESS_MALFORMED` or `LIMIT_EXCEEDED(CARD_ACCESS)`. |
| One or more compatible PACE suites advertised | Select one deterministic reviewed suite; attempt PACE. BAC becomes ineligible for this operation and its retries. | `PACE_COMPATIBLE_ADVERTISED`, `PACE_ATTEMPTED`. |
| Compatible and unsupported PACE suites both advertised | Ignore unsupported suites; select a compatible reviewed suite. | `PACE_COMPATIBLE_ADVERTISED`, optionally `PACE_ADVERTISED_PARTIALLY_UNSUPPORTED`, `PACE_ATTEMPTED`. |
| Only unsupported PACE suites advertised; BAC policy explicitly allowed for this context | No PACE attempt. Because no compatible suite exists, attempt BAC only under the explicit policy exception. | `PACE_ADVERTISED_UNSUPPORTED`, `BAC_ATTEMPTED_BY_POLICY`. |
| Only unsupported PACE suites advertised; BAC forbidden | Stop without BAC. | `PACE_ADVERTISED_UNSUPPORTED`, `BAC_FORBIDDEN`. |
| PACE success | Continue over the PACE-established secure-messaging channel. | `PACE_SUCCEEDED`. |
| PACE authentication failure | Stop and close; never BAC. | `PACE_AUTHENTICATION_FAILED`, `DOWNGRADE_BLOCKED`. |
| PACE integrity/secure-messaging failure | Stop and close; never BAC. | `PACE_SECURE_MESSAGING_FAILED`, `DOWNGRADE_BLOCKED`. |
| PACE technical/provider/unsupported-parameter failure after selection | Stop and close; never BAC. | `PACE_TECHNICAL_FAILURE` or `PACE_PARAMETERS_UNSUPPORTED`, `DOWNGRADE_BLOCKED`. |
| PACE timeout/cancellation/tag loss | Cancel/close immediately; never BAC. | `PACE_TIMED_OUT`, `CANCELLED`, or `TRANSPORT_LOST`; when applicable also `DOWNGRADE_BLOCKED`. |
| BAC selected and succeeds | Continue over BAC secure messaging. | `BAC_SUCCEEDED`. |
| BAC selected and authentication fails | Stop and close. | `BAC_AUTHENTICATION_FAILED`. |
| BAC technical/secure-messaging failure | Stop and close. | `BAC_TECHNICAL_FAILURE` or `BAC_SECURE_MESSAGING_FAILED`. |
| BAC timeout/cancellation/tag loss | Cancel/close immediately. | `BAC_TIMED_OUT`, `CANCELLED`, or `TRANSPORT_LOST`. |

Retries are reducer-owned new operation generations. A retry may repeat the same eligible access method after explicit user/policy authorization; it must not reinterpret a previous PACE failure as BAC eligibility.

## 4. Sensitive logging and exception containment

### Mandatory runtime rules

- Configure `java.util.logging` for `org.jmrtd`, `net.sf.scuba`, and reviewed Bouncy Castle namespaces before the first library class is loaded. Verify the Android JUL/logcat bridge in debug and release builds.
- Never register JMRTD or Scuba APDU listeners. Assert listener collections remain empty; provide no adapter API that accepts a listener.
- Keep Active Authentication disabled. Never call the JMRTD AA path that logs a command APDU on transmission failure.
- Never interpolate, concatenate, format, serialize, inspect in a debugger/crash event, or forward `toString()` from JMRTD access keys, PACE specs/results, MRZ/DG objects, session keys, wrappers, APDUs, SODs, certificates, or exceptions.
- Never pass a library/platform exception, its message, cause chain, stack trace, suppressed exceptions, localized message, or library class name upward.
- Catch at the adapter boundary and create a fresh finite Atlas failure with a stable code and predefined safe description. Correlation metadata may include only session-safe operation generation, phase enum, limit kind, and retry-neutral category.
- Never log DG1/DG2 values, portrait bytes/metadata derived from identity, SOD/certificate subjects/issuers/serials, access material, protocol parameters tied to a document, APDU bytes/status payloads, secure-messaging state, or tag identifiers.
- Treat Bouncy Castle messages and ASN.1/certificate exceptions as attacker-controlled/sensitive. Map by caught type and operation phase, not message text.
- Suppress Scuba logging and never expose its APDU events. No external logging framework should be added.
- `cert-cvc:1.4.13` has direct `printStackTrace()` paths that bypass JUL. Before M7.3, either exclude it with accepted build/runtime proof or demonstrate those classes/methods are unreachable and absent from the shrunken release. Merely suppressing a logger is insufficient.

### Exception translation model

```text
library/platform exception
        ↓ caught inside one protocol phase
phase + finite classification (no message/cause/object)
        ↓
PassportProtocolFailure
        ↓ effect-handler mapping
stable IdvError/NFC observation
        ↓
reducer decides retry/progression/outcome
```

The adapter may keep the original exception only on the current stack long enough to classify it; it must not retain it in an artifact, callback, future, log, metric, state, or crash reporter.

### Proof strategy

1. Inject unique sentinel document numbers, dates, MRZ text, access bytes, session keys, APDU command/response bytes, DG content, portrait bytes, SOD subjects, certificate serials, and exception messages.
2. Capture JUL, Android logcat, stdout/stderr, uncaught-exception/crash hooks, state/effect/event rendering, UI semantics, analytics, and test reports in both debuggable and minified release builds.
3. Exercise BAC/PACE success and every failure; malformed CardAccess/LDS/SOD/certificate/image; tag loss; timeout; cancellation; AA-prohibited code; and cert-cvc reachability.
4. Assert no sentinel, byte encoding (hex/base64/decimal), library class name, raw status word, or raw exception text appears.
5. Run static source/bytecode audits for APDU listener registration, AA invocation, `printStackTrace`, `System.out/err`, logger configuration timing, string interpolation, exception forwarding, and prohibited `toString()` paths.
6. Treat any leak as a release blocker, clear captured artifacts, fix containment, and repeat the complete suite.

## 5. Memory and artifact ownership

All material is memory-only. The session artifact store remains the authority; reducer state contains only opaque, identity-scoped references.

| Material | Owner and boundary | Lifetime and cleanup | Proposed maximum |
| --- | --- | --- | --- |
| MRZ access material | Existing Atlas session store; resolved only inside protocol adapter | From validated MRZ until NFC completes/cancels/terminal cleanup; close and overwrite Atlas-owned `CharArray` | Exact TD3 access fields only; no duplicate full MRZ |
| BAC/PACE derived keys | JMRTD protocol invocation | Shortest protocol scope; dereference on channel close/failure/cancel | Algorithm-defined fixed size; no artifact-store entry |
| Secure-messaging session keys/state | JMRTD wrapper inside adapter | One transport lease; discard before callback/close | Algorithm-defined; never copied to Atlas models |
| DG1 raw bytes | Bounded adapter buffer only | Parse, extract four comparison fields, overwrite buffer immediately | 4 KiB raw file cap |
| Minimal DG1 comparison fields | Atlas NFC artifact store, not reducer/UI | Until comparison or session cleanup; mutable owned representation | Four fixed-format fields only |
| DG2 raw bytes | Bounded adapter buffer/stream | Until controlled portrait extraction; overwrite/drop before callback | 4 MiB raw file cap |
| Encoded portrait | Session artifact store behind `PortraitArtifactRef` | Until future face comparison or terminal/cancel cleanup | 4 MiB encoded cap |
| Decoded portrait | Controlled image decoder/face-engine handoff, never reducer | Decode on demand; release immediately after use and on cancellation | 8 million pixels and 32 MiB decoded cap |
| SOD raw bytes | Bounded adapter/PA component | Until PA evidence is produced; overwrite/drop | 1 MiB cap |
| DSC/certificates | PA/trust component behind internal artifact/evidence boundary | Current PA operation only unless a reviewed immutable trust snapshot owns public anchors | Count/encoded-size bounded by SOD and aggregate caps |
| Temporary APDU buffers | JMRTD/Scuba bridge and Atlas transport lease | One exchange; overwrite Atlas-owned mutable buffer after consumption | No transcript; single response at most 64 KiB and device maximum |
| Aggregate NFC artifacts | Session artifact store with atomic accounting | Current session only; entire store cleared idempotently on cancel, expiry, replacement, or terminal outcome | 48 MiB including encoded/decoded working set |

Cancellation closes `IsoDep`/card service first to unblock I/O, prevents new reads/commits, invalidates the exact operation token, zeroes Atlas-owned buffers, closes all `AutoCloseable` artifacts, and drops references. A result commits artifacts only if the session/token is still active and every requested artifact fits the remaining aggregate budget. Partial data is never published.

JVM/Android and third-party zeroization is best effort. Immutable `String`, provider/JMRTD internals, temporary arrays created by libraries, decoder buffers, garbage-collected copies, and OS/driver buffers may survive until reclaimed. Atlas must minimize conversions/copies, use mutable owned arrays where possible, overwrite those arrays, keep lifetimes short, avoid persistence/swap claims, and state honestly that complete zeroization cannot be guaranteed.

## 6. Bounded LDS read strategy

Every number below is **PROPOSED — REQUIRES DEVICE/CORPUS VALIDATION** except the existing reducer's 60-second NFC timeout, which supplies the current outer deadline. The values are conservative engineering starting points: DG1 is normatively tiny; DG2 is expected to dominate; decoded 32-bit images cost four bytes per pixel; and SOD/other selected files should be far smaller than portrait data. M7.3 must measure a representative authorized corpus and lower or raise a limit only through security/privacy review.

| Resource | Proposed control and justification |
| --- | --- |
| CardAccess | 64 KiB; parse from a bounded stream before suite selection. It contains security infos, not images. Malformed length never permits BAC. |
| DG1 | 4 KiB; far above TD1/TD2/TD3 MRZ payload needs while preventing attacker-selected allocation. |
| DG2 | 4 MiB encoded LDS file; expected largest required DG. Stream and reject declared/actual length conflicts before allocation. |
| SOD | 1 MiB; allows CMS, hashes, and a DSC while bounding ASN.1/certificate work. |
| Generic LDS file | 4 MiB per allowlisted file, with tighter file-specific caps above. Never enumerate/read unrequested DGs. DG14, if approved, should receive a tighter corpus-derived cap. |
| Encoded portrait | 4 MiB and no greater than its bounded DG2 container; one selected face image unless product/security approve multiples. |
| Decoded portrait | Maximum 4096 on either dimension, 8 million total pixels, and 32 MiB estimated decoded allocation; inspect dimensions before full decode and subsample where supported. |
| Total NFC artifact memory | 48 MiB including raw/encoded/decoded buffers and known working copies; reserve budget before allocation/commit and account concurrent copies. |
| APDU response accumulation | Keep no APDU history. At most one response buffer of `min(device maxTransceiveLength, 64 KiB)`; stream file content into its bounded destination and enforce monotonic byte counts. |
| Duration | Existing 60-second reducer NFC deadline is the hard outer bound; every transport operation uses only remaining monotonic time. No parser/decode future survives cancellation/deadline. |
| Complexity | Limit ASN.1/TLV nesting, collection/member counts, certificate/signer counts, and parser iterations after corpus measurement; integer arithmetic must be overflow-safe before allocations. |

On declared-length, actual-length, count, nesting, pixel, allocation, aggregate, APDU, or deadline excess, stop parsing/reading, close transport, clear partial artifacts, and report `LimitExceeded(kind)` or `TimedOut`. The protocol layer does not retry, reject the user, or choose a final `VerificationOutcome`.

## 7. DG1 data minimization

Atlas needs only the fields already used by its policy-free printed/chip comparison engine.

| DG1 field | Classification | Reason |
| --- | --- | --- |
| Document number | **REQUIRED** | Primary printed/chip document binding field. |
| Date of birth | **REQUIRED** | Existing TD3 comparison field; helps prevent a document-number-only match. |
| Expiry date | **REQUIRED** | Existing TD3 comparison field and captured-document binding. |
| Nationality | **REQUIRED** | Existing TD3 comparison field and additional consistency signal. |
| Name | **NOT REQUIRED** | Existing comparison does not use it; transliteration/format variance adds PII and mismatch risk. |
| Sex | **NOT REQUIRED** | Not needed for printed/chip comparison or access control. |
| Issuing state | **NOT REQUIRED** | Issuer trust derives from signed evidence/trust policy, not a DG1 display field; current comparison does not use it. |

The adapter must discard raw DG1 immediately after extracting these four fixed-format values. DG1 is not persisted, displayed, exposed to UI/accessibility/analytics, placed in reducer state/events/errors, or returned through the engine contract. Comparison emits only `MATCH`, `MISMATCH`, or `INCONCLUSIVE`-equivalent safe evidence.

## 8. DG2 and portrait boundary

```text
JMRTD internal DG2 object
        ↓ bounded, format-checked extraction
encoded portrait in Atlas session artifact store
        ↓
PortraitArtifactRef
        ↓ future, separate Milestone 8 boundary
FaceMatchEngine
```

Only an opaque, session/owner/kind-scoped `PortraitArtifactRef` leaves NFC processing. The store validates session identity and artifact kind on resolution, enforces the encoded/decoded/aggregate limits, and clears bytes on cancellation/terminal cleanup.

Milestone 8 must consume `PortraitArtifactRef`. It must not depend on JMRTD/Scuba/Bouncy Castle/EJBCA, parse DG2/LDS, access `Tag`/`IsoDep`, perform BAC/PACE/secure messaging, receive raw APDUs, or reopen the chip. M7.2 does not begin Milestone 8.

## 9. Passive Authentication evidence model

PA is structured evidence, never a single `AUTHENTIC`/`NOT_AUTHENTIC` boolean.

```kotlin
internal data class PassiveAuthenticationEvidence(
    val dataGroupHashes: Map<RequestedPassportFile, DataGroupHashStatus>,
    val sodSignature: SodSignatureStatus,
    val dscExtraction: DscExtractionStatus,
    val signerTrust: SignerTrustStatus,
    val trustSnapshot: SafeTrustSnapshotMetadata?,
)

internal enum class DataGroupHashStatus { VALID, FAILED, NOT_CHECKED }
internal enum class SodSignatureStatus { VALID, FAILED, NOT_CHECKED, UNSUPPORTED_ALGORITHM }
internal enum class DscExtractionStatus { AVAILABLE, MISSING, MALFORMED, NOT_CHECKED }
internal enum class SignerTrustStatus {
    TRUSTED,
    UNTRUSTED,
    UNKNOWN,
    TRUST_STORE_STALE,
    ISSUER_NOT_COVERED,
}
```

The layers mean:

1. DG hash validation: each read DG digest versus the hash in the SOD.
2. SOD signature validation: cryptographic signature correctness, independent of issuer trust.
3. DSC extraction: whether a usable document signer certificate is present and parseable.
4. Signer trust: DSC chain/policy validation under the exact Atlas trust snapshot.
5. Trust freshness: whether the bundled snapshot and revocation inputs meet approved age policy.
6. Coverage: whether the issuer/country/document era is represented.

A hash/signature can be valid while signer trust is unknown or stale. Missing, unsupported, stale, or uncovered trust never becomes `TRUSTED`; cryptographic failure is not automatically proof of a counterfeit document. The reducer/policy remains authoritative over these observations. See [offline CSCA trust strategy](../security/offline-csca-trust-strategy.md).

## 10. Optional Chip Authentication plan

CA is not required for the first M7.3 implementation and remains disabled unless separately approved.

Prerequisites are: successful protected chip access; bounded DG14/security-info parsing; DG14 hash validated against SOD; SOD signature validated; DSC trusted under a fresh/covered Atlas trust snapshot; a strictly allowlisted CA suite/key/parameters; and an active cancellable transport. PACE-CAM evidence requires equivalent binding to trusted signed document data.

Safe observations should include `NOT_REQUESTED`, `PREREQUISITE_MISSING`, `UNSUPPORTED`, `SUCCEEDED`, `AUTHENTICATION_FAILED`, `SECURE_MESSAGING_FAILED`, `TRANSPORT_LOST`, `TIMED_OUT`, and `TECHNICAL_FAILURE`.

Successful CA may establish that the live chip possesses the private key corresponding to the trusted, signed DG14 public key and that fresh session keys/channel state were established. It may contribute clone-resistance evidence for that session. It does not by itself establish issuer trust, complete passport authenticity, document validity, holder identity, facial match, liveness, or absence of every cloning/relay attack. BAC/PACE or successful DG reads alone establish none of those claims.

## 11. Architecture acceptance criteria

M7.3 may implement this proposal only if reviewers accept the ownership split, finite contracts, downgrade table, logging/exception rules, artifact limits/lifetimes, DG minimization, PA separation, offline trust boundary, and optional-CA prerequisites. Any need to leak a prohibited type/value, add custom passport cryptography, let the protocol layer choose final outcome/retry, add runtime networking, or couple M8 to JMRTD is a STOP condition.

**No production interface or protocol implementation was created by M7.2.**
