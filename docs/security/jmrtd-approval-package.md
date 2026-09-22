# JMRTD 0.8.8 approval package (Milestone 7.2)

Evidence snapshot: 2026-09-02. Authorization addenda: on 2026-09-21 the repository requester explicitly approved the narrowly limited M7.3 engineering scope and frozen dependency graph; on 2026-09-22 the requester separately approved bounded Netherlands residence-permit PA/CA engineering scope recorded in ADR 0017. Legal/open-source, security/PKI, trust/revocation, release, and device review remain blockers. This record is not legal advice, does not fill the formal review boxes below, and does not authorize distribution or a production-readiness claim.

## 1. Executive decision summary

At the M7.2 evidence snapshot, Project Atlas was **READY FOR HUMAN APPROVAL REVIEW** of a future, separately authorized M7.3 integration of `org.jmrtd:jmrtd:0.8.8`. The requester subsequently authorized the limited M7.3 engineering implementation recorded in ADR 0016. JMRTD remains **ACCEPT WITH LIMITATIONS**, and release/distribution remains blocked until every applicable review below is approved or approved with explicitly accepted conditions.

The original M7.2 package did not approve or add JMRTD. Later requester authorizations permit the protected-access/bounded-DG1 subset in ADR 0016 and the bounded, single-anchor Netherlands residence-permit PA/CA subset in ADR 0017. DG2, AA/TA, broad governed trust/revocation coverage, face work, aggregate authenticity/holder claims, and Milestone 8 remain unauthorized. All unchecked legal, security, privacy, architecture, release, PKI, and risk decisions remain blocking at release unless an authorized reviewer records a decision.

## 2. Historical M7.2 verified baseline

| Check | M7.2 finding |
| --- | --- |
| Workspace | Exactly `/Users/pracheebehera/Documents/Codex/2026-08-02/implement-milestone-1-only-follow-agents` |
| Repository guidance | `AGENTS.md`, `README.md`, the implementation plan, NFC/security/architecture documents, relevant ADRs, NFC contracts, artifact store, reducer/policy, and iOS mapping reviewed |
| M7 | Complete at the safe Android reader-mode, `Tag`, and `IsoDep` transport boundary |
| M7.1 | Investigation complete; `org.jmrtd:jmrtd:0.8.8` is preferred and **ACCEPT WITH LIMITATIONS** |
| Real runtime | Connects and closes `IsoDep`, sends no APDU, and reports `PROTOCOL_UNSUPPORTED` |
| Production dependencies | No JMRTD, Scuba, Bouncy Castle, or EJBCA dependency is declared |
| Custom passport cryptography | None present |
| BAC/PACE/secure messaging/DG1/DG2/PA/CA | Not implemented |
| Milestone 8 | Not started |
| Git | No Git metadata is available; baseline/change auditing must use direct inventory and content hashes rather than `git status`/`git diff` |

The existing architecture is the controlling baseline: Atlas owns lifecycle, operation tokens, cancellation, timeouts, session artifact ownership, safe observations, reducer policy, and terminal outcomes. See [Android ePassport NFC architecture](../architecture/android-epassport-nfc.md), [protocol-engine proposal](../architecture/passport-protocol-engine-proposal.md), and ADRs [0014](../adr/0014-block-unreviewed-epassport-protocol-library.md) and [0015](../adr/0015-activity-bound-nfc-reader-mode.md).

## 3. Frozen proposed dependency graph

The exact upstream runtime graph declared by the JMRTD 0.8.8 POM is:

```text
org.jmrtd:jmrtd:0.8.8                         direct candidate
├── net.sf.scuba:scuba-smartcards:0.0.21     transitive
├── org.bouncycastle:bcprov-jdk18on:1.85.2  transitive
├── org.bouncycastle:bcutil-jdk18on:1.85     transitive
│   └── org.bouncycastle:bcprov-jdk18on:1.85
│       └── mediated and locked to 1.85.2
└── org.ejbca.cvc:cert-cvc:1.4.13            transitive
    └── org.bouncycastle:bcprov-jdk15on:1.68
        └── excluded by JMRTD's POM
```

