# ADR 0006: Inject synthetic engines for Demo Mode

- Status: Accepted
- Date: 2026-08-02

## Context

A live passport, NFC positioning, image conditions, and biometric input are unreliable conference dependencies. Scattered demo conditionals could accidentally bypass production behavior.

## Decision

Demo Mode is explicitly selected through configuration and supplied through the same camera, OCR, NFC, and face contracts as real adapters. Synthetic fixtures depict no real private person and cover success plus required failure scenarios. Production bindings must not silently resolve demo engines.

## Consequences

Demonstrations and integration tests are deterministic in airplane mode. Composition-root safeguards and visible Demo Mode labeling are mandatory. Milestone 1 defines configuration scenarios only; fake engines begin in Milestone 4.
