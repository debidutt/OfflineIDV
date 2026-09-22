# ADR 0016: Contain JMRTD behind a PACE-first, DG1-only adapter

## Status

Accepted for the approved Milestone 7.3 engineering implementation on 2026-09-21. Distribution remains blocked by legal/open-source compliance review and the release/device gates below.

## Context

The Milestone 7 transport boundary can discover an Android `IsoDep` tag but cannot establish protected document-chip access. The M7.2 review froze JMRTD 0.8.8 and its runtime graph as the preferred candidate with limitations. Netherlands residence permits use a TD1 MRZ and an RF chip; the current validation need is limited to reading DG1 and comparing selected printed/chip fields. It does not require DG2, face matching, Passive Authentication, Chip Authentication, or a document-authenticity claim.

## Decision

- Pin `org.jmrtd:jmrtd:0.8.8`, Scuba Smartcards 0.0.21, Bouncy Castle provider 1.85.2, Bouncy Castle utility 1.85, and cert-cvc 1.4.13. Resolve these groups only from Maven Central, fail on conflicts, exclude legacy `bcprov-jdk15on`, and enable dependency locking.
- Keep Android `Tag`, `IsoDep`, Scuba APDUs, and all JMRTD types inside `android:nfc`. The reducer, policy evaluator, shared mapper, and app UI receive only existing Atlas observations and opaque artifacts.
- Parse EF.CardAccess under a 64 KiB limit. Select only reviewed ECDH-GM AES-CBC-CMAC PACE OIDs with standardized EC parameter identifiers 8 through 18.
- Attempt PACE whenever a compatible suite is advertised. Never invoke BAC after any selected PACE attempt fails. Permit BAC only when CardAccess is absent or advertises no compatible reviewed PACE suite.
- Read only EF.DG1 under a 4 KiB limit. Retain document number, nationality, date of birth, and expiry date in clearable Atlas artifacts; discard the raw DG1 representation.
- Configure third-party `java.util.logging` namespaces off before protocol use, register no APDU listener, retain no APDU transcript, expose no exception message/type, and use predefined Atlas failures only.
- Leave Passive Authentication as `NOT_PERFORMED`. Do not read DG2 or invoke AA, CA, EAC-TA, online trust retrieval, or custom cryptography.

## Consequences

The passport TD3 and residence-permit TD1 pipelines share one format-neutral printed/chip comparator. The residence-permit profile requires NFC read and printed/chip consistency but not face matching, allowing its bounded flow to finish after DG1 comparison. A successful result means only that configured MRZ checks and printed/DG1 consistency completed; it does not establish chip/document authenticity, holder identity, clone resistance, or liveness.

The implementation is not release-ready until Gradle resolves and locks the exact graph, strict dependency verification covers the final resolved artifacts, debug/release D8/R8 and manifest audits pass, all JVM/lint/assembly gates pass, legal/open-source obligations are accepted, and representative Netherlands residence-permit hardware validation passes in airplane mode. Any graph/provenance/logging/permission/device failure requires correction or restoration of the transport-only fallback. Milestone 8 is not part of this decision.