JMRTD's `junit:junit:4.13.2` and `bcpkix-jdk18on:1.85` entries are test-scope and are not runtime dependencies. No dynamic version, range, snapshot, substitute repository, or unlisted artifact is permitted.

`net.sf.scuba:scuba-sc-android:0.0.26` is **not in the proposed lock**. It is optional, has no JMRTD dependency edge, duplicates Atlas's existing lifecycle-safe `IsoDep` ownership, depends on older `scuba-smartcards:0.0.20`, and contributes an old min/target SDK manifest plus `android.permission.NFC`. A future reviewer may authorize it only through a new dependency delta and full repeat of this package's checks.

### Functional necessity

| Artifact | Direct/transitive | License finding for review | Atlas need in initial M7.3 scope |
| --- | --- | --- | --- |
| `org.jmrtd:jmrtd:0.8.8` | Direct | Weak copyleft: source headers state LGPL-2.1-or-later; POM/root-license metadata is inconsistent, so legal must determine the operative grant and conditions. | **Required** for BAC, PACE, secure messaging, LDS/SOD parsing, and protocol cryptographic mechanics. |
| `net.sf.scuba:scuba-smartcards:0.0.21` | Transitive | LGPL-2.1-or-later per published source/POM; legal/OSS confirmation remains required. | **Required** for JMRTD's card-service/APDU abstraction. Atlas must bridge its existing transport without exposing APDUs upward. |
| `org.bouncycastle:bcprov-jdk18on:1.85.2` | Transitive | Permissive Bouncy Castle license; include its copyright, permission, and warranty text as compliance directs. | **Required** for provider algorithms used by JMRTD. It must remain privately instantiated; Atlas must not globally register or reorder it. |
| `org.bouncycastle:bcutil-jdk18on:1.85` | Transitive | Permissive Bouncy Castle license; include its copyright, permission, and warranty text as compliance directs. | **Required** for ASN.1/CMS/ICAO structures used by SOD and related paths. Its `[1.85,1.86)` package range is compatible with provider-only patch 1.85.2. No 1.85.2 bcutil artifact exists. |
| `org.ejbca.cvc:cert-cvc:1.4.13` | Transitive | LGPL-2.1 per the published POM/source license; legal/OSS confirmation remains required. | **Declared upstream but not functionally required** for the proposed BAC/PACE/DG1/DG2/PA scope; its JMRTD references are in CVC/terminal-authentication support. The default approval lock keeps the published graph intact. M7.3 must either prove safe exclusion with clean D8/R8 and runtime tests, or prove the artifact unreachable and contained. Its unsuppressible stack-trace paths are an implementation blocker until one of those proofs is accepted. |

## 4. Proposed dependency lock and checksums

Repository for every proposed artifact: Maven Central only, canonical base `https://repo1.maven.org/maven2/`. SHA-256 values below were independently calculated from artifacts downloaded on 2026-09-02. They are proposed inputs to Gradle dependency verification, not an indication that Gradle has been changed.

| Coordinate | Binary SHA-256 | POM SHA-256 | Sources SHA-256 |
| --- | --- | --- | --- |
| `org.jmrtd:jmrtd:0.8.8` | `3f5af114afd3a9dde4b4205e157fd696208d56a989b8e8e835de1e95b67bf6b5` | `3d7f815f00db8b19acc40d5afb29bfcb9be22c40daebdc85c2f583e5987d2d57` | `48134c4252515831fc509006f280a5e32ecd30873d5ca3c2b2bf6cc45940ac22` |
| `net.sf.scuba:scuba-smartcards:0.0.21` | `747f3207e92ad890c55598a40c3c695c3865e7a512b5cb40c2d1f961ec1cf245` | `0004f4e2e22a5cf1c9c93f8342d88d405921759418d8aeb367834c3d6446d5fc` | `7f7d2d1825d613bf5574e960ca733be39e860fd9456f4472d92e5a2ae03ea0c5` |
| `org.bouncycastle:bcprov-jdk18on:1.85.2` | `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5` | `774ffb514ef342d7e7543805944fec32e4da2c4292ba5d7d8560f05a65a7c2dd` | `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f` |
| `org.bouncycastle:bcutil-jdk18on:1.85` | `590f55ed5d68529239898a4a5c4f730b6e37f45d1cfa3fbe51f8485abe32c42d` | `9f1c79a12a7c03d78860e05750dcc44e855e20205c7af7cffbf29a69448d85ea` | `b470a692878f92abf00b9c3af9147a45453250a80930df8f23f1d092c55e2d5e` |
| `org.ejbca.cvc:cert-cvc:1.4.13` | `3e4467de94a36dd3c93eddb816e91abc7035f7e05d4123b1bbfb9dde9df9ab97` | `d9b23d33179cc87c304d6cc39d374eb94d6d59f166943be134458d9ad464f754` | `10c4497a3698a9e37add3bdbc8b3ea994d502ad2100e3eb7a878a21121f48a26` |

