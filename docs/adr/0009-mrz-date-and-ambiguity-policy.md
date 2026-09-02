# ADR 0009: Make MRZ century and OCR ambiguity explicit

- Status: Accepted
- Date: 2026-08-02

## Context

TD3 dates contain two-digit years, and OCR can confuse visually similar letters and digits. A parser that silently chooses a century or rewrites characters can convert uncertain evidence into apparently certain identity data.

## Decision

Interpret dates only relative to a caller-supplied reference date. Birth dates use a configurable 120-year non-future window; expiry dates use a configurable window from ten years before through twenty years after the reference date. Keep a deterministic preferred date while retaining alternative candidates and an `AMBIGUOUS_CENTURY` issue whenever policy does not produce one unique interpretation.

Limit character correction to `O` → `0` and `I` → `1` candidates in numeric birth and expiry fields. Record every selected candidate as `MrzAmbiguity`, reduce confidence, and never repair alphabetic fields or check-digit positions.

## Consequences

Parsing and tests are deterministic without a system clock. Callers can use a preferred interpretation without losing uncertainty, and expiry remains evidence rather than structural failure. Product policy must decide how to handle ambiguous evidence in Milestone 3; this milestone does not make that decision.
