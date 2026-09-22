# ePassport library security review

## Review status

Historical checkpoint with M7.3/M7.4 addenda: requester authorization on 2026-09-21 permitted the ADR 0016 protected-access/DG1 engineering subset, and separate authorization on 2026-09-22 permitted the bounded Netherlands residence-permit PA/CA subset in ADR 0017. Statements below about the “current runtime” describe the M7/M7.1 snapshot. Legal/open-source, trust/revocation, and final security/release/device approval remain open.

The original Milestone 7 checkpoint correctly approved no protocol dependency and stopped Atlas at the real Android `NfcAdapter`/`Tag`/`IsoDep` boundary. The follow-up Milestone 7.1 investigation completed on 2026-09-01 and supersedes the original evidence gaps:

- [Full M7.1 ePassport protocol investigation](epassport-protocol-investigation.md)
- Preferred candidate: `org.jmrtd:jmrtd:0.8.8`.
- Candidate classification: **ACCEPT WITH LIMITATIONS**.
- Project recommendation: **PROCEED ONLY AFTER SPECIFIC APPROVALS**.
- Current runtime decision: keep `PROTOCOL_UNSUPPORTED`; no dependency or protocol adapter is approved for addition by M7.1 alone.

M7.1 retrieved and matched the exact source and artifacts, reconstructed transitives, re-audited logging/privacy and downgrade behavior, reviewed realistic alternatives, and investigated offline CSCA governance. The previously reported BAC-key logger is historical and absent from 0.8.8. Current privacy, LGPL, provider, Android/device, trust-store, supply-chain, and organizational-approval limitations remain material.

## Milestone 7.1 safety boundary (historical)

Atlas still does not implement or package BAC, PACE, secure messaging, passport APDU logic, LDS parsing, DG1/DG2 reads, ASN.1 signature verification, certificate-chain validation, Passive Authentication, or Chip Authentication. Real Android Mode opens/closes `IsoDep`, does not open the MRZ-derived key, transmits no APDU, never falls back to fake NFC, and reports protocol support as unavailable.

This is an implementation-level safety block, not permission to bind a fake protocol in Real Android Mode and not a claim that chip access or MRZ equality proves authenticity.

## Decision summary

JMRTD 0.8.8 is the preferred candidate because its exact published source is current and auditable, it supports the required offline passport protocols, and it can fit an Atlas-owned lifecycle/cancellation/artifact boundary. It is not unconditionally approved. Before implementation, Atlas needs the complete written approvals and acceptance evidence listed in sections 24–26 of the full investigation, including:

1. legal/product approval for LGPL Android app and SDK distribution;
2. security approval for exact source, fail-closed PACE/BAC selection, logging containment, provider use, PA/CA scope, and threat model;
3. privacy approval for identity, portrait, access-key, APDU, and memory lifecycle handling;
4. architecture and Android release/device validation;
5. a governed, signed, offline CSCA/CRL trust-store design before any Passive Authentication trust claim;
6. exact pins, checksums, dependency locking, SBOM/notices, transitive/manifest review, and ongoing vulnerability/release monitoring.

If any required approval or validation fails, Atlas keeps the transport-only fallback.

## Hard stop

M7.1 investigation is complete. Passport protocol implementation has NOT started. Milestone 8 has NOT started.

**STOP — waiting for human approval before adding any ePassport dependency or implementing BAC/PACE, secure messaging, DG1/DG2, Passive Authentication, or Chip Authentication.**