For completeness, the excluded `scuba-sc-android:0.0.26` AAR checksum is `6fdcd956b4ef9296d6bac8d4b27f32fe42c94122ba34e98d56e46bec29e83a8d` and its POM checksum is `87b4e9b4b39567180d655edeeeaeb158955153e5f554e958231afd982b672fdc`. Maven Central publishes no source JAR at the expected coordinate.

### POM and source provenance

| Artifact | POM provenance | Source provenance and confidence |
| --- | --- | --- |
| JMRTD 0.8.8 | Maven Central POM; SCM fields still point to older SourceForge/SVN metadata | SourceForge Git commit `277cd36445d63e037498dae2463cb44bd155f897` (`Version 0.8.8.`). M7.1 byte-matched published `org/**` source to this commit. **High**. |
| Scuba Smartcards 0.0.21 | Maven Central POM, published 2026-08-22; SCM points generically to SourceForge | SourceForge Git commit `d9f6308e933005999ecf532cfd98e34d8a094966` (`scuba-smartcards to 0.0.21.`). Published Java sources match that release state. **High**, but the absence of an immutable release tag should be reviewed. |
| bcprov 1.85.2 | Maven Central POM; official Bouncy Castle repository named | Official `bcgit/bc-java` tag `r1rv85v2`, dereferenced commit `854bd5c4286e1530132418906bf6175da7d9d83c`; `gradle.properties` identifies 1.85.2. **High**. |
| bcutil 1.85 | Maven Central POM; declares bcprov 1.85 | Official `bcgit/bc-java` tag `r1rv85`, commit `57fbd3c501f7a369f64eda311d6a709fc0bcae84`; `gradle.properties` identifies 1.85. **High**. |
| cert-cvc 1.4.13 | Maven Central POM contains obsolete SVN/Fisheye SCM links | Published Java sources exactly match Keyfactor commit `12f87797793cbe37825b0190d97a63883e5fac6d`; nominal tag `cert-cvc_1_4_13` dereferences to its parent `9f7cfe8c3deb09535d236ba3a2591512a3f7464a`. The release/tag mismatch must remain visible. **Medium**. |

Detached signatures are published for release artifacts where Maven Central lists them, but M7.2 did not independently establish signer identity or a trusted key chain. A future release-engineering gate must verify signature identity where feasible and must rely on reviewed SHA-256 verification regardless.

### Packaging, permissions, networking, and logging

| Artifact | Android manifest/resources | Permissions | Networking | Logging/representation surface |
| --- | --- | --- | --- | --- |
| JMRTD | Plain JAR; none | None | No client or automatic network path found | Extensive `java.util.logging`; sensitive `toString()` surfaces; APDU listener APIs; AA error path logs a command APDU. |
| Scuba Smartcards | Plain JAR; none | None | None found | `java.util.logging` in smart-card/TLV paths and APDU listener abstractions. |
| bcprov | Plain multi-release JAR; none | None | Contains opt-in LDAP, OCSP, CRL URL, and certificate-store networking classes. Atlas must never invoke them; no runtime trust fetch is permitted. | Uses internal `java.util.logging` in some provider/utility paths; no added logging framework. Provider/key/certificate exceptions must be translated. |
| bcutil | Plain JAR; none | None | No automatic network behavior in the proposed path | No added logging framework; objects/exceptions still stay inside the adapter. |
| cert-cvc | Plain JAR; none | None | None found | No logging framework, but production `CVCPublicKey.getEncoded()` and `CardVerifiableCertificate.getPublicKey()` call `printStackTrace()`; certificate `toString()` can disclose CVC content. This cannot be solved by JUL suppression alone. |

