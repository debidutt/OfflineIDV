# ePassport real-device and passport validation matrix (Milestone 7.2)

Status: planned validation for a future, separately approved M7.3. No row in this document was executed by M7.2. Real identity data must never be committed.

## 1. Entry gates and data-handling rules

M6 camera/OCR and M7 NFC transport debt should be executed **before M7.3 implementation as blocking entry gates**. Protocol debugging must not begin on an unvalidated camera/artifact/NFC lifecycle foundation.

Use only organization-authorized, runtime-presented test passports or approved eMRTD test cards. Never copy MRZ, document number, name, birth/expiry date, nationality, portrait, DG, SOD, certificate subject/serial, APDU, key, screenshot, logcat, heap dump, or video into source control, CI artifacts, issue trackers, reports, or chat. Record only device slot, anonymized document profile code, safe observation enums, timing/memory aggregates, and pass/fail/defect references that contain no identity data.

Before each run, verify log suppression and capture redaction. After each run, trigger terminal cleanup, close NFC/camera resources, delete local diagnostic artifacts, and confirm the next session cannot resolve prior references.

## 2. Device fleet

Final named models should be selected from the supported market/device policy at M7.3 start. At minimum the fleet must fill these slots:

| Slot | Required coverage | Rationale | Status |
| --- | --- | --- | --- |
| D1 | Google/Pixel-family device on current supported Android/API | Reference Android NFC/Camera behavior and current OS. | NOT EXECUTED |
| D2 | Samsung flagship or common mid-range device | High-volume vendor, distinct lifecycle/NFC stack and antenna placement. | NOT EXECUTED |
| D3 | Motorola/Lenovo or equivalent mainstream vendor | Additional NFC controller/camera implementation. | NOT EXECUTED |
| D4 | Xiaomi/Redmi, Oppo/OnePlus, or equivalent supported-market vendor | Aggressive lifecycle/background policies and different NFC hardware. | NOT EXECUTED |
| D5 | Oldest supported Android/API 26-class device with NFC, if product still supports it | Validate minSdk/runtime/provider constraints. | NOT EXECUTED |
| D6 | Intermediate Android release (for example API 30/31) | Catch behavior changes between min and current. | NOT EXECUTED |
| D7 | Target SDK/API 35 device | Exact Atlas target baseline. | NOT EXECUTED |
| D8 | Current shipping Android version at M7.3 release review | Forward/current compatibility; exact version chosen then. | NOT EXECUTED |
| D9 | Low-memory supported device | Bounded DG2 decode, process pressure, cancellation, cleanup. | NOT EXECUTED |
| D10 | Tablet/foldable only if in product scope | Rotation/resizing/antenna/lifecycle differences. | NOT EXECUTED / SCOPE DECISION |

At least three distinct NFC controller/vendor combinations and three physical antenna locations (top, center, rear/side or device-specific positions) are required. Test with and without a protective phone case and with the passport open/closed as issuer/device guidance allows.

## 3. M6 camera/OCR debt — entry gate

| ID | Scenario | Procedure/observation | Pass criterion | Status |
| --- | --- | --- | --- | --- |
| M6-01 | CameraX capture | Real Mode permission → preview → still capture using synthetic/non-identity TD3 test page. | Capture succeeds behind contract; no UI/raw-state leak; artifact cleared terminally. | NOT EXECUTED |
| M6-02 | Rotation | Rotate before preview, during preview, and after capture. | Correct orientation/crop behavior; no duplicate operation, crash, stale callback, or retained Activity. | NOT EXECUTED |
| M6-03 | Permission grant | Start with permission absent and grant on reducer request. | Reducer/effect sequence resumes exactly once. | NOT EXECUTED |
| M6-04 | Permission denial and permanent denial | Deny once and with “don't ask again” where available. | Safe finite observation/recovery; no repeated platform prompt loop. | NOT EXECUTED |
| M6-05 | Preview lifecycle | Resume/pause/background/foreground and activity recreation. | Bind/unbind/rebind is safe; no black/stale preview, resource leak, duplicate capture, or policy change. | NOT EXECUTED |
| M6-06 | ML Kit bundled OCR | Recognize approved synthetic TD3 pages under representative focus/light. | Bundled recognizer runs locally and maps only through existing MRZ path. | NOT EXECUTED |
| M6-07 | Airplane-mode OCR | Cold launch and run capture/OCR with airplane mode before process start. | No model download/network request; result behavior matches online-disabled baseline. | NOT EXECUTED |
| M6-08 | Cancellation/terminal cleanup | Cancel at preview, capture, and OCR; then start a new session. | Late results ignored; buffers/artifacts cleared; new session isolated. | NOT EXECUTED |

