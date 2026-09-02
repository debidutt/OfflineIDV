# Offline CSCA trust strategy (Milestone 7.2)

Status: approval-level architecture for a future offline trust implementation. No CSCA trust store, ingestion tool, network client, certificate policy, or Passive Authentication implementation is created or approved here.

## 1. Objective and non-claims

Atlas needs a governed, versioned, read-only offline trust snapshot to decide whether a Document Signer Certificate (DSC) chains to an accepted Country Signing Certification Authority (CSCA). JMRTD can supply SOD/DSC and cryptographic primitives, but it does not supply Atlas's trust policy or governance.

Successful chip access, matching DG1, valid DG hashes, or a valid SOD signature does not by itself establish issuer trust. A Master List is input to trust governance, not an automatic trust decision. Atlas must never report signer trust as valid when the trust snapshot is missing, stale, uncovered, unverified, or rolled back.

The runtime remains offline. It must not fetch ICAO PKD data, Master Lists, DSCs, CSCA certificates, link certificates, CRLs, OCSP responses, policy, telemetry, or updates. Trust snapshots arrive only inside normal signed app/SDK releases.

## 2. Evidence separation

Passive Authentication must preserve these separate facts:

| Evidence | Question answered | Does not answer |
| --- | --- | --- |
| DG hash status | Does each read DG hash match the value in SOD? | Who signed SOD; whether signer is trusted; chip possession/liveness. |
| SOD signature status | Is the SOD signature cryptographically valid under the embedded/extracted DSC? | Whether that DSC chains to an accepted CSCA or is revoked. |
| DSC extraction | Is a parseable signer certificate available? | Whether it is trusted/current/covered. |
| DSC path/trust | Does DSC validate to an accepted CSCA under Atlas policy? | Whether all DGs match; whether the live chip is uncloned. |
| Trust freshness | Is the exact trust/CRL snapshot within approved age/validity policy? | Country coverage or document-specific success. |
| Coverage | Does the snapshot cover the issuer/country and relevant document era? | Signature/hash validity. |

Runtime output follows the structured model in [PassportProtocolEngine proposal](../architecture/passport-protocol-engine-proposal.md), including `VALID`/`FAILED`/`NOT_CHECKED` per DG, separate SOD signature and DSC extraction, and `TRUSTED`, `UNTRUSTED`, `UNKNOWN`, `TRUST_STORE_STALE`, or `ISSUER_NOT_COVERED` signer trust.

## 3. Trust inputs

Potential inputs, each requiring independent approval, are:

- CSCA trust anchors obtained through an approved out-of-band process;
- ICAO CSCA Master Lists and the full Master List signer validation chain anchored in an independently established ICAO/United Nations trust anchor;
- current and historical/retired CSCA certificates needed for in-scope passports;
- CSCA link certificates and documented rollover relationships;
- CRLs and any approved national/bilateral supplements;
- country/issuer/document-era coverage metadata;
- algorithm, key-size, curve, certificate-constraint, name/country-mapping, and reference-time policy; and
- provenance, license/redistribution terms, retrieval time, issue/effective/next-update dates, signer identity, hashes, and reviewer decisions.