The raw runtime JARs total approximately 11.14 MiB before dexing and shrinking, dominated by bcprov. Final APK/AAB size, method count, D8/R8 compatibility, reflection/keep rules, and release-artifact contents are unknown until M7.3-A.

## 5. Mandatory supply-chain controls for M7.3

Before any production dependency is accepted, M7.3 must:

1. Declare only the exact coordinates in section 3; use no dynamic versions, ranges, snapshots, dependency substitution, local binary, JitPack, or unapproved repository.
2. Restrict resolution to Maven Central with Gradle repository content filters for the exact approved groups; fail on project repositories and unexpected repository sources.
3. Enable strict Gradle dependency verification with the binary, POM, source, and metadata checksums reviewed for the resolved graph.
4. Enable dependency locking for every relevant configuration and review the lockfile as a security artifact.
5. Fail resolution on version conflict rather than silently selecting a different transitive; explicitly assert bcutil 1.85 plus mediated bcprov 1.85.2 and the exclusion of `bcprov-jdk15on:1.68`.
6. Record source tag/commit, release URL, POM, binary/source checksums, publication time, reviewer, and source-to-release comparison in a durable provenance record.
7. Generate CycloneDX or SPDX SBOM entries for the final APK, AAB, and any SDK AAR; confirm all five proposed artifacts and no unexpected dependency.
8. Produce third-party notices and the complete applicable license texts; link each binary to its notices/source offer/replacement instructions as legal requires.
9. Review the final merged manifest and packaged binary for permissions/components; `INTERNET` and `ACCESS_NETWORK_STATE` remain absent.
10. Run SCA/advisory checks against the exact locked graph at approval, merge, and release time. Record false-positive analysis, particularly for the excluded legacy bcprov edge.
11. Re-audit sensitive logging, `toString()`, network-capable provider paths, reflection, native code, resources, and unexpected service providers from the final resolved artifacts.
12. Preserve release artifact provenance: signed CI inputs, reproducible build records where practical, hashes of APK/AAB/AAR, SBOM, lockfile, verification metadata, and reviewer sign-off.
13. Repeat security/license/release review for every version or checksum change. Version reuse with changed bytes is a hard failure.
14. Assign an owner and at least quarterly review cadence, plus event-driven review for upstream releases, advisories, repository/signing changes, and app releases.

### Later rejection triggers

The dependency must be rejected or removed and Atlas must return to `PROTOCOL_UNSUPPORTED` if any of these occurs:

- checksum, signature, source, POM, tag/commit, or repository provenance mismatch;
- an unexpected transitive, native library, manifest component, permission, resource, network path, telemetry path, or logging dependency;
- unresolved exploitable security advisory or unsupported cryptographic algorithm/provider behavior;
- failure to contain JMRTD/Scuba/BC/cert-cvc logging, exceptions, object representations, APDU listeners, or cert-cvc stack traces;
- implicit or accidental PACE-to-BAC downgrade, unknown-suite fallback, or use of custom Atlas passport cryptography;
- D8/R8, minSdk 26, target/compile SDK, provider, algorithm, shrinker, or representative-device failure;
- inability to impose LDS/image/time/memory bounds or to clean session artifacts after every terminal/cancellation path;
- legal/OSS conditions cannot be met for any intended distribution form or downstream redistributor;
- missing/stale/ungoverned trust data is treated as valid PA, or runtime networking becomes necessary;
- upstream abandonment, unresponsive critical vulnerability handling, or inability to obtain auditable source; or
- any blocking reviewer rejects the integration or leaves a required review unanswered.

