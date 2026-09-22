# ADR 0014: Stop ePassport access at the reviewed protocol boundary

## Status

Accepted for Milestone 7. Superseded for the limited M7.3 engineering subset by ADR 0016; it remains the required fallback decision if M7.3 release gates fail.

## Context

Real ePassport reading requires security-sensitive BAC/PACE and secure-messaging code. Atlas forbids custom passport cryptography and requires exact library source, logging, dependency, Android, offline, license, and manifest review before integration. JMRTD is actively maintained and provides the necessary low-level protocols, but its exact 0.8.8 artifacts were unavailable to the restricted build environment. Historical source includes a BAC-failure log containing the access key, and LGPL distribution obligations have not been approved.

## Decision

- Do not package an ePassport protocol or cryptography dependency in Milestone 7.
- Implement the real Android NFC capability, reader-mode lifecycle, `Tag`/`IsoDep` containment, cancellable session coordination, safe error mapping, and opaque artifact/comparison extension points.
- Connect and close `IsoDep` without transmitting passport APDUs, then report `PROTOCOL_UNSUPPORTED`.
- Keep Passive Authentication unavailable/not performed without a governed CSCA trust store and keep Chip Authentication unsupported.
- Never substitute deterministic fakes in Real Android Mode.

## Consequences

The Android integration is hardware-real through the transport boundary but cannot read DG1/DG2 or authenticate a passport. The limitation is visible and testable rather than overstated. Completing real data-group reads requires a later security checkpoint within Milestone 7 readiness remediation; it does not authorize Milestone 8 work.