The [ICAO Master List](https://www.icao.int/icao-pkd/icao-master-list), [ICAO ePassport basics](https://www.icao.int/icao-pkd/epassport-basics), and [ICAO Doc 9303 Part 12](https://www.icao.int/sites/default/files/publications/DocSeries/9303_p12_cons_en.pdf) are primary references. Their availability does not authorize ingestion, establish complete country coverage, or resolve redistribution terms.

## 4. Offline ingestion and release architecture

```text
approved external source
        ↓ isolated acquisition; retain original bytes and provenance
quarantine/staging
        ↓ signature/hash/format/validity/duplicate checks
Master List signer validation under independently pinned trust
        ↓ extract CSCAs/link certs/CRLs without auto-trusting them
policy normalization and coverage analysis
        ↓ four-eyes PKI/security/legal review
canonical Atlas trust snapshot + manifest
        ↓ sign, hash, version, archive, SBOM/release record
normal signed app/SDK release
        ↓ runtime verifies embedded snapshot before use
read-only in-memory trust evaluator (no network)
```

Acquisition/ingestion is release engineering, not mobile runtime. It must run in a controlled environment with immutable source retention, deterministic/canonical output where practical, locked parser/tool dependencies, and an audit trail. The production app receives only the approved normalized snapshot, its manifest, and verification key material needed to authenticate it.

## 5. Snapshot format and identity

Each snapshot manifest should contain at least:

- monotonic Atlas trust-store version and schema version;
- creation, source retrieval, review, effective, expiry/stale-after, and next-review timestamps;
- source URLs/identifiers and SHA-256 of every original Master List/CRL/supplement;
- Master List signer certificate fingerprint and verified chain identifier;
- sorted fingerprints and metadata for accepted active/retired CSCAs, link certificates, CRLs, and national additions;
- inclusion/exclusion reason and approval record for every input certificate;
- country/issuer/document-era coverage and known gaps;
- algorithm/policy profile version and reference-time policy;
- revocation policy and freshness state per issuer/source;
- prior snapshot version/hash and emergency/rollback marker;
- generator/tool/SBOM/source commit identifiers;
- four-eyes reviewer identities/roles and legal redistribution approval reference; and
- snapshot payload hash plus release signature/key identifier.

The runtime exposes only safe metadata needed by policy, such as snapshot version, coarse freshness category, coverage category, and policy version. It must not expose certificate subjects, serials, raw certificates, or country/identity data to UI/logs/analytics.

## 6. Master List signer trust

Before extracting any CSCA as a candidate anchor, ingestion must:

1. establish the Master List signer root/chain out of band under written PKI governance;
2. verify container format, signature, signer chain, validity/constraints, and accepted algorithms;
3. bind the exact original bytes and signer fingerprints to the audit record;
4. reject unsigned, ambiguously signed, malformed, duplicate-conflicting, or unapproved-list input; and
5. treat every extracted certificate as a candidate subject to Atlas inclusion/coverage policy, not automatically trusted because it was listed.

Trust-anchor establishment and replacement are ceremonies with four-eyes control. A new ICAO/UN signer/root, cross-sign, or algorithm transition is a governance event, not an automatic software update.

## 7. DSC path validation and CSCA rollover

Runtime path validation must use only the immutable selected snapshot and approved policy. It should:

- parse the DSC and SOD under byte/count/complexity limits;
- verify SOD signature separately before/alongside path evaluation;
- build only paths terminating at accepted CSCAs for the asserted issuer/document profile;
- validate certificate signatures, basic constraints/key usage and relevant eMRTD profile rules, algorithms/key sizes/parameters, name/country relationships, and policy-defined validity at the approved reference time;
- handle direct CSCA rollover and link certificates explicitly, including direction, validity overlap, path loops, conflicts, and retired anchors;
- never use chip-supplied, Android system, network-fetched, or arbitrary application certificates as trust anchors; and
- report path failure, unsupported algorithm, missing link, conflicting path, or coverage gap distinctly enough for safe policy evidence.

Reference time requires PKI/product approval. Candidate choices include scan time, document issuance/signing time where reliably and cryptographically established, and current time; they have materially different expired-document/historical-validation consequences. The code must not silently choose one.

## 8. Revocation and CRLs

A Master List is not a revocation service. Governance must decide:

- which CSCA/DSC/link-certificate CRLs are required and from which authenticated sources;
- whether and how historical validation uses revocation status at signing/reference time;
- accepted CRL signature/issuer/number/delta/base relationships and maximum age;
- behavior for missing, stale, malformed, conflicting, expired, future-dated, or unsupported CRLs;
- whether any issuer has no usable offline revocation data and how that limits product claims; and
- emergency response for compromised DSC/CSCA or incorrect revocation material.

The runtime performs no OCSP, LDAP, HTTP, AIA, CRL distribution-point, or other network lookup, including through Bouncy Castle's network-capable certificate APIs. Missing/stale/unavailable revocation produces explicit unknown/stale evidence under policy, never silent success.

## 9. Versioning, updates, expiry, and rollback prevention

- Bundle updates only through reviewed signed app/SDK releases; there is no runtime PKD dependency or background updater.
- Assign a release owner and a normal cadence no slower than the organization's accepted Master List/CRL freshness policy. The M7.1 snapshot observed roughly quarterly ICAO Master List updates, but governance must set the actual maximum age and emergency SLA.
- Monitor source issue/next-update/expiry dates and alert release/PKI owners before the embedded snapshot becomes stale.
- Compare the embedded monotonic version and previous-hash chain against app-protected state where available. An older or mismatched snapshot reports rollback/stale/invalid and cannot yield trusted signer evidence.
- Define signed key rotation, compromised-key replacement, emergency revocation, and snapshot withdrawal procedures.
- Keep the previous accepted snapshot and its decision record for forensic/audit use, not as an automatic runtime fallback after current-snapshot failure.
- App/device downgrade can defeat local monotonic state after reinstall or data loss; app signing, platform anti-rollback where available, release policy, and explicit snapshot age checks reduce but do not eliminate this residual risk.

## 10. Missing-country and stale-trust behavior

| Condition | Runtime trust evidence | Prohibited behavior |
| --- | --- | --- |
| Issuer/country absent | `ISSUER_NOT_COVERED` | Trying another country's CSCA, system roots, chip roots, or reporting invalid/authentic. |
| Required historical/rollover anchor absent | `UNKNOWN` or `ISSUER_NOT_COVERED` with safe internal reason | Ignoring chain gaps. |
| Snapshot past stale-after/expiry | `TRUST_STORE_STALE` | `TRUSTED`, silent use, or network refresh. |
| Snapshot signature/hash/schema invalid | `UNKNOWN`/technical trust unavailable | Loading partial contents or last-known snapshot without explicit approved policy. |
| CRL missing/stale under a required revocation policy | `UNKNOWN` or stale trust evidence | Treating no revocation data as not revoked. |
| DSC path cryptographically invalid/revoked | `UNTRUSTED` with finite reason category | Collapsing all causes to counterfeit or exposing certificate details. |
| Path and all required policy/freshness/coverage checks pass | `TRUSTED` | Converting trust alone to whole-passport/holder authenticity. |

The reducer/product policy decides what these observations mean. The trust component does not select retry or a terminal `VerificationOutcome`.

## 11. Auditability and governance ownership

These are governance controls, not code-only tasks:

| Owner | Required decision/evidence |
| --- | --- |
| PKI/trust governance | Root establishment, accepted sources, Master List signer chain, CSCA/link/rollover rules, reference time, CRL policy, coverage, freshness, key ceremonies, incident response. |
| Security/crypto | Parser/algorithm constraints, signature/path validation design, rollback protection, threat/fuzz review, crypto agility. |
| Legal/OSS/data licensing | Rights to acquire, transform, bundle, archive, and redistribute Master Lists, certificates, CRLs, metadata, and tooling. |
| Privacy | Certificate metadata exposure, logs, support bundles, audit retention, country/issuer information, deletion rules for scan-derived evidence. |
| Release engineering | Reproducible ingestion, locked tools, signatures/hashes, SBOM, CI custody, app/SDK release cadence, emergency distribution. |
| Product/risk/compliance | Country/document coverage, stale/missing behavior, historical/expired documents, accepted inconclusive rate and claims. |

Audit records must be immutable, access-controlled, and sufficient to reconstruct why a certificate was accepted/excluded and which exact snapshot validated a session, without retaining passport-holder identity or scan data.

## 12. Required validation

- known valid/invalid Master List signatures and independent signer-chain verification;
- malformed CMS/certificate/CRL/link inputs, duplicate/conflicting anchors, cycles, huge fields, unsupported algorithms, and fuzz corpus;
- DSC direct-chain, rollover/link, expired/historical, revoked, wrong-country/root, self-signed, missing-intermediate, and ambiguous-path cases;
- per-country/document-era coverage reports reviewed against authorized corpora;
- missing/stale/future-dated/expired/conflicting CRL and snapshot cases;
- corrupted payload/manifest/signature/schema, monotonic-version rollback, app downgrade/reinstall, key rotation, and emergency withdrawal drills;
- deterministic snapshot generation and independent binary/hash/SBOM verification;
- host application with network permission plus traffic monitoring to prove the trust path never invokes provider LDAP/OCSP/URL APIs; and
- release-age alerts and an operational tabletop demonstrating normal and emergency update SLAs.

## 13. Approval blockers

Before M7.3 can report `SignerTrust.TRUSTED`, reviewers must approve the root ceremony, source and redistribution rights, snapshot format/signing keys, CSCA/link/reference-time rules, revocation policy, country coverage, maximum age, update/emergency owner and cadence, rollback handling, audit retention, safe evidence mapping, and validation evidence.

If those are not approved or an in-scope issuer is missing/stale, Atlas may still expose separately valid DG-hash/SOD-signature evidence if implemented and approved, but signer trust remains unknown/stale/uncovered. It must never claim Passive Authentication is wholly valid on that basis.

**The offline CSCA trust store is not implemented. Runtime networking remains prohibited.**