## 6. LGPL and distribution approval checklist

This checklist frames questions for qualified legal and open-source reviewers. It is not legal advice. Reviewers must identify the operative license version for each component and resolve JMRTD's source-header/POM/root-license ambiguity.

### Distribution scenarios and questions

| Scenario | Questions requiring written determination |
| --- | --- |
| Atlas APK | Does dex merging/R8 packaging create a combined work or other distribution condition? Which notices, license text, corresponding source, relinking/replacement mechanism, and modification disclosures are required? |
| Atlas AAB | Do Play-generated split APKs change source/relinking delivery or notice placement? How will obligations remain accessible to every recipient? |
| Atlas inside another Android app | Which party is the redistributor, who provides notices/source/replacement instructions, and how are host signing and shrinker choices controlled? |
| Atlas as SDK/AAR | What obligations and contract terms must flow to customers who package and redistribute JMRTD/Scuba/cert-cvc? Is a separate distribution bundle required? |
| Static/dynamic Android reality | How should counsel characterize DEX/AOT merging, class loading, R8 rewriting, and the absence of a conventional shared-library replacement mechanism? |
| Replacement/relinking | What practical method permits a recipient to replace or relink the LGPL component, and is that method viable with Android signatures, split APKs, and store delivery? |
| Corresponding source | Which exact component source, build scripts, modifications, interface definitions, and offer/delivery mechanism must be provided, for how long, and by whom? |
| Notices | Where must copyright, license, source offer, warranty disclaimer, and modification notices appear in app, store listing, SDK docs, and distributed archives? |
| Modifications | If Atlas patches, shades, relocates, optimizes, or otherwise modifies a component, what source and prominent-change obligations apply? Must such changes be forbidden? |
| Downstream redistribution | What compliance pack, audit rights, customer terms, and support process are needed for integrators and white-label hosts? |
| App signing | Does preventing replacement of signed APK/AAB contents conflict with any required reverse-engineering, debugging, or relinking right, and what keys/artifacts need not or must not be supplied? |
| Play Store | Are Play terms, signing, integrity protection, obfuscation, split delivery, and takedown processes compatible with the chosen compliance method? Is in-app notice access sufficient? |

Reviewers must also classify Bouncy Castle's permissive license and cert-cvc/Scuba LGPL obligations independently; approving JMRTD alone is not approval of the full graph.

**LEGAL REVIEW:**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

Reviewer/name/date: ____________________

Conditions/rationale: ____________________

**OPEN-SOURCE COMPLIANCE:**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

Reviewer/name/date: ____________________

Conditions/rationale: ____________________

## 7. Architecture and security package

The proposed boundary, Kotlin-like contract, PACE/BAC decision table, fail-closed invariants, logging and exception controls, memory/artifact ownership, bounded LDS reads, DG1 minimization, DG2 `PortraitArtifactRef` flow, PA evidence model, and optional CA plan are specified in [PassportProtocolEngine proposal](../architecture/passport-protocol-engine-proposal.md).

The complete ePassport-specific threats, mitigations, residual risks, and required tests/reviews are in [ePassport threat model](epassport-threat-model.md). Offline CSCA, Master List, DSC, link-certificate, revocation, freshness, rollback, and governance decisions are in [offline CSCA trust strategy](offline-csca-trust-strategy.md). These documents are normative M7.3 entry conditions.

## 8. Existing hardware-validation debt and entry gate

No hardware item below was executed in M7.2. The concrete matrix is in [ePassport device validation matrix](../testing/epassport-device-validation-matrix.md).

| Debt | Status | M7.3 recommendation |
| --- | --- | --- |
| M6 CameraX capture, rotation, permission behavior, preview lifecycle, ML Kit, airplane-mode OCR | **OPEN — NOT EXECUTED** | Execute before M7.3 implementation as an entry gate. It validates the lifecycle/artifact foundations M7.3 will share. |
| M7 NFC capability, disabled state, reader mode, `IsoDep`, tag removal, cancellation | **OPEN — NOT EXECUTED** | Execute before dependency integration as a blocking entry gate. Do not debug protocol behavior on an unvalidated transport. |
| M7.3 passport/protocol matrix | **FUTURE** | Execute in M7.3-H after each earlier phase passes synthetic/fuzz/build gates; required before release approval. |

