# TD3 MRZ engine

## Scope and responsibility

The `android:mrz` module implements deterministic, Android-framework-free Kotlin for ICAO TD3 passport machine-readable zones. It accepts candidate text from a future OCR boundary, normalizes only well-defined OCR presentation artifacts, parses the fixed layout, interprets dates relative to a caller-supplied reference date, and reports structure and checksum consistency as evidence.

MRZ validation does not establish document authenticity, chip authenticity, holder identity, or face similarity. It does not perform OCR, camera capture, NFC, orchestration, persistence, networking, logging, or UI work.

## TD3 layout

Indexes below are zero-based and end-exclusive.

| Line | Range | Width | Field |
| --- | --- | ---: | --- |
| 1 | `0..2` | 2 | Document code |
| 1 | `2..5` | 3 | Issuing state or organization |
| 1 | `5..44` | 39 | `SURNAME<<GIVEN<NAMES` |
| 2 | `0..9` | 9 | Document number |
| 2 | `9..10` | 1 | Document-number check digit |
| 2 | `10..13` | 3 | Nationality |
| 2 | `13..19` | 6 | Birth date (`YYMMDD`) |
| 2 | `19..20` | 1 | Birth-date check digit |
| 2 | `20..21` | 1 | Sex marker (`M`, `F`, or `<`) |
| 2 | `21..27` | 6 | Expiry date (`YYMMDD`) |
| 2 | `27..28` | 1 | Expiry-date check digit |
| 2 | `28..42` | 14 | Optional/personal data |
| 2 | `42..43` | 1 | Optional-data check digit |
| 2 | `43..44` | 1 | Composite check digit |

Only `A-Z`, `0-9`, and `<` are valid after normalization. TD3 input must resolve to exactly two 44-character lines.

## Normalization policy

Normalization is intentionally conservative:

1. Convert CRLF and CR line endings to LF.
2. Trim whitespace surrounding the complete input and each physical line.
3. Remove only ASCII spaces inside a candidate line. Tabs and other characters inside a line are rejected.
4. Convert ASCII `a-z` to `A-Z` without locale-sensitive rules.
5. Preserve every filler `<`.
6. Accept exactly two complete lines, or one exactly 88-character sequence that can be split at character 44.
7. Reject unsupported characters, extra lines, and incomplete or overlong lines. No arbitrary character is deleted.

Case folding, removal of OCR-added ASCII spaces, and safe one-line splitting are returned as non-sensitive normalization metadata. Normalized lines are held only inside the module and are never included in `toString()`, errors, or validation diagnostics.

## OCR ambiguity policy

Character correction is field-aware and never silent:

- `O` may produce candidate `0`, and `I` may produce candidate `1`, only in the six numeric birth/expiry date characters.
- Document numbers remain alphanumeric, so `O` and `I` are not changed there.
- Alphabetic document-code, issuing-state, nationality, and name fields never convert digits into letters.
- Check-digit positions must contain their standard character directly. Mandatory digits are not repaired; the optional-data digit may be `<` only when the optional field is empty.
- Every selected numeric candidate records its field, offset, observed character, selected character, and candidate set in `MrzAmbiguity`.
- Any recorded ambiguity reduces confidence to `AMBIGUOUS`, even when checksums happen to agree.

The parsed model exposes a preferred interpretation for practical callers but retains ambiguity metadata so it cannot be presented as certain.

## Check digits

Character values are `0` through `9` for digits, `10` through `35` for `A` through `Z`, and `0` for `<`. Values are multiplied by repeating weights `7, 3, 1`; the sum modulo 10 is the check digit.

The engine validates document number, birth date, expiry date, optional data when present, and the composite digit. For filler-only optional data, `<` means not applicable; numeric `0` is also accepted as the calculated checksum.

The composite source is constructed exactly as:

```text
document number + its digit
+ birth date + its digit
+ expiry date + its digit
+ optional data + its digit
```

This corresponds to line-2 slices `0..10`, `13..20`, `21..28`, and `28..43`.

## Name policy

The 39-character name field is divided at its first `<<`. Single fillers inside each component become presentation spaces and repeated/edge spaces are removed. Components keep their MRZ uppercase spelling; the engine performs no title casing, transliteration, or locale-sensitive rewriting. Missing given names are valid when the separator is present. A missing separator is reported as a structural issue.

## Date-century policy

All date operations require an explicit `LocalDate` reference. Parsing never reads the system clock.

Birth dates use an inclusive rolling window from `referenceDate - 120 years` through `referenceDate`. Candidate years must match `YY`, form a valid calendar date, and not be in the future. The latest candidate is preferred. If two candidates remain in the window, both are retained and `AMBIGUOUS_CENTURY` is reported. The maximum age is policy-configurable for explicit product constraints; a valid future candidate with no permitted non-future candidate is reported as `FUTURE_BIRTH_DATE`, never resolved as a birth date.

Expiry dates use an inclusive window from `referenceDate - 10 years` through `referenceDate + 20 years`. A single candidate is resolved. If no unique candidate lies in that window, adjacent-century candidates are retained with a preferred closest date and `AMBIGUOUS_CENTURY`; expiry status is `UNKNOWN`. The window is policy-configurable but deterministic.

Calendar-format failures and impossible dates are separate outcomes. A resolved expiry is `EXPIRED`, `EXPIRES_TODAY`, or `VALID`. Expiration is evidence only: an expired but structurally correct MRZ can still have valid check digits.

## Validation and safe errors

`MrzValidationResult` aggregates structural issues, individual `CheckDigitResult` values, ambiguities, normalization changes, expiry status, and confidence. A Boolean helper may summarize format/checksum consistency, but callers retain the complete evidence.

Fatal normalization failures return `MrzParseResult.Rejected`. Structurally readable input returns `Parsed` even when semantic fields, dates, or check digits are invalid so callers can inspect safe issue codes. `MrzParseError` and validation results map to the existing `IdvError.Mrz` reasons without including raw data.

`Td3PassportMrz`, `MrzName`, `MrzDate`, normalization results, parse results, and validation results use explicitly redacted string representations. Raw MRZ lines, names, document numbers, dates, state/nationality codes, and optional data are never rendered by those objects.

## Known limitations and non-goals

- TD1, TD2, visas, residence permits, non-passport TD3 variants, and extended document-number conventions are unsupported.
- Issuing state and nationality are syntactically validated as three uppercase letters; no external code-list lookup is performed.
- OCR correction is limited to `O/0` and `I/1` in numeric date fields.
- Century interpretation is policy-based because TD3 stores only two year digits.
- MRZ consistency is not authenticity, passive authentication, chip authentication, liveness, or identity proof.
- The module does not retain original OCR text or normalized complete lines after parsing.
