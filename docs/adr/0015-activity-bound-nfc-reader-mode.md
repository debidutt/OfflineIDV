# ADR 0015: Bind NFC reader mode to a pending operation and resumed host

## Status

Accepted for Milestone 7.

## Context

Android NFC discovery is Activity-scoped, while verification operations are ViewModel/session-scoped and tokenized. Retaining an Activity in verification, exposing `Tag`/`IsoDep` to UI, or leaving reader mode enabled outside the NFC step would violate lifecycle, dependency, and privacy boundaries.

## Decision

- Keep the Activity attachment in the Android NFC adapter owned by the app composition root.
- Enable reader mode only while a read is pending and the Activity is resumed; disable it on pause, cancellation, completion, or cleanup.
- Keep `Tag` and `IsoDep` inside the NFC platform package.
- Serialize one accepted tag session per read, close duplicates and stale sessions, and suppress late completion after cancellation.
- Echo the reducer operation token only from `RealVerificationEffectHandler`; the NFC feature never dispatches a verification event or reads reducer state.

## Consequences

Activity recreation can safely reattach to a pending ViewModel-owned read. Transport cleanup is deterministic and UI/domain models remain platform-free. Representative-device validation is still required for OEM reader-mode behavior, tag positioning, removal, and background/foreground transitions.