Failure of M6/M7 device gates keeps the transport-only fallback. It does not justify adding JMRTD speculatively.

## 9. Proposed M7.3 implementation sequence

Documentation only; none of these phases has started.

| Phase | Scope and dependencies | Required tests and exit criteria | STOP conditions |
| --- | --- | --- | --- |
| M7.3-A Dependency/supply chain | Add only the approved lock after all blocking approvals and M6/M7 entry gates. Decide cert-cvc exclusion/reachability. Configure verification, locking, SBOM/notices, repository filters. | Clean resolution; exact graph/checksums; debug and release D8/R8; minSdk; manifest/permission/offline audit; APK/AAB/AAR size and method impact; no unexpected classes/resources/native code. | Any graph/provenance/license/build/permission/network/logging deviation. |
| M7.3-B Protocol adapter | Implement the documented `PassportProtocolEngine` boundary and Atlas transport/card-service bridge; retain token, cancellation, timeout, and artifact ownership. | Contract tests, late-callback/tag-substitution tests, no leaked JMRTD/Android/APDU/raw exception types, cleanup and redaction tests. | Need to expose prohibited types, construct custom protocol crypto/APDUs outside adapter, or weaken current lifecycle. |
| M7.3-C PACE/BAC | CardAccess parsing, compatible-suite allowlist, PACE-first policy, explicit BAC eligibility. CA/AA disabled. | Complete downgrade table tests; compatible/unsupported/malformed CardAccess; PACE auth/technical/timeout failures never call BAC; key/log capture tests. | Any silent downgrade, unknown suite/key best effort, or sensitive logging. |
| M7.3-D DG1 bounded read | Authenticated bounded DG1 read; extract only document number, birth date, expiry, nationality for comparison; no persistence/UI/state. | Size/TLV/ASN.1 fuzzing; field-minimization assertions; malformed/limit/cancel tests; printed/chip observation only. | Raw DG/identity escapes boundary or limits cannot be enforced before allocation. |
| M7.3-E DG2 bounded read | Bounded DG2 parsing and portrait extraction to session artifact store, returning only `PortraitArtifactRef`. | Encoding corpus, decompression-bomb/dimension/size limits, malformed image fuzzing, memory pressure, cleanup/cross-session isolation. | M8/JMRTD coupling, unbounded decode/allocation, persistence, or portrait leak. |
| M7.3-F PA primitives | DG hash, SOD signature, DSC extraction, and separately governed signer-trust evidence. Requires approved offline-trust strategy/bundle or reports trust unknown. | Valid/invalid/partial/missing/stale/unsupported evidence tests; signed trust-bundle/rollback tests; issuer-gap behavior; no binary authenticity collapse. | Missing governance, stale/missing trust reported valid, online fetch, or ambiguous evidence collapsed. |
| M7.3-G Optional CA extension | Only if separately approved; DG14-bound, PA-prerequisite CA with strict suites. | Suite/parameter, signed-DG14 binding, secure-messaging transition, clone-resistance evidence, failure mapping, real documents. | No separate approval, PA prerequisite absent, unknown-algorithm fallback, or authenticity overclaim. |
| M7.3-H Device/passport validation | Execute the full matrix across vendors/OS/passports/protocol/lifecycle/offline conditions. | All blocking matrix rows pass with sanitized evidence; release build and authorized runtime-only passports; independent security review. | Real identity data committed, required coverage unavailable/unaccepted, regression, leak, crash, hang, or flaky cleanup. |

Each phase requires review of the exact delta and must preserve `PROTOCOL_UNSUPPORTED` in production until its own exit criteria and the release gate are accepted. Partial success is not authority to ship.

## 10. Required M7.3 test families

