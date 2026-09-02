# ADR 0007: Keep the MRZ engine pure Kotlin

- Status: Accepted
- Date: 2026-08-02

## Context

TD3 normalization, parsing, check digits, character correction, and semantic date handling are deterministic domain operations with no need for Android framework APIs.

## Decision

Implement the MRZ engine as Android-framework-free Kotlin with immutable models and synthetic fixtures. OCR produces candidate text at a boundary; the MRZ module owns normalization and ICAO validation. Platform-specific imaging types never enter the parser.

## Consequences

The engine is fast to unit test and its semantics can be ported directly to native Swift. Care is required around ambiguous OCR correction and dates; algorithms and fixtures are explicitly deferred to Milestone 2.
