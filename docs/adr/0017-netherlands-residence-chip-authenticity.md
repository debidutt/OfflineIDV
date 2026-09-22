# ADR 0017: Add bounded Netherlands residence-permit chip authenticity

## Status

Accepted for the requester-approved Milestone 7.4 engineering implementation on 2026-09-22. This decision does not approve distribution or a production identity-verification claim.

## Context

ADR 0016 deliberately stopped after protected access, bounded DG1 reading, and printed/chip consistency. Those checks do not establish that LDS data was signed by a covered issuer and do not establish possession of the private key associated with an authenticated chip public key. The Netherlands residence-permit flow needs those two properties as separate evidence before it can complete successfully.

The application is offline-first and may not download trust material at runtime. The available official Netherlands PKD export contains a dedicated residence-permit CSCA certificate. A complete production trust program, current revocation inputs, broad issuer coverage, and representative-device evidence are still unavailable.

## Decision

- Keep all JMRTD, Bouncy Castle, SOD, DG14, certificate, and Chip Authentication types inside `android:nfc`.
- Read EF.SOD under a 1 MiB limit and verify the DG1 hash, SOD signature, signer identifier, reviewed algorithms/key sizes, DSC validity, and direct DSC signature under one fingerprint-pinned Netherlands residence-permit CSCA.
- Bundle only Netherlands residence-permit CSCA serial 4 from the official Netherlands PKD export. Pin SHA-256 `0F:D7:E3:BE:92:3B:DB:E8:3B:4E:4B:08:E2:59:74:65:5B:5E:55:F6:61:44:8B:9A:FA:A4:A7:8F:76:0E:D2:BC` and fail closed outside the snapshot window 2026-09-22 inclusive through 2027-01-22 exclusive.
- Treat `VALID` as a bounded prototype observation: the read DGs match the signed hashes, the SOD signature is valid, and the DSC is directly signed by the pinned current CSCA. It does **not** mean revocation was checked.
- Do not perform runtime CSCA, Master List, CRL, OCSP, LDAP, HTTP, or other network retrieval. Revocation is not evaluated because an official CRL could not be acquired in the implementation environment; this remains a distribution blocker.
- After valid Passive Authentication, read EF.DG14 under a 64 KiB limit, verify its SOD hash, select exactly one matching ECDH key, and invoke JMRTD Chip Authentication version 1 with AES-CBC-CMAC 256, 192, or 128, strongest first. Reject DH, 3DES, version 2, weak EC keys, unknown suites, mismatched key identifiers, and ambiguity.
- Report signed-data authenticity and fresh chip-key possession as separate finite observations and verification evidence. Require both only in the Netherlands residence-permit Real Android profile.
- Do not read DG2 or implement Active Authentication, Terminal Authentication, face matching, liveness, custom cryptographic primitives, persistence, or network services.

## Consequences

The residence-permit policy now rejects an explicit signature/trust or Chip Authentication failure and returns inconclusive when required proof is missing, stale, uncovered, unsupported, or technically unavailable. A successful live-chip proof supports clone resistance only for the authenticated chip key bound through signed DG14; it is not holder identity, liveness, or a complete document-validity claim.

Coverage is intentionally narrow. A card chaining to a different or rolled-over CSCA, using Chip Authentication version 2, using another suite/key type, or presented after snapshot staleness fails closed. Physical execution against the requester's permit, complete trust/revocation governance, legal/open-source review, dependency locking/verification, fuzzing, and release-build/device gates remain mandatory. Milestone 8 is outside this decision and has not started.
