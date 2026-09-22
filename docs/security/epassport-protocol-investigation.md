# ePassport protocol investigation (Milestone 7.1)

Evidence snapshot: 2026-09-01. This is an engineering and security assessment, not legal advice. Later requester authorizations permitted the ADR 0016 protected-access/DG1 engineering subset on 2026-09-21 and the bounded ADR 0017 Netherlands residence-permit PA/CA subset on 2026-09-22; they did not waive legal/open-source, trust/revocation, or final security/release/device gates. Statements below about the current repository/runtime describe the historical M7.1 snapshot.

## 1. Executive summary

The recommendation is **PROCEED ONLY AFTER SPECIFIC APPROVALS**, with `org.jmrtd:jmrtd:0.8.8` as the preferred protocol implementation. The candidate is **ACCEPT WITH LIMITATIONS**, not approved for immediate addition. Project Atlas must keep its existing transport-only `PROTOCOL_UNSUPPORTED` behavior until every condition in section 26 has written approval and a separately authorized implementation milestone begins.

JMRTD 0.8.8 is the strongest available fit because it is current, actively maintained, source-auditable, Android-capable, offline, and substantially complete for BAC, PACE, secure messaging, LDS parsing, DG1/DG2, and the cryptographic primitives needed for Passive Authentication. The exact published source matches SourceForge commit `277cd36445d63e037498dae2463cb44bd155f897`. It also has material limitations: LGPL distribution risk, a large Bouncy Castle dependency, privacy-sensitive object representations, an Active Authentication exception path that logs a command APDU, incomplete high-level CSCA trust management, and unverified Atlas build/device interoperability.

The earlier BAC-key logging concern is **HISTORICAL / FIXED** in the exact candidate. It was removed in 2015 by commit `114f8ba69900a38af721193d7c80aae0edd46dfa`; the audited 0.8.8 source does not contain that logger. That finding does not make current logging safe by itself. JMRTD types can still render MRZ access material, MRZ text, session keys, protocol parameters, and secure-messaging state through `toString()`, APDU listener APIs remain available, and one Active Authentication transmission-error path logs a command APDU at `INFO`. Atlas would need enforced logger suppression, no listeners, no raw object/exception interpolation, and regression tests before processing real data.

No candidate is approved unconditionally. Applied Recognition's reader and Noveo's fused SDK have unsafe implicit PACE-to-BAC downgrade defaults. `gmrtd` v1.1.5 retains APDU traces and contains exceptionally sensitive debug logging plus permissive signature fallback. OpenPACE is a strong EAC component but not a complete passport reader and is GPLv3 unless commercially licensed. Less mature forks and demo readers do not meet provenance, auditability, or production-readiness requirements.

## 2. Existing M7 blocker

Milestone 7 is complete only through the safe Android NFC transport boundary:

```text
CameraX -> bundled ML Kit OCR -> MRZ extraction/validation
        -> NfcAdapter -> Tag -> IsoDep -> PROTOCOL_UNSUPPORTED
```

The accepted baseline owns Android reader-mode lifecycle, operation tokens, cancellation, `Tag`/`IsoDep` containment, connection/close handling, clearable opaque artifacts, safe error translation, and reducer-facing observations. `AndroidPassportChipSession` connects to `IsoDep`, deliberately does not open the MRZ-derived access key, sends no APDU, and returns `PROTOCOL_UNSUPPORTED`.

The repository contains no BAC, PACE, secure-messaging, passport APDU, ASN.1 signature-validation, certificate-chain-validation, or Chip Authentication implementation. The NFC module has no passport or Bouncy Castle dependency. Milestone 8 has not started. The blocker is therefore real and safely contained: Atlas needs an approved protocol implementation, provider/dependency decision, legal approval, privacy controls, trust-store design, and Android validation before it can read DG1/DG2.

## 3. Research methodology

The review used the following process:

1. Confirmed the required repository root and read `AGENTS.md`, the README, implementation plan, NFC architecture, ADRs 0014–0015, NFC contracts, Android transport, manifest, Gradle configuration, and relevant tests.
2. Confirmed that Git metadata is absent; baseline/change auditing therefore used direct file inventory and content inspection rather than `git status` or `git diff`.
3. Retrieved exact Maven POMs, binaries, source archives, Gradle metadata, Android AAR manifests, and checksums where published.
4. Matched released source to exact tags/commits, preferring publisher repositories and Maven Central over aggregators.
5. Inspected source and, where source was unavailable, bytecode for logging, APDU traces, access-key handling, downgrade behavior, networking, persistence, randomness, providers, passive-authentication behavior, and Android assumptions.
6. Reconstructed significant dependency trees and licenses and inspected Android manifests for permissions/components.
7. Compared primary ICAO material for Passive Authentication, Master Lists, trust anchors, update cadence, and offline operation.
8. Applied Atlas's fail-closed, offline-only, no-custom-crypto, no-sensitive-logging, lifecycle-ownership, and policy-separation requirements.

This was not a legal opinion, penetration test, physical-passport interoperability test, complete CVE database attestation, reproducible Android release build, or production implementation.

## 4. Candidate inventory