All M6 rows are blocking unless product/security explicitly remove the affected platform capability from M7.3 scope.

## 4. M7 NFC transport debt — entry gate

| ID | Scenario | Procedure/observation | Pass criterion | Status |
| --- | --- | --- | --- | --- |
| M7-01 | NFC unavailable | Run on a device without NFC or controlled feature absence. | Capability is `UNAVAILABLE`; reader mode is not enabled; reducer/policy remains authoritative. | NOT EXECUTED |
| M7-02 | NFC disabled | Disable NFC before request; enable while app is active and retry. | `DISABLED` is distinct/recoverable; no settings bypass or fake fallback. | NOT EXECUTED |
| M7-03 | Reader mode lifecycle | Request read while resumed, pause/resume, cancel, finish. | Reader mode exists only for resumed host + pending operation and disables on every end path. | NOT EXECUTED |
| M7-04 | IsoDep recognition/connect/close | Present authorized passport/test card. | Android `Tag`/`IsoDep` remains internal; connection closes idempotently; current runtime reports `PROTOCOL_UNSUPPORTED`. | NOT EXECUTED |
| M7-05 | Non-IsoDep/unsupported tag | Present another NFC tag. | Safe unsupported observation; no crash/APDU/protocol attempt. | NOT EXECUTED |
| M7-06 | Tag removal | Remove before connect, after connect, and at current transport stop. | Loss maps safely, transport closes, no late success. | NOT EXECUTED |
| M7-07 | Cancellation | Cancel before tag, during connect, and immediately after callback scheduling. | Reader/transport close promptly; exact token prevents late mutation. | NOT EXECUTED |
| M7-08 | Duplicate/repeated tag | Keep tag in field, tap repeatedly, then present second tag. | One session accepted; duplicates closed/ignored; no cross-tag continuation. | NOT EXECUTED |
| M7-09 | Activity recreation | Rotate/recreate while waiting and while a tag callback is in flight. | Runtime rebinds without retaining old Activity; no duplicate reader/read/result. | NOT EXECUTED |
| M7-10 | Airplane mode | Cold launch with airplane mode and exercise capability/reader/transport. | Identical local transport behavior; no network attempt/permission. | NOT EXECUTED |

Every M7 row is a blocking M7.3 entry gate. `PROTOCOL_UNSUPPORTED` is the expected current result and must not be interpreted as a protocol defect.

## 5. Passport/document corpus

Each profile code is anonymous and contains no issuer/person identifier in reports. Legal, privacy, and security must approve possession/use.

| Profile | Required property | Minimum coverage | Status |
| --- | --- | --- | --- |
| P1 | PACE-capable passport | At least two approved PACE suite/parameter profiles; include ECDH/Brainpool where in target scope. | FUTURE |
| P2 | BAC-only passport | At least one if legally/operationally available; otherwise document accepted coverage gap and product consequence. | FUTURE |
| P3 | Different issuing countries | At least five target-market countries spanning available chip generations and trust coverage. | FUTURE |
| P4 | Different chip generations | Older supported BAC-era and newer PACE-era chips. | FUTURE |
| P5 | DG2 encoding variants | Representative JPEG, JPEG2000, and other explicitly supported encodings/containers. | FUTURE |
| P6 | Large DG2 | Largest safely authorized corpus examples near proposed encoded/decoded limits. | FUTURE |
| P7 | Expired passport | At least one where possession/testing is permitted; exercise reference-time/product semantics without retaining data. | FUTURE |
| P8 | Damaged/read-sensitive passport | Optional only when safe and authorized; test RF/removal/retry without risking the document. | FUTURE / AUTHORIZATION REQUIRED |
| P9 | Unsupported PACE suite | One real/test-card example if available; otherwise approved simulator/test-card evidence plus documented real-world gap. | FUTURE |
| P10 | Master List/trust coverage variants | Trusted, issuer-not-covered, stale-snapshot, rollover/link, and revocation test material; synthetic certificates may cover negative cases. | FUTURE |

No single document may stand in for issuer, suite, DG2, generation, and trust diversity. Record corpus coverage separately from pass rate.

## 6. M7.3 protocol matrix

