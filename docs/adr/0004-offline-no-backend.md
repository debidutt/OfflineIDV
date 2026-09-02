# ADR 0004: Make runtime verification backend-independent

- Status: Accepted
- Date: 2026-08-02

## Context

The conference flow must visibly work in airplane mode, and identity data must not leave the active device. A hidden runtime dependency would undermine both reliability and privacy.

## Decision

All capture, OCR, MRZ validation, chip reading, face-comparison abstraction, evidence aggregation, and decision behavior run locally. The Android app requests no internet permission. No cloud API, remote account, remote analytics upload, or on-demand model download is allowed.

## Consequences

The demo remains reliable without connectivity and has a smaller privacy boundary. Application size, device capability, local model governance, and offline certificate freshness become product constraints. Build-time dependency resolution still requires network access.