| Candidate | Exact version/evidence | Serious review status |
| --- | --- | --- |
| JMRTD | `org.jmrtd:jmrtd:0.8.8`; [SourceForge commit `277cd36445d63e037498dae2463cb44bd155f897`](https://sourceforge.net/p/jmrtd/jmrtd/ci/277cd36445d63e037498dae2463cb44bd155f897/) | Preferred; accept with limitations |
| Applied Recognition Passport Reader | `com.appliedrec:mrtd-reader:3.0.4`; [GitHub commit `04f51b3f0620ea875dc33057a2291aef1ccff971`](https://github.com/AppliedRecognition/Passport-Reader-Android/commit/04f51b3f0620ea875dc33057a2291aef1ccff971) | Reject |
| gmrtd | GitHub release `v1.1.5`; [commit `3d60e3eff9445b03590800d03693f86d12b6063d`](https://github.com/gmrtd/gmrtd/commit/3d60e3eff9445b03590800d03693f86d12b6063d) | Reject current version |
| OpenPACE | Release `1.1.4`; [commit `7bc871ab0063c6f1e2443a9b7168262bbe8fa8be`](https://github.com/frankmorgner/openpace/commit/7bc871ab0063c6f1e2443a9b7168262bbe8fa8be) | Reject as the Atlas reader dependency |
| Noveo NFC SDK fused | [`io.github.noveogroup:nfc-sdk-android-fused:1.0.8`](https://central.sonatype.com/artifact/io.github.noveogroup/nfc-sdk-android-fused/1.0.8) | Reject |
| passauf | `v0.2.0`; [commit `48b7cc7d7398bbdfdcfef2c900a01972275c6c8a`](https://github.com/aveao/passauf/commit/48b7cc7d7398bbdfdcfef2c900a01972275c6c8a) | Insufficient maturity; reject for production |
| juncaffe/android-emrtd-reader and tutorial/demo forks | No reproducible, current production artifact established | Insufficient evidence |
| Commercial/cloud wrappers considered at screening level | Public source/provenance or fully offline evidence unavailable; some require remote services | Insufficient evidence or reject for offline use |

## 5. Candidate comparison matrix

`Partial PA` means useful SOD/DG/signature primitives without a governed end-to-end CSCA trust solution. `CA partial` means protocol primitives exist but Atlas integration and evidence semantics remain unvalidated.

| Candidate/version | Maintenance | License | BAC | PACE | DG1 | DG2 | Passive Authentication | Chip Authentication | Android | Offline | Decision |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| JMRTD 0.8.8 | Active; released 2026-08-27 | LGPL-2.1-or-later source grant; published metadata is inconsistent | Yes | Yes: GM/IM/CAM, DH/ECDH, 3DES/AES | Yes | Yes | Partial library support; Atlas trust store absent | **PARTIAL** | Java 8 bytecode and Android support; Atlas build/device validation pending | Yes | **ACCEPT WITH LIMITATIONS** |
| AppliedRec 3.0.4 | Maintained; 2025 release | Apache-2.0 wrapper plus LGPL transitives | Yes | Yes, but silent failure-to-BAC downgrade | Yes | Yes | Incomplete verification/trust semantics | **UNSUPPORTED** | minSdk 26 but compileSdk 36/Compose-heavy wrapper | Mostly; bundled trust data provenance unclear | **REJECT** |
| gmrtd 1.1.5 | Very active but young | MIT | Yes | GM/CAM; no evidence of IM | Yes | Yes | Broad implementation with bundled pools; validation has permissive paths | **SUPPORTED** | Native Go AAR; ABI/toolchain/supply-chain cost | Yes | **REJECT** |
| OpenPACE 1.1.4 | Active | GPLv3 or separate proprietary license | No | Strong GM/IM, DH/ECDH, 3DES/AES | No | No | No complete LDS/PA reader | **SUPPORTED** protocol component | C/OpenSSL/JNI integration required | Core can be offline | **REJECT** |
| Noveo fused 1.0.8 | Recent binary release | Apache-2.0 wrapper plus LGPL transitives | Yes | Yes, unsafe default fallback | Likely | Likely | Unsupported/unproven | **UNKNOWN** | minSdk 26; Kotlin/Gradle version mismatch risk | Yes | **REJECT** |

## 6. Detailed candidate analysis

### JMRTD 0.8.8

JMRTD is the only serious candidate without a present hard blocker that necessarily prevents a controlled future integration. It is mature, current, local-only, protocol-complete for Atlas's first read path, and can sit behind an Atlas-owned adapter. Its exact source can be audited, and it delegates cryptography to reviewed JCA/Bouncy Castle implementations rather than requiring Atlas to write protocol crypto.

Limitations are significant. The LGPL distribution model needs legal/product approval; the runtime adds Bouncy Castle, Scuba smart cards, and EJBCA CVC; the core lacks Atlas's governed CSCA store and current high-level trust orchestration; sensitive `toString()` methods and an APDU logging path require containment; and source compatibility is not proof of minSdk 26, R8, vendor-NFC, PACE, or physical-document interoperability. JMRTD is therefore a candidate for a later authorized adapter, not a dependency approved by this report alone.

### Applied Recognition Passport Reader 3.0.4

This Android wrapper is convenient but makes choices Atlas must not inherit. It depends on JMRTD 0.8.2 rather than the current 0.8.8, mixes current and legacy Bouncy/Spongy Castle dependencies, pulls UI/Compose/activity concerns into the NFC boundary, and requires compileSdk 36 while Atlas is pinned to 35. Its PACE path catches broad failures and silently falls back to BAC, so an authentication/integrity failure can be converted into a weaker protocol attempt rather than failing closed.

Its passive-authentication path verifies only part of the required evidence: it does not establish a governed CSCA validation policy or clearly compare every requested DG hash. The wrapper exposes/retains identity and portrait results through parcelable/serializable and global structures and prints stack traces. Its bundled Master List provenance and update policy are undocumented. The wrapper's Apache-2.0 license does not remove the LGPL obligations of JMRTD/Scuba/CVC transitives. Decision: **REJECT**.

### gmrtd 1.1.5

`gmrtd` is an unusually complete, actively developed Go implementation with BAC, PACE, LDS, PA, CA, and a clean caller-supplied transceiver abstraction. It is MIT licensed, offline, and defaults to failing a PACE error unless the caller explicitly enables BAC fallback. Those are strong qualities.

The current release nevertheless violates Atlas's privacy and fail-closed requirements. It always retains an in-memory APDU trace for every command, exposes that trace to the Android wrapper, and has debug logs for MRZ/passwords, private and shared key material, session keys, secure-messaging state, APDUs, and certificates. Debug logging is not enabled by default, but global logger configuration can expose it. A signature-verification path tries alternative elliptic curves for out-of-range signature values and accepts a result if an alternative verifies; missing or malformed SOD signing time can skip validity checks. Those compatibility fallbacks are inappropriate for Atlas verification evidence without a tightly reviewed strict mode.

The Android 16-KiB-page release AAR is about 10.1 MB compressed and contains roughly 19.2 MB of native libraries before application packaging, with arm64 and x86_64 ABIs. A native Go toolchain and release archive, rather than a standard Maven source/artifact chain, increase reproducibility, ABI, memory, and supply-chain work. Decision: **REJECT current version**.

### OpenPACE 1.1.4

OpenPACE is a credible, mature C/OpenSSL implementation of PACE and EAC with GM/IM, DH/ECDH, AES/3DES, Brainpool, TA, and CA support. It is a protocol component rather than a complete LDS reader: it does not supply BAC, DG1/DG2 reading, SOD processing, or a complete passive-authentication trust path. Android adoption would require native/OpenSSL/JNI packaging and new lifecycle/memory/supply-chain analysis.

The standard license is GPLv3, a strong-copyleft risk for an Android app and distributable SDK; a separate proprietary license is offered but would still not make the component complete. Decision: **REJECT as Atlas's reader dependency**. It may be a reference implementation for protocol test vectors only after license review, not code to copy.

### Noveo NFC SDK Android fused 1.0.8

This artifact targets minSdk 26 and wraps JMRTD 0.8.3. Its published source URL was unavailable, its POM SCM points to a placeholder, and the sources archive is effectively empty. Bytecode inspection found a default `allowBacFallback=true`; automatic authentication catches PACE failures and then attempts BAC. The default logging configuration is enabled at `DEBUG`, with APDU logging enabled and a safe-redaction mode whose coverage cannot be verified without source.

It also brings Kotlin 2.3.0/Gradle 9.4 metadata into an Atlas Kotlin 2.2.10 baseline and uses older JMRTD/Bouncy Castle/Scuba versions. Passive and Chip Authentication capability could not be established. Decision: **REJECT** due unsafe defaults; incomplete provenance is an additional blocker.

### Screened-out candidates

`passauf` v0.2.0 is MIT and promising but its own documentation describes incomplete certificate-chain validation, detailed PII debug logging, and a young Android implementation not intended for validation. `juncaffe/android-emrtd-reader` and similar forks lack a current reproducible release and independent production evidence. Tutorial readers commonly copy old JMRTD examples, implement protocol glue/crypto themselves, log exceptions, or omit trust validation. Cloud/commercial connectors whose public evidence requires remote validation, hides protocol source, or does not establish an offline SDK are incompatible with the present requirement. These candidates are not substitutes for a maintained, auditable protocol dependency.

## 7. Exact JMRTD re-review

### Identity and provenance

| Item | Finding |
| --- | --- |
| Current stable version at snapshot | `0.8.8`; Maven metadata lists it as latest/release |
| Coordinates | `org.jmrtd:jmrtd:0.8.8` |
| Publication | Maven Central, 2026-08-27 |
| Publisher source | `https://git.code.sf.net/p/jmrtd/jmrtd` |
| Matching commit | `277cd36445d63e037498dae2463cb44bd155f897`, commit message `Version 0.8.8.`, dated 2026-08-26 |
| Binary JAR SHA-256 | `3f5af114afd3a9dde4b4205e157fd696208d56a989b8e8e835de1e95b67bf6b5` |
| Source JAR SHA-256 | `48134c4252515831fc509006f280a5e32ecd30873d5ca3c2b2bf6cc45940ac22` |
| POM SHA-256 | `3d7f815f00db8b19acc40d5afb29bfcb9be22c40daebdc85c2f583e5987d2d57` |
| Provenance confidence | **HIGH**: every `org/**` file in the source JAR byte-matched the release commit's JMRTD source and the POM matched; a detached signature is published, but its signer/key chain was not independently verified |

The repository currently has no release tags, and published POM SCM metadata still references older SVN infrastructure. Those weaknesses should be recorded, but exact content matching gives stronger evidence than the stale metadata alone. Relevant primary locations are the [Maven Central 0.8.8 directory](https://repo1.maven.org/maven2/org/jmrtd/jmrtd/0.8.8/), [JMRTD SourceForge repository](https://sourceforge.net/p/jmrtd/jmrtd/ci/master/tree/), and [JMRTD project page](https://sourceforge.net/projects/jmrtd/).

### Maintenance and license

Release activity is current: 0.8.0 appeared in February 2025, followed by regular 0.8.x releases through 0.8.8 in August 2026. Recent commits include PACE-CAM and provider-related work. This supports an **active** maintenance classification, not a guarantee of response time or long-term support.

Source headers grant LGPL version 2.1 or later. The published POM says GNU Library/Lesser GPL, while the repository's root license file contains LGPLv3 text. The operative engineering classification is weak copyleft with metadata ambiguity; legal review must determine notices, corresponding source, modification disclosure, relinking/replacement, signing, SDK redistribution, and store-distribution obligations.

### Capability summary

- BAC: supported through `PassportService`/BAC protocol classes.
- PACE: supported for GM, IM, and CAM with DH/ECDH, 3DES/AES suites, SHA variants, standardized/named parameter sets, and Brainpool curves.
- EF.CardAccess: parsed by `CardAccessFile`/`PACEInfo`; the caller chooses the advertised compatible suite and invokes PACE.
- DG1/DG2: LDS file classes and streaming read APIs are present.
- Passive Authentication: SOD parsing, DG hash material, Document Signer extraction, and SOD signature primitives exist. Deprecated high-level verification/trust management does not supply Atlas's required governed CSCA path and policy.
- Chip Authentication: EAC-CA protocol/result and DG14 primitives exist. This is **PARTIAL** for Atlas until suites, evidence mapping, device behavior, and key/session transitions are validated.
- Android: Java 8 bytecode; upstream describes Android use. A separate Scuba Android transport or an Atlas-owned bridge is needed.
- Offline/network: core processing has no HTTP client, telemetry, remote certificate fetch, or runtime network requirement.
- Permissions: the core JAR has no Android manifest. The reviewed Scuba Android adapter contributes only NFC permission and no service/provider/network permission; Atlas already declares NFC.

## 8. Security source audit

The following classifications describe the exact reviewed releases, not hypothetical downstream wrapper behavior.

| Finding | Candidate | Classification | Consequence |
| --- | --- | --- | --- |
| BAC failure logged a `BACKey` containing document number, birth date, and expiry | JMRTD historical source | **HISTORICAL / FIXED** | Removed in 2015; absent from 0.8.8 |
| Active Authentication transmission exception logs command APDU hex at `INFO` | JMRTD 0.8.8 | **CURRENT PRODUCTION-REACHABLE** if AA is invoked and transmission fails | AA is outside proposed first scope, but namespace suppression is still mandatory |
| APDU listener callbacks | JMRTD/Scuba | **DISABLED BY DEFAULT** | Atlas must never register a listener |
| Access keys, MRZ, results, and secure-messaging values exposed by `toString()` | JMRTD 0.8.8 | **CURRENT PRODUCTION-REACHABLE** if callers/loggers interpolate them | Ban object/raw-exception logging and test captured logs |
| Unknown PACE access-key type uses a best-effort derivation branch | JMRTD 0.8.8 | **CURRENT PRODUCTION-REACHABLE** only if Atlas supplies an unrecognized type | Adapter must accept only explicit supported key types and fail otherwise |
| CA unknown algorithm/key-type branch can select 3DES | JMRTD 0.8.8 | **CURRENT PRODUCTION-REACHABLE** only if CA is used with ambiguous inputs | Keep CA disabled until strict suite validation exists |
| Always-retained in-memory APDU trace | gmrtd 1.1.5 | **CURRENT PRODUCTION-REACHABLE** | Hard privacy blocker |
| MRZ/password/private-key/session-key/APDU debug logging | gmrtd 1.1.5 | **CURRENT DEBUG-ONLY** but globally enableable | Hard blocker without upstream redaction/removal and isolation |
| Alternative-curve signature acceptance and missing-signing-time leniency | gmrtd 1.1.5 | **CURRENT PRODUCTION-REACHABLE** | Hard fail-closed verification blocker |
| Broad PACE exception converted to BAC attempt | AppliedRec 3.0.4 | **CURRENT PRODUCTION-REACHABLE** | Unsafe implicit downgrade |
| Stack-trace printing and global identity/portrait result retention | AppliedRec 3.0.4 | **CURRENT PRODUCTION-REACHABLE** | Privacy/lifecycle blocker |
| Debug/APDU logging enabled in default config; redaction implementation unavailable | Noveo 1.0.8 | **CURRENT PRODUCTION-REACHABLE** by default | Hard privacy/audit blocker |
| PACE failures converted to BAC when default option is used | Noveo 1.0.8 | **CURRENT PRODUCTION-REACHABLE** | Unsafe implicit downgrade |
| Filesystem trust lookup in default tooling | OpenPACE 1.1.4 | **CURRENT PRODUCTION-REACHABLE** for those APIs | Not an Atlas in-memory LDS/trust solution |

JMRTD core contained no `System.out`, `System.err`, `println`, HTTP client, telemetry, or filesystem persistence path in the audited package. It uses `SecureRandom`, not a deterministic or time-seeded protocol RNG. Absence of those findings is not a substitute for a later adapter-level threat model and runtime capture test.

## 9. Logging/privacy analysis

The historical BAC-key issue is closed narrowly: exact JMRTD 0.8.8 does not contain it. SourceForge commit [`114f8ba69900a38af721193d7c80aae0edd46dfa`](https://sourceforge.net/p/jmrtd/code/ci/114f8ba69900a38af721193d7c80aae0edd46dfa/) changed the failure message from an interpolated BAC key to `BAC failed` in September 2015.

Current privacy risk remains because:

- `BACKey.toString()` contains document number, date of birth, and expiry.
- `PACEKeySpec.toString()` includes access-key bytes.
- `MRZInfo.toString()` emits full MRZ content; DG1 representations can include it.
- BAC/PACE result and secure-messaging wrapper representations include key/protocol/session state.
- APDU listeners can observe command/response traffic, including sensitive LDS data depending on placement.
- exceptions may carry nested objects or APDU status/context even when the top-level library log is generic.

Required containment is defense in depth: configure `java.util.logging` suppression for `org.jmrtd` and `net.sf.scuba` before the first library call; verify any Android JUL-to-logcat bridge; never add APDU listeners; prohibit `toString()`/raw exception logging; translate all failures to finite safe Atlas codes; and run negative log-capture tests that exercise BAC/PACE failure, tag loss, secure-messaging MAC failure, malformed LDS, DG reads, PA failure, cancellation, and timeout. Security approval must review the exact suppression behavior in release builds. Logger suppression alone is not sufficient if sensitive values are interpolated by Atlas, retained in crash reports, or captured by an APM SDK.

## 10. License analysis

| Candidate | Classification | App-distribution risk | SDK-distribution risk | Approval |
| --- | --- | --- | --- | --- |
| JMRTD 0.8.8 | Weak copyleft, LGPL-2.1-or-later headers; metadata ambiguity | Notices/license and covered-source obligations; Android dex/AAB signing and replacement/relinking mechanics require counsel | Higher: downstream customers redistribute covered components and need compliant notices/source/replacement terms | Legal, product, and open-source approval required |
| AppliedRec 3.0.4 | Apache-2.0 wrapper plus LGPL transitives | Apache notices do not replace transitive LGPL obligations | Same, compounded by wrapper/UI distribution | Required; rejected on security grounds regardless |
| gmrtd 1.1.5 | Permissive MIT | Notice preservation, subject to third-party Go dependency notices | Generally suitable, but native artifact terms/SBOM still required | Standard legal/OSS review; rejected on security grounds |
| OpenPACE 1.1.4 | Strong copyleft GPLv3 or separate commercial license | GPLv3 can require source/installation-information analysis; incompatible with an unapproved proprietary distribution assumption | Especially high for a reusable SDK; proprietary terms would need negotiation | Explicit legal/product/commercial approval; rejected as incomplete |
| Noveo 1.0.8 | Apache-2.0 wrapper plus LGPL transitives | Transitive LGPL remains; source evidence is deficient | Same plus customer provenance obligations | Required; rejected on security/provenance grounds |

For LGPL specifically, packaging a Java library into dex and an Android APK/AAB does not erase the recipient's LGPL rights. Engineering should avoid modifying covered libraries, isolate them behind a replaceable boundary, ship required notices and license text, preserve/source the exact covered version as counsel directs, and not prohibit lawful reverse engineering for debugging modifications. Android application signing, Play/App Store delivery, dex merging, R8, and practical replacement/relinking can make compliance non-obvious. A distributable Atlas SDK increases the issue because customers, not only Atlas, become redistributors; documentation, contracts, notices, source availability, and replacement instructions may need to flow downstream. Only counsel can decide whether the proposed packaging satisfies the relevant LGPL version and distribution channels.

## 11. Crypto provider analysis

JMRTD 0.8.8 directly declares:

- `org.bouncycastle:bcprov-jdk18on:1.85.2`
- `org.bouncycastle:bcutil-jdk18on:1.85`

JMRTD instantiates a private `BouncyCastleProvider` and passes the provider object to JCA operations; it does not need Atlas to globally register or reorder providers. Android's platform implementation uses relocated `com.android.org.bouncycastle` packages, while the Maven provider uses `org.bouncycastle`, so normal class-package collision is not expected. The provider name could still conflict if application code globally registers it; Atlas must not do that.

No shading/relocation is presently recommended. Shading a signed provider can break signature/self-integrity assumptions and creates a bespoke artifact that is harder to audit and update. Provider construction and use should remain internal to the protocol layer. Atlas must verify all required algorithms on minSdk 26 and current target devices, especially Brainpool/EC agreement, cipher transformations, MACs, certificate parsing, and provider-selection fallbacks.

Bouncy Castle 1.85.2 was current at the evidence snapshot. The 1.85 line included fixes for published issues affecting earlier versions, and 1.85.2 corrected an AES-256 OID/password-key derivation defect in 1.85; the planned passport path generally uses explicit session keys, but exact 1.85.2 is still preferable. No authoritative advisory was identified that established a vulnerability in exact 1.85.2 during this review. That is a time-bounded search result, not proof of absence; security monitoring must continue using [Bouncy Castle's official Java release page](https://www.bouncycastle.org/download/bouncy-castle-java/) and authoritative advisory sources.

Raw candidate artifacts total approximately 11.15 MiB before dexing/shrinking, dominated by `bcprov` (about 9.8 MiB). Final APK/AAB and per-ABI impact was not established. Atlas must measure an R8 release build and validate that shrinking does not remove reflection/provider/LDS paths. The declared `bcutil` 1.85 depends on `bcprov` 1.85, while JMRTD directly selects `bcprov` 1.85.2; Gradle will normally mediate upward, but the mixed patch level must be locked and tested or aligned through an approved upstream/update decision.

## 12. BAC analysis

JMRTD, AppliedRec, gmrtd, and Noveo support BAC. OpenPACE does not. BAC derives access/session material from the MRZ access key and establishes secure messaging, but it is the older, weaker access-control path and is not document authenticity verification.

Atlas's required policy is strict:

1. Parse EF.CardAccess before selecting access control.
2. If a compatible advertised PACE suite is present, attempt that exact reviewed suite.
3. If PACE fails for authentication, integrity, malformed data, unsupported parameters, transport ambiguity, or implementation error, fail closed. Do not try BAC.
4. Permit BAC only when no compatible PACE mechanism is advertised and policy explicitly allows BAC for that document profile.
5. Never expose or log the MRZ-derived key, derived keys, secure-messaging state, or APDUs.

JMRTD leaves selection to the caller, so an Atlas adapter can enforce this rule. AppliedRec and Noveo violate it by default. gmrtd has an explicit opt-in fallback option and defaults more safely, but its other hard blockers remain. BAC success provides chip-access evidence only; it must never be labelled authenticity or clone resistance.

## 13. PACE analysis

| Candidate | Variants | Key agreement/ciphers | Brainpool | EF.CardAccess/selection | Downgrade finding |
| --- | --- | --- | --- | --- | --- |
| JMRTD 0.8.8 | GM, IM, CAM | DH/ECDH; 3DES and AES 128/192/256 suites | Yes, named/standardized parameters | Parses PACEInfo; caller selects/calls | Caller-controlled; Atlas can fail closed |
| AppliedRec 3.0.4 | Inherited JMRTD support but wrapper chooses first entry | Inherited | Inherited | Wrapper reads card security/access data and chooses narrowly | **Unsafe**: broad PACE failure silently falls back to BAC |
| gmrtd 1.1.5 | GM and CAM established; IM not established in review | ECDH-oriented implementation, AES/3DES suites | Yes | Caller options; explicit fallback switch | Default fail-closed, but other validation/privacy blockers |
| OpenPACE 1.1.4 | GM and IM; strong EAC breadth | DH/ECDH; 3DES/AES | Yes | Protocol component APIs | No BAC fallback because BAC is absent |
| Noveo 1.0.8 | Inherited from JMRTD 0.8.3 | Inherited | Inherited | Automatic mode | **Unsafe**: default allows PACE failure to BAC |

PACE-CAM may yield evidence related to chip authenticity only when its static key/certificate binding and downstream validation are correctly handled; a successful PACE channel by itself is not a Passive Authentication or general authenticity result. Atlas must keep suite negotiation, downgrade rules, and evidence semantics out of the generic NFC feature layer and in an approved protocol-policy adapter boundary.

## 14. DG1/DG2 analysis

JMRTD supplies LDS parsing and access for DG1 and DG2. DG1 contains biographic/MRZ data; DG2 contains facial biometric records and potentially large images with multiple encodings. AppliedRec and gmrtd also read them; Noveo likely wraps those reads but lacks sufficient public source evidence; OpenPACE does not provide an LDS reader.

Atlas should not expose library LDS objects beyond the adapter. A future implementation must stream with reviewed maximum lengths, reject malformed/oversized BER/TLV and unsupported image forms, copy only the minimum required values into Atlas-owned memory, bound decompression/decoding, translate DG1 comparison to policy-neutral observations, and make DG2 portrait ownership/cleanup explicit. Reading DG1/DG2 must occur only after authenticated secure messaging where required. Neither successful reads nor MRZ equality authenticate the issuing state.

## 15. Passive Authentication analysis

Passive Authentication has two distinct layers that must never be collapsed:

**A. Document cryptographic consistency**

- Read EF.SOD.
- Extract its signed data and Document Signer Certificate (DSC).
- Verify the SOD signature.
- Read each claimed DG and compare its digest with the corresponding SOD hash.

**B. Issuer trust**

- Validate the DSC through the relevant CSCA trust anchor and rollover/link-certificates.
- Apply certificate constraints, algorithms, validity/reference-time policy, and country/issuer mapping.
- Apply the approved revocation/CRL policy and trust-store provenance/version rules.

JMRTD supports most of A as low-level primitives and provides certificate data needed by B, but its deprecated high-level trust helper is not an Atlas trust policy or current governed CSCA store. It is therefore **partial**. AppliedRec's wrapper performs incomplete checks and does not establish B. gmrtd is broader but has permissive signature/validity behavior that blocks acceptance. OpenPACE does not provide the LDS/SOD path. Noveo is unproven.

Future Atlas evidence must report the layers separately, for example: DG hashes match/mismatch/not read; SOD signature valid/invalid/unsupported; signer chain trusted/untrusted/unknown; revocation current/stale/unavailable. A missing CSCA, expired store, unsupported algorithm, or stale revocation material is `UNVERIFIED`/`INCONCLUSIVE`, never `VALID` and never automatically `FAKE`.

## 16. Offline CSCA/trust-store analysis

ICAO's Public Key Directory (PKD) distributes DSCs, certificate revocation lists, and CSCA Master Lists; it does not transfer each relying party's responsibility for trust policy. ICAO's Master List contains CSCA certificates submitted by participating states through diplomatic channels and is signed by ICAO's Master List Signer, whose certificate chains to the United Nations CSCA. ICAO states that recipients must establish their own trust policy and that Master Lists can contain non-conformant certificates. Coverage is not equivalent to every issuing state or every historical passport.

At the snapshot, the [ICAO Master List page](https://www.icao.int/icao-pkd/icao-master-list) described a list issued 2026-07-15 with 579 certificates and updates approximately every three months as appropriate. [ICAO's ePassport basics](https://www.icao.int/icao-pkd/epassport-basics) explains the DSC-to-CSCA relationship and the PKD's Master List/CRL role. [Doc 9303 Part 12](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p12_cons_en.pdf) requires trust anchors to be established out of band and covers CSCA rollover/link certificates.

A fully offline Atlas design would need:

- an out-of-band, independently approved trust anchor for authenticating the ICAO/UN Master List signing chain;
- signature verification of every ingested Master List before extraction;
- a versioned, read-only bundle of accepted CSCAs, current/retired rollover anchors, link certificates, metadata, and any separately approved national/bilateral additions;
- country/issuer coverage and known-gap metadata, certificate constraints, duplicate/conflict policy, reference-time rules, and rollback prevention;
- an offline revocation design with current CRLs and a defined response to stale/missing CRLs; a Master List alone does not provide revocation freshness;
- provenance, signer, issue date, ingest tooling review, four-eyes approval, checksums, SBOM/release records, expiry monitoring, incident response, and emergency update procedures;
- normal application releases at least as frequently as the accepted ICAO update cadence, plus an emergency release path. No runtime network permission is required.

Raw public Master Lists are on the order of low single-digit MiB or less, but actual Atlas size depends on historical/rollover anchors, national supplements, CRLs, metadata, and compression. A third-party example bundle measured about 1.3 MB for hundreds of certificates; it is not a trustworthy sizing or provenance substitute. Atlas must measure its approved store and release-update overhead. Missing countries, retired documents, expired certificates, or stale app versions must produce explicit inconclusive trust evidence.

## 17. Chip Authentication analysis

| Candidate | Classification | Rationale |
| --- | --- | --- |
| JMRTD 0.8.8 | **PARTIAL** | EAC-CA protocol/result and DG14 primitives exist; strict suite negotiation, PA binding, device interoperability, and Atlas evidence mapping remain unvalidated |
| AppliedRec 3.0.4 | **UNSUPPORTED** | Wrapper does not implement a reviewed CA flow |
| gmrtd 1.1.5 | **SUPPORTED** at library level | CA implementation exists, but the candidate is rejected for privacy/verification reasons |
| OpenPACE 1.1.4 | **SUPPORTED** at component level | Mature EAC-CA, but no complete reader/LDS/PA solution and unacceptable default licensing/integration fit |
| Noveo 1.0.8 | **UNKNOWN** | Public source/capability evidence is insufficient |

Clone-resistance evidence would require successful Passive Authentication first to trust the relevant DG14/static chip-authentication public key, strict algorithm/parameter validation, a fresh authenticated key agreement with the chip, secure-messaging transition validation, and clear failure semantics. PACE-CAM can contribute related evidence for documents that advertise it, but must be bound to trusted signed document data. CA is not required for initial candidate acceptance because coverage varies and BAC/PACE plus PA can still provide useful, accurately labelled evidence. Atlas must never claim “not cloned” merely because BAC/PACE or DG reads succeeded.

## 18. Android compatibility

Atlas currently uses minSdk 26, compileSdk/targetSdk 35, JDK 17, AGP 9.3.1, and Kotlin 2.2.10.

JMRTD 0.8.8 emits Java 8 bytecode and its core source did not reveal desktop UI, `java.awt`, Swing, `javax.smartcardio`, HTTP, JNI, or desktop-filesystem dependencies in the mobile path. The separate `scuba-sc-android:0.0.26` AAR is very small, uses direct `IsoDep.transceive`, and has an old minSdk/target metadata baseline rather than modern lifecycle design. It depends on `scuba-smartcards:0.0.20`, which Gradle would normally mediate to JMRTD's 0.0.21. Atlas should preferably retain ownership of its already reviewed `IsoDep` lifecycle and adapt it to the library's card-service abstraction; whether that uses the Scuba adapter internally or a minimal reviewed bridge is an implementation decision for a later approved milestone.

Source/API review cannot establish runtime compatibility. Required tests include a clean Gradle resolution/build, D8/R8 release build, lint, duplicate-class/provider audit, minSdk 26 execution, 16-KiB-page-compatible packaging where relevant, shrinker/reflection rules, lifecycle recreation, cancellation/tag loss/timeout, and representative authorized passports across BAC, multiple PACE suites/curves, DG2 encodings, malformed LDS, PA, and CA-if-enabled.

AppliedRec compiles against API 36 and brings Compose/activity/UI scope inconsistent with Atlas. gmrtd uses native Go libraries and requires ABI/page-size/toolchain validation. OpenPACE requires a new C/OpenSSL/JNI surface. Noveo's Gradle/Kotlin metadata is ahead of the accepted Atlas versions. None has been proven by a successful Atlas build or physical-device run in M7.1.

## 19. Transitive dependency/permission analysis

### Preferred candidate tree

| Artifact | Role/version | Approx. raw size | License/health observation |
| --- | --- | ---: | --- |
| `org.jmrtd:jmrtd:0.8.8` | Protocol/LDS | 505 KiB | LGPL; active |
| `net.sf.scuba:scuba-smartcards:0.0.21` | APDU/card abstraction | 84 KiB | LGPL; old-looking version but current declared JMRTD dependency; no runtime deps |
| `org.bouncycastle:bcprov-jdk18on:1.85.2` | Crypto provider | 9.8 MiB | Permissive BC license; current at snapshot |
| `org.bouncycastle:bcutil-jdk18on:1.85` | ASN.1/CMS utility support | 702 KiB | Permissive BC license; patch-level skew with bcprov requires locking/test |
| `org.ejbca.cvc:cert-cvc:1.4.13` | Card-verifiable certificates | 78 KiB | LGPL-2.1; old build metadata; legacy `bcprov-jdk15on` is explicitly excluded by JMRTD |
| optional `net.sf.scuba:scuba-sc-android:0.0.26` | Android `IsoDep` adapter | 4 KiB AAR | LGPL; old target metadata; depends on Scuba 0.0.20, to be mediated/locked |

No logging framework, HTTP client, telemetry SDK, XML framework, service, content provider, or network dependency appears in that reconstructed runtime tree. ASN.1/CMS work is supplied primarily through Bouncy Castle/JMRTD. The Scuba Android AAR manifest requests `android.permission.NFC`; it does not request `INTERNET`, `ACCESS_NETWORK_STATE`, `READ_EXTERNAL_STORAGE`, or `WRITE_EXTERNAL_STORAGE`, and declares no service/provider. JMRTD and its Java JAR transitives have no Android manifest. A resolved Gradle manifest/dependency report is still mandatory because metadata and resolution can change through mediation.

AppliedRec contributes an activity/Compose surface and a required-NFC declaration, plus old Spongy Castle alongside Bouncy Castle and a compile-time JUnit dependency. Noveo's AAR itself declares no permission/component, but its inaccessible source blocks a complete audit. gmrtd's AAR declares no permission but packages large native libraries. OpenPACE would add native OpenSSL/JNI artifacts whose exact Android manifest/package would be Atlas-owned and separately reviewed.

## 20. Offline behavior

JMRTD, gmrtd, OpenPACE core, Noveo, and AppliedRec protocol operations can run locally; JMRTD's exact core has no runtime network client. A JMRTD-based Atlas path can therefore preserve the current absence of `INTERNET` and `ACCESS_NETWORK_STATE`. It must not fetch certificates, models, keys, policy, or telemetry at runtime.

Offline protocol operation does not make offline trust automatic. A governed CSCA/CRL bundle must arrive through normal signed app/SDK releases, and the app must expose the trust-data version/age to safe policy logic. If the bundle is stale or lacks an issuer, verification becomes inconclusive. Candidates requiring cloud verification, remote identity APIs, runtime certificate fetches, mandatory telemetry, or remote APDU/protocol services are rejected. Periodic app-delivered trust-store updates are acceptable only under the governance in section 16.

## 21. Atlas architecture fit

JMRTD can fit the desired boundary if integration remains narrow:

```text
verification reducer/policy
        ^ safe, finite observations only
android:nfc Atlas session/artifact ownership
        -> PassportProtocolEngine (Atlas abstraction)
        -> JMRTD 0.8.8 + reviewed Scuba/provider dependencies
        -> Atlas-owned IsoDep lifecycle/transport
```

Atlas must continue to own Activity/reader-mode lifecycle, operation-token correlation, single-session arbitration, cancellation, deadlines, `IsoDep` closure, artifact lifetimes, cleanup, safe failures, feature-domain observations, and reducer/product policy. The library should own BAC/PACE mechanics, secure messaging, eMRTD APDU sequencing, LDS parsing, and cryptographic operations. No JMRTD `PassportService`, LDS, key, certificate, APDU, wrapper, or exception object should cross into verification/UI contracts.

The adapter must be cancellable and close-aware, enforce byte/record limits, own PACE/BAC downgrade selection, isolate provider use, and map PA/CA sub-results without deciding the terminal product outcome. It must not duplicate protocol crypto, ASN.1 verification, APDU construction, or certificate-path logic in Atlas. Trust-store selection/validation policy needs a separately reviewed component and must not leak into a low-level `IsoDep` wrapper.

AppliedRec forces UI/activity and policy choices into the boundary. gmrtd's transceiver shape fits, but logging/trace/verification choices do not. OpenPACE is too low-level and would force Atlas to assemble missing reader/PA behavior. Noveo hides behavior and defaults under an unauditable wrapper. JMRTD is the best architectural fit despite requiring a small Atlas-facing adapter later.

## 22. Candidate scoring

Scores are 1 (poor) to 5 (strong). For implementation complexity, 5 means easiest/lowest integration burden. A hard blocker overrides totals.

| Candidate | Maint. | Protocol | Android | Audit | Privacy | License | Offline | Deps | Atlas fit | Complexity | Hard blocker |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| JMRTD 0.8.8 | 5 | 5 | 3 | 4 | 3 | 2 | 5 | 3 | 4 | 3 | No immediate technical blocker; approvals/validation still mandatory |
| AppliedRec 3.0.4 | 3 | 3 | 3 | 3 | 1 | 2 | 4 | 1 | 1 | 2 | Yes: implicit downgrade, retention/logging, incomplete PA |
| gmrtd 1.1.5 | 3 | 5 | 3 | 4 | 1 | 5 | 5 | 3 | 4 | 1 | Yes: APDU retention, key logs, permissive signature verification |
| OpenPACE 1.1.4 | 4 | 2 | 2 | 5 | 3 | 1 | 5 | 3 | 2 | 1 | Yes: incomplete reader and GPL/native integration |
| Noveo 1.0.8 | 4 | 2 | 3 | 1 | 1 | 2 | 5 | 2 | 3 | 3 | Yes: source provenance, default logging, implicit downgrade |

## 23. Supply-chain requirements

Any later approval of JMRTD must require, before merge:

- exact pins for JMRTD and every transitive; no `+`, ranges, snapshots, or repository substitutions;
- Gradle dependency locking and dependency-verification SHA-256 entries;
- recorded Maven URL, publication metadata, release commit, source/JAR/POM checksums, and source-to-release reproducibility result;
- independent verification of the published detached signature/key identity or a documented decision not to rely on it;
- complete resolved dependency graph, AAR/JAR manifests, duplicate classes, native libraries, consumer rules, services/providers, and permission diff;
- SBOM and third-party notices with exact license texts, corresponding-source handling, and modification inventory;
- approved artifact repositories only and CI denial of unexpected transitives/versions;
- archived reviewed source and a documented patch/update policy; avoid vendoring or modifying covered code unless separately approved;
- automated SCA plus periodic human security review of release notes, commits, provider advisories, logging, downgrade, and PA/CA behavior;
- clean release-build/R8 provenance and artifact size checks; representative-device/interoperability evidence tied to the exact lockfile;
- a trust-store SBOM/provenance/version/update record separate from code dependencies.

## 24. Organizational approvals required

1. **Security/cryptography:** exact source audit, downgrade policy, logging containment, provider behavior, PA/CA scope, threat model, and test plan.
2. **Legal/open-source/product:** LGPL version/metadata ambiguity, APK/AAB packaging, notices/source/relinking/replacement obligations, app-store terms, and downstream Atlas SDK redistribution.
3. **Privacy/data protection:** transient MRZ/identity/portrait/key/APDU processing, crash/logging/diagnostic exclusions, memory ownership, retention, deletion, and DPIA/records as applicable.
4. **Architecture:** `PassportProtocolEngine` boundary, Atlas lifecycle/cancellation/session ownership, error/evidence mapping, and no verification policy in NFC/protocol code.
5. **Android/platform/release engineering:** toolchain, minSdk, R8, provider, size, performance, device/vendor, permission, and supply-chain validation.
6. **PKI/trust governance:** ICAO/UN and national anchor provenance, Master List/CRL ingestion, coverage, validity, update/emergency/rollback policy, and operational owner.
7. **Product/risk/compliance:** accurate claims and UI semantics for chip access, DG consistency, issuer trust, revocation, PA, CA, and inconclusive cases.

## 25. Recommended strategy

**Overall recommendation: PROCEED ONLY AFTER SPECIFIC APPROVALS.**

Select **JMRTD 0.8.8** for a future, separately authorized proof/integration phase, classified **ACCEPT WITH LIMITATIONS**. Do not add it during M7.1. Keep the current transport-only fallback as the shipping/runtime behavior until section 26 is satisfied.

JMRTD is preferable to its wrappers/forks because Atlas can audit the exact current upstream source, control PACE/BAC selection, isolate logging/provider behavior, retain its lifecycle and policy boundaries, and avoid inherited UI/global-retention/implicit-downgrade decisions. It is preferable to gmrtd's current release because it does not always retain APDU traces or contain the same pervasive secret debug logging/permissive signature fallback, and it has a conventional Maven/Java integration path. It is preferable to OpenPACE because it supplies BAC, LDS/DG reading, and SOD primitives as a complete reader foundation.

It is preferable to the transport-only fallback only in functional capability: once approved and validated, it could safely own the protocol mechanics Atlas must not implement. The transport-only fallback remains safer **today** because it opens no access key, sends no APDU, processes no DG/portrait, and creates no LGPL/provider/trust-store exposure. The fallback must remain the behavior if approvals fail, validation fails, or the product elects not to accept the added risk and maintenance burden.

## 26. Conditions for implementation

All of the following are mandatory before any dependency or adapter implementation begins:

1. Obtain written approvals listed in section 24, including a distribution-specific LGPL decision for both app and SDK delivery.
2. Reconfirm that `0.8.8` is still the approved version at implementation time; repeat the security/advisory/release review if a newer version exists rather than silently upgrading or adopting an old version.
3. Pin and verify every artifact/checksum/source commit, lock dependencies, generate SBOM/notices, inspect resolved manifests, and approve the `bcutil`/`bcprov` version relationship.
4. Approve a design in which Atlas owns lifecycle/tokens/cancellation/`IsoDep`/artifacts/evidence and the library owns all passport crypto, secure messaging, APDU protocol, and LDS parsing. No custom protocol crypto is permitted.
5. Specify and test fail-closed suite selection: PACE when a compatible PACE is advertised; BAC only when none is compatible; no fallback after PACE authentication/integrity/processing failure; reject unknown key types/algorithms. Keep CA and AA disabled until separately reviewed.
6. Suppress `org.jmrtd`/`net.sf.scuba` logging before first use, register no APDU listener, ban raw library objects/exceptions from logs/crash events, and pass comprehensive sensitive-log regression tests in debug and release configurations.
7. Complete a clean Atlas Gradle/D8/R8 release build, dependency/permission audit, size/performance measurement, minSdk 26 validation, provider/algorithm tests, and shrinker/reflection testing.
8. Pass representative authorized physical-device/document interoperability for BAC and multiple PACE suites/parameters, DG1/DG2 encodings, malformed/oversized LDS, tag loss, cancellation, timeout, recreation, backgrounding, memory pressure, repeated sessions, and airplane mode.
9. Implement only through a separately approved milestone with bounded in-memory data, clearable Atlas-owned buffers, no persistence/network, finite safe errors, and verified terminal/cancellation cleanup.
10. Approve the offline trust-store design before claiming Passive Authentication: signed provenance, CSCA/link/rollover material, DG hashes/SOD/chain as separate evidence, CRL/reference-time policy, update/rollback/emergency process, and inconclusive handling for missing/stale trust.
11. Complete a threat model and independent security review of the exact adapter and release artifact before any production identity/authenticity/clone-resistance claim.

Failure of any condition means **KEEP TRANSPORT-ONLY FALLBACK**.

## 27. Remaining uncertainties

- Legal interpretation of LGPL-2.1-or-later headers versus repository/POM metadata and compliant replacement/relinking for APK/AAB and downstream SDK customers.
- Exact D8/R8, minSdk 26, API 35, provider, shrinker, method-count, memory, startup, and final APK/AAB impact in Atlas.
- Physical interoperability across document issuers, BAC, PACE GM/IM/CAM, DH/ECDH/Brainpool parameters, DG2 codecs, extended length/APDU behavior, and vendor `IsoDep` implementations.
- Whether Atlas should use the Scuba Android adapter or a minimal Atlas-owned card-service bridge while retaining the existing `IsoDep` lifecycle; no choice is authorized here.
- Full authoritative vulnerability status at future implementation time and upstream support responsiveness.
- Final CSCA/CRL sources, licensing/redistribution terms, country/historical coverage, validity/reference-time policy, update owner, emergency cadence, and bundle size.
- Strict, production-appropriate CA/PACE-CAM evidence behavior and availability across target documents.
- Whether JUL suppression is complete on every supported Android logging bridge and crash-reporting configuration.
- Independent verification of Maven detached-signature identity and a durable upstream release-tag policy.

## 28. Hard STOP statement

M7.1 investigation is complete. Passport protocol implementation has NOT started.

Milestone 8 has NOT started.

**STOP — waiting for human approval before adding any ePassport dependency or implementing BAC/PACE, secure messaging, DG1/DG2, Passive Authentication, or Chip Authentication.**

No production Kotlin, Java, Gradle dependency, Android manifest, resource, runtime behavior, or test was changed by M7.1. The repository has no Git metadata, so this was verified by direct scope/file inspection rather than Git status/diff.