| ID | Protocol scenario | Required observation/assertion | Device/document coverage | Status |
| --- | --- | --- | --- | --- |
| PR-01 | CardAccess absent + BAC allowed | BAC attempted only by explicit policy; protected channel/read evidence remains non-authenticity. | P2 across at least D1/D2/D3. | FUTURE |
| PR-02 | CardAccess absent + BAC forbidden | No BAC APDU/protocol call; `BAC_FORBIDDEN`. | Test card/mocks + one applicable document if available. | FUTURE |
| PR-03 | CardAccess malformed/oversized | Fail closed; no BAC; safe malformed/limit observation. | Fuzz/test card on D1/D2. | FUTURE |
| PR-04 | Compatible PACE success | Deterministic reviewed-suite selection and `PACE_SUCCEEDED`. | P1 suites across D1–D4. | FUTURE |
| PR-05 | Only unsupported PACE + BAC allowed | No PACE attempt; BAC only under explicit documented policy; observation records unsupported PACE and policy selection. | P9/test card across D1/D2. | FUTURE |
| PR-06 | Only unsupported PACE + BAC forbidden | No BAC; safe unsupported/forbidden observation. | P9/test card. | FUTURE |
| PR-07 | PACE authentication failure | Stop/close; `DOWNGRADE_BLOCKED`; BAC spy count zero. | Test card/fault injection plus device. | FUTURE |
| PR-08 | PACE technical/provider failure | Stop/close; no BAC; safe technical category, no raw exception. | Fault injection on all provider/device classes. | FUTURE |
| PR-09 | PACE timeout/tag loss | Cancel/close; no BAC; no late result/artifact. | P1 on D1–D4, removal at every step. | FUTURE |
| PR-10 | BAC success/failure/timeout | Finite distinct observations; no access/authenticity conflation. | P2/test card on D1–D3. | FUTURE |
| PR-11 | Secure-messaging failure | Stop/close; no data published; safe SM failure. | Fault injection and test card. | FUTURE |
| PR-12 | DG1 read | Only four comparison fields extracted; no raw/identity state/UI/log/persistence. | P1/P2/P3 across D1–D4. | FUTURE |
| PR-13 | DG2 read | Controlled extraction to `PortraitArtifactRef`; size/pixel/memory accounting. | P1/P2/P5/P6 across D1–D4/D9. | FUTURE |
| PR-14 | SOD read | Bounded parse; no certificate/subject/serial logging; artifact discarded after evidence. | P1/P2/P3. | FUTURE |
| PR-15 | PA complete | DG hashes, SOD signature, DSC extraction, trust/freshness/coverage reported separately. | P1/P2/P3/P10. | FUTURE |
| PR-16 | PA partial evidence | Missing DG/hash/DSC/trust/coverage/stale/unsupported each preserved; never aggregate authentic. | Synthetic/test-card plus P10. | FUTURE |
| PR-17 | Malformed/unsupported LDS/SOD/certificate | No crash/hang/OOM/leak; safe malformed/unsupported/limit observation. | Fuzz corpus on D1/D2/D9. | FUTURE |
| PR-18 | Optional CA disabled | No DG14/CA APDU or evidence claim in initial scope. | All release runs unless separately approved. | FUTURE |
| PR-19 | Optional CA approved path | PA-bound DG14, strict suite, success/failure evidence; no whole-authenticity claim. | Only under separate approval and available documents. | FUTURE / SEPARATE APPROVAL |

## 7. Lifecycle, RF, offline, and stress matrix

Run these at PACE/BAC, DG1, DG2, and SOD phases where technically possible.

