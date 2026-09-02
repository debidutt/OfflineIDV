# ADR 0002: Keep platform implementations native

- Status: Accepted
- Date: 2026-08-02

## Context

Camera, OCR, NFC, secure storage, UI, lifecycle, and accessibility APIs differ materially between Android and iOS.

## Decision

Align domain vocabulary, error taxonomy, state-machine semantics, evidence, outcomes, and security rules. Implement Android with CameraX, ML Kit adapters, `IsoDep`, Keystore/private storage, Compose, and TalkBack semantics. Implement iOS counterparts with AVFoundation, Vision, CoreNFC, Keychain/file protection, SwiftUI, and VoiceOver.

## Consequences

Each platform can honor native lifecycle and accessibility behavior. Some code is duplicated, so shared synthetic fixtures and behavioral conformance scenarios become important; no Android/iOS framework type may enter a shared conceptual contract.
