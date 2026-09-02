# ADR 0005: Minimize and explicitly clean temporary sensitive storage

- Status: Accepted
- Date: 2026-08-02

## Context

Document images, MRZ/NFC content, portraits, selfies, and biometric material are highly sensitive. Mobile runtimes and lifecycle interruptions make accidental persistence and incomplete cleanup credible risks.

## Decision

Prefer owned in-memory processing. If persistence is unavoidable, use application-private storage with Keystore-backed encryption, explicit expiry, and cleanup on every terminal path. Never use public storage or put sensitive content in logs, errors, analytics, saved UI state, or navigation arguments.

## Consequences

Implementations require lifecycle-aware cleanup and conservative zeroization claims. `SensitiveValue` establishes only an in-memory contract in Milestone 1; encrypted persistence, cleanup orchestration, and verification belong to Milestone 9.