| ID | Condition | Pass criterion | Status |
| --- | --- | --- | --- |
| LS-01 | NFC antenna locations and positioning | Clear non-sensitive guidance; no repeated sessions/downgrade; acceptable success rate recorded by device slot. | FUTURE |
| LS-02 | NFC disabled/unavailable mid-flow | Safe finite failure/recovery; no fake/network fallback. | FUTURE |
| LS-03 | Screen rotation/activity recreation | Exact operation survives or cancels per design; no duplicate reader/protocol/result/artifact. | FUTURE |
| LS-04 | Background/foreground | Reader mode/transport follow lifecycle; secrets do not enter saved state; late callbacks ignored. | FUTURE |
| LS-05 | Tag removal at each protocol/read step | Prompt close/cancel, no downgrade, no partial artifact. | FUTURE |
| LS-06 | Repeated scan/retry | Monotonic operation generation; old callbacks/results cannot resolve; bounded retries. | FUTURE |
| LS-07 | User/host cancellation | Transport unblocks/closes and artifacts clear within measured budget. | FUTURE |
| LS-08 | Timeout | Existing 60-second outer deadline and internal remaining-time checks stop all work. | FUTURE |
| LS-09 | Airplane mode/cold start | Full approved flow is local; traffic monitor sees no request. | FUTURE |
| LS-10 | Host app has `INTERNET` permission | Atlas/JMRTD/BC trust path still makes no DNS/HTTP/LDAP/OCSP/CRL request. | FUTURE |
| LS-11 | Memory pressure/low-memory callback | No OOM/ANR/corrupt cross-session data; safe cancellation/limit observation and cleanup. | FUTURE |
| LS-12 | 25 sequential sessions per device/profile group | Stable memory/resource counts; every session isolated and cleaned. | FUTURE |
| LS-13 | Two tags/rapid replacement | No tag substitution within a protocol operation; second tag closed/ignored. | FUTURE |
| LS-14 | Process death | No sensitive restoration/persistence; new process starts without prior artifacts. | FUTURE |

## 8. Limits and containment validation

The numeric limits in the protocol proposal are **PROPOSED — REQUIRES DEVICE/CORPUS VALIDATION**.

| ID | Limit/control | Required evidence | Status |
| --- | --- | --- | --- |
| LC-01 | DG1 4 KiB | Corpus maximum/headroom and crafted just-under/at/over cases; allocation occurs only after check. | FUTURE |
| LC-02 | DG2/portrait 4 MiB encoded | Corpus distribution, read/decode time, just-under/at/over, declared/actual mismatch. | FUTURE |
| LC-03 | SOD 1 MiB | Country/certificate/hash corpus distribution plus malformed/oversized CMS. | FUTURE |
| LC-04 | Decoded max 4096 per side, 8 MP/32 MiB | Header-first checks, subsampling, overflow/bomb tests, supported codecs and device memory. | FUTURE |
| LC-05 | Aggregate NFC 48 MiB | Instrumented accounting includes concurrent library/decoder copies and low-memory device headroom. | FUTURE |
| LC-06 | Single APDU response max 64 KiB/device cap; no transcript | Extended/chained reads, device `maxTransceiveLength`, no retained APDU events/history. | FUTURE |
| LC-07 | 60-second outer duration | P1/P2/P5/P6 latency percentiles across fleet; every subcall honors remaining time/cancellation. | FUTURE |
| LC-08 | Logging/exception containment | Sentinel capture across JUL/logcat/stdout/stderr/crash/state/UI/test reports in debug and minified release. | FUTURE |
| LC-09 | cert-cvc | Accepted exclusion/unreachability proof; direct stack-trace paths absent/unreachable in final artifact. | FUTURE |

Security/privacy must approve final thresholds based on recorded corpus percentiles, worst-case memory copies, device headroom, denial-of-service risk, and accepted document-coverage impact. A limit must never be raised only to make one unexplained failing document pass.

## 9. Pass/fail record template

For each test record only:

```text
Matrix ID:
Build SHA-256 / SBOM / dependency-lock ID:
Device slot + model/OS/NFC-controller classification:
Anonymous passport profile code:
Airplane mode / host-network-permission state:
Safe observations:
Elapsed time and peak aggregate memory (non-sensitive):
Cleanup/cross-session result:
PASS / FAIL / BLOCKED:
Sanitized defect reference:
Reviewer/date:
```

Do not attach raw logs. If a failure can be diagnosed only with sensitive APDU/identity output, stop and obtain an approved isolated diagnostic procedure; do not weaken logging rules in a normal build.

## 10. Exit criteria

M7.3-H exits only when:

- every blocking M6/M7/M7.3 row passes or has a written scope removal/residual-risk acceptance by the correct reviewer;
- required vendor/OS/NFC/passport/suite/encoding/trust coverage is met and gaps are explicit;
- no sensitive data was retained or committed;
- release-build offline/logging/permission/dependency evidence matches debug behavior;
- cancellation/timeout/tag-loss/recreation/repetition/low-memory cleanup is deterministic;
- limit values are approved from corpus/device evidence; and
- independent security, privacy, PKI, architecture, Android/release, and product reviewers accept the final report.

A blocked or missing matrix row is not a pass. Until these criteria are met, Atlas keeps the transport-only `PROTOCOL_UNSUPPORTED` behavior.