- exact dependency graph/checksum/signature/repository/lock/SBOM/notices tests;
- source, merged-manifest, APK/AAB/AAR permission/component/native/resource audits;
- D8/R8, shrinker/reflection, provider, algorithm, duplicate-class, minSdk 26, size, method-count, startup, and memory tests;
- protocol contract/type-leak and no-custom-crypto/APDU-construction audits;
- exhaustive PACE/BAC downgrade and suite/key allowlist tests;
- logcat, JUL, stderr, crash-event, exception-message, state/UI, and `toString()` capture tests with sentinel secrets;
- malformed/oversized/truncated BER/TLV/ASN.1, LDS, SOD, certificate, CardAccess, and image fuzz/property tests;
- per-file, APDU-buffer, decoded-image, aggregate-memory, duration, cancellation, and cleanup limit tests;
- DG1 minimization and DG2 `PortraitArtifactRef` ownership/cross-session tests;
- PA layer-separation, trust freshness/coverage/revocation/rollback tests;
- late callback, tag substitution, repeated scan, recreation, backgrounding, tag loss, timeout, cancellation, and terminal cleanup tests;
- the authorized real-device/passport matrix in airplane mode; and
- independent adapter/source/release-artifact security and privacy review.

## 11. Formal approval checklist

A blank box is **not approval**. Each approved-with-conditions decision must list its conditions, owner, due date, and whether it blocks implementation or only release.

**SECURITY / CRYPTO**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**LEGAL / OSS**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**PRIVACY**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**ARCHITECTURE**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**ANDROID / RELEASE ENGINEERING**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**PKI / TRUST GOVERNANCE**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

**PRODUCT / RISK / COMPLIANCE**

- [ ] APPROVED
- [ ] APPROVED WITH CONDITIONS
- [ ] REJECTED

Decision record location: ____________________

Reviewers/names/dates: ____________________

Conditions, owners, due dates, accepted residual risks: ____________________

## 12. Approval rule and unresolved blockers

The M7.2 decision required separate authorization before M7.3 could start. The requester supplied that limited engineering authorization on 2026-09-21 and explicitly retained legal/open-source review as a release blocker. A missing formal review is not release approval. Silence is not approval. M7.1's **ACCEPT WITH LIMITATIONS** finding alone is not release authorization.

Current unresolved blockers are:

1. all seven organizational approvals and both explicit legal/OSS determinations are blank;
2. the cert-cvc retain-versus-exclude decision and its `printStackTrace()` containment proof;
3. independent detached-signature identity verification and durable provenance records;
4. successful Atlas Gradle/D8/R8/minSdk/provider/release build with the exact graph;
5. final APK/AAB/SDK licensing, notices, source/relinking, signing, and downstream obligations;
6. complete logging/exception/APDU/toString containment in debug and release artifacts;
7. empirical device/corpus validation of proposed LDS/image/time/memory limits;
8. M6 camera/OCR and M7 NFC real-device debt;
9. representative authorized passport interoperability and malformed-input testing;
10. governed offline CSCA/CRL inputs, policy, update ownership, coverage, freshness, and rollback process before signer trust can be claimed; and
11. independent security/privacy review of the eventual adapter and exact release artifact.

The approval package is complete even though these decisions remain deliberately unresolved; resolving them is the job of human approval and future authorized implementation/release work.

## 13. M7.2 status and mandatory stop

Historical M7.2 close: no production Kotlin or Java, reducer, policy evaluator, NFC implementation, Android manifest/resource/runtime configuration, Gradle declaration, version catalog, or test was changed by M7.2. At that snapshot, JMRTD, Bouncy Castle, Scuba, and EJBCA had not been integrated and M7.3 had not started. The later limited M7.3 implementation is recorded in ADR 0016 and `docs/implementation-plan.md`. Milestone 8 has not started.

**M7.2 approval preparation is complete. The 2026-09-21 requester approval authorizes limited M7.3 engineering implementation only.**

**STOP before release/distribution until every applicable formal, supply-chain, build, device, legal, and open-source gate is satisfied.**
