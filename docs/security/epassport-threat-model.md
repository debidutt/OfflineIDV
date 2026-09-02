# ePassport threat model (Milestone 7.2)

Status: approval-level threat model for a possible future M7.3. It documents required controls and tests; it does not claim they are implemented.

## 1. Scope, assets, and trust boundaries

Assets include MRZ access material, BAC/PACE/session keys, APDU traffic, DG1 identity fields, DG2 portraits, SOD/DSC/certificate evidence, offline CSCA/CRL data, verification observations, operation tokens, and session artifact references.

Trust boundaries are:

1. passport chip/malicious NFC tag to Atlas `IsoDep` transport;
2. Atlas transport to the proposed JMRTD adapter;
3. untrusted CardAccess/LDS/ASN.1/image/certificate bytes to parsers and decoders;
4. JMRTD/Scuba/Bouncy Castle/EJBCA objects and exceptions to Atlas-owned safe types;
5. protocol artifacts to the session artifact store and future face boundary;
6. offline trust-store build/governance to the signed app bundle and runtime validator;
7. asynchronous Android callbacks to exact-token reducer events; and
8. third-party source/binaries/repositories to the final APK/AAB/SDK release.

The runtime remains offline. The passport, tag, every byte read from it, library exception text, embedded certificate metadata, and image data are untrusted and sensitive.

## 2. Threat register

| Threat | Boundary | Required mitigation | Residual risk | Required test/review |
| --- | --- | --- | --- | --- |
| Malicious NFC tag impersonates a passport | Tag → transport | Require `IsoDep`; select the ICAO applet through JMRTD; accept only allowlisted protocol/LDS structures; no authenticity claim from tag discovery/access. | A tag can emulate enough behavior to consume time or produce plausible untrusted data. | Non-passport `IsoDep`, emulator, wrong applet, status-word, and arbitrary-response corpus; protocol security review. |
| Malformed CardAccess/LDS data | Chip → parser | Bounded streams; strict length/count/nesting checks; fail closed; translate to finite observation; clear partial data. | Parser defects in third-party code remain possible. | Mutation/property/fuzz tests for BER/TLV/LDS, truncated/duplicate/indefinite/overflow lengths; dependency source review. |
| Oversized LDS payload | Chip → memory/artifact store | Per-file and aggregate reservation before allocation; stream reads; no unrequested DGs; hard deadline; `LIMIT_EXCEEDED`. | Library may allocate internally before Atlas sees a length. | Instrument allocations with crafted lengths; memory-pressure/heap tests; inspect JMRTD allocation order. |
| Crafted ASN.1 complexity | LDS/SOD/certificates → Bouncy Castle/JMRTD | Depth/member/count/byte limits, timeout/cancellation, allowlisted algorithms, updated exact provider, no recursive unbounded Atlas parser. | CPU/memory denial or provider/parser vulnerability. | ASN.1 fuzz corpus, deep nesting, huge integers/OIDs/sets, algorithm-confusion cases; SCA and independent crypto review. |
| PACE downgrade to BAC | CardAccess/adapter decision | Compatible PACE means PACE must be attempted; any post-attempt failure blocks BAC; deterministic allowlist; explicit observations. | Policy misconfiguration or new suite classified incorrectly. | Exhaustive downgrade table, branch/spy assertions that BAC is never invoked after PACE attempt; security sign-off. |
| BAC selected when forbidden | Product/security policy → adapter | BAC only when no compatible PACE and explicit context policy allows it; immutable request policy; unknown/malformed state fails closed. | Issuer compatibility pressure may encourage unsafe exceptions. | BAC-allowed/forbidden/absent/unsupported/malformed CardAccess matrix; policy-change review. |
| Unknown access-key/suite fallback | Adapter → JMRTD | Map only explicit key/suite enums; reject unknown OIDs/types/curves/parameters before JMRTD best-effort paths. | Incomplete algorithm registry may deny legitimate documents. | Unknown/future OID, key type, parameter ID, curve, cipher, digest tests; interoperability review. |
| Replay of prior protocol result | Callback → effect handler/reducer | Exact session/operation generation; one active read; atomic artifact commit only while active; stale/duplicate no-op. | In-process bug could associate a stale result before invalidation. | Replay old success/failure after retry, cancel, expiry, and new session; concurrency stress. |
| Cloned chip passes BAC/PACE/read | Protocol evidence → policy | Label access/read separately; require PA and, if policy requires clone resistance, separately approved CA bound to signed DG14. | PA without CA does not establish live-chip key possession; CA coverage varies. | Evidence-semantics review, cloned/emulated test where lawful, CA prerequisite tests. |
| Untrusted or substituted DSC | SOD/DSC → trust validator | Verify SOD signature and DG hashes separately; build DSC path to approved CSCA under immutable policy; ignore chip-supplied roots. | Trust-store gaps and certificate-policy ambiguity. | Wrong root, self-signed DSC, cross-country substitution, broken chain, constraint/validity/algorithm tests; PKI review. |
| Stale CSCA/CRL data | Bundled trust snapshot → PA | Versioned signed snapshot, accepted age policy, expiry monitoring, rollback prevention; report `TRUST_STORE_STALE`, never trusted. | Offline app may remain outdated; revocation freshness is inherently delayed. | Clock/reference-date boundaries, stale/missing CRL, expired app bundle, update/rollback/emergency drills. |
| Missing issuer/country coverage | Trust snapshot → PA | Explicit coverage metadata and `ISSUER_NOT_COVERED`; no default/fallback trust. | Legitimate documents become inconclusive. | Countries/document eras outside bundle, rollover gaps, historical passports; product acceptance review. |
| Malicious portrait encoding/decompression bomb | DG2 → image decoder/artifact store | Validate container/mime/dimensions before full decode; encoded/pixel/decoded/aggregate caps; subsample; allowlist codecs; no UI decode. | Native/platform decoder vulnerabilities or hidden allocation overhead. | Corrupt JPEG/JPEG2000 variants, huge dimensions, bombs, multi-image DG2, OOM pressure; decoder security review. |
| APDU command/response leakage | JMRTD/Scuba/transport → logs/listeners | No APDU listeners; suppress library logging before load; no APDU trace/history; AA disabled; sentinel log-capture tests. | Debugger, OS/vendor layer, or third-party exception may retain bytes. | JUL/logcat/stdout/stderr/crash capture in debug/release; static listener/AA audits. |
| Access-key leakage | MRZ store → adapter/library | Opaque reference; shortest scoped access; no strings where avoidable; never log/interpolate; close/overwrite owned buffers. | JMRTD/JVM may copy immutable values that cannot be zeroized. | Sentinel key across logs, state, heap-lifetime tests, cancellation/cleanup; privacy review. |
| Session-key leakage | JMRTD secure messaging → process | Never expose/copy to Atlas contracts; close wrapper/transport on every path; no object `toString()`. | Provider/library/heap copies survive until GC. | Forced failures at every PACE/BAC/SM stage; heap and log review; library source audit. |
| Exception/context leakage | Library/platform → Atlas error/UI/crash | Catch inside adapter; fresh closed failure without message/cause/class name; no raw throwable upward; crash hook redaction. | Fatal VM/native errors may bypass normal mapping. | Sentinel exception messages/causes/suppressed chains; UI/state/test-report/crash capture; forced parser/provider errors. |
| Debug logging re-enabled | Build/runtime config → logging | Release and debug logger configuration installed before library load; tests assert effective levels/handlers; no remote crash/log SDK. | OEM logging bridges may differ. | Device-vendor logcat capture, build-variant config audit, first-call/class-load race test. |
| cert-cvc direct stack trace | cert-cvc → stderr/logcat | Prefer proven exclusion when TA/CVC is unused; otherwise prove affected methods unreachable and absent after R8. JUL suppression alone is not accepted. | Reflection or future JMRTD changes could restore reachability. | Bytecode/source call graph, forced CVC paths, stderr/logcat capture, R8 artifact inspection; security approval. |
| Memory retention after completion | Adapter/artifact/library → heap | Short lifetimes, mutable owned buffers, explicit close/overwrite, atomic cleanup, no persistence, conservative zeroization claim. | JVM/library/decoder/driver copies cannot be guaranteed erased. | Weak-reference/heap-pressure checks, repeated sessions, terminal/cancel/expiry cleanup assertions; privacy acceptance. |
| Cross-session artifact confusion | Artifact reference → store | Reference includes session, kind, and issuing-owner identity; store rejects equal-looking/foreign/stale refs; clear before replacement. | Programming error at composition boundary. | Two-store/two-session collision, replay, wrong-kind, post-cleanup resolution tests. |
| Late callback mutates completed/new session | Android/worker callback → reducer/store | Cancellation handle, exact token, serialized event queue, active-token check before artifact commit, late result closed and ignored. | Race between callback and cancellation. | Deterministic barriers/stress tests at every commit/close boundary; rotation/background/retry. |
| Tag substitution mid-session | Discovery/transport → protocol | One accepted tag session per operation; close duplicates; no reconnect to a new tag inside an established protocol; operation restart required. | Relay attacks and sophisticated RF substitution are not fully prevented. | Present second tag, remove/replace during PACE/BAC/DG read, relay-oriented security review. |
| Tag removal/unstable RF causes ambiguous downgrade | Transport → adapter | Map loss distinctly; close; no BAC after PACE attempt; reducer decides retry with a new generation. | Poor antenna/document behavior may cause denial. | Removal at each protocol step, antenna locations/cases, repeated retry and timeout. |
| Dependency binary compromise | Maven/repository → build/release | Exact Maven Central restriction, SHA-256 verification, dependency locks, source/commit match, SBOM, signed CI provenance. | Publisher/signing/build-system compromise can affect source and binary together. | Clean-room resolution, independent hashes/source diff, signature identity review, reproducibility assessment. |
| Crypto-provider compromise/misconfiguration | Bouncy Castle → protocol claims | Exact 1.85.2/1.85 lock, advisory monitoring, private provider instance, strict algorithms, no global registration/shading, device algorithm tests. | Undisclosed provider flaw; large attack surface includes unused algorithms/network classes. | Provider-selection/known-answer/curve/MAC/cipher/cert tests; SCA; independent crypto review; R8 inspection. |
| Provider network-capable classes used accidentally | BC certificate/revocation API → network | No `INTERNET` permission; offline trust implementation accepts only bundled inputs; static ban on LDAP/OCSP/URL fetch APIs. | Host app embedding an SDK may have network permission. | Source/bytecode API audit, host app with network permission plus network monitor, airplane-mode test. |
| Certificate parsing attack | SOD/DSC/CSCA/CRL → parser | Size/count/depth/algorithm constraints, exact provider, immutable validated trust bundle, no chip root trust. | Complex X.509/CMS parser attack surface. | Malformed cert/CRL/CMS fuzzing, chain cycles, duplicate/conflicting extensions, huge names/OIDs, advisory review. |
| Weak/deprecated algorithm accepted | Document metadata → crypto provider | Explicit approved algorithm policy by document profile/reference time; unsupported evidence rather than provider default; no best effort. | Older legitimate passports may become inconclusive. | 3DES/RSA size/hash/curve boundary matrix, unknown algorithms, product/security exception process. |
| SOD signature valid but DG hash omitted/not checked | PA evidence → reducer/policy | Per-DG hash map; SOD signature and signer trust separate; no aggregate `AUTHENTIC` flag. | Callers may misinterpret partial evidence. | Partial DG/SOD cases, mapper/UI vocabulary review, policy truth table. |
| Trust-store rollback/substitution | Release/update → runtime | Signed manifest, monotonically increasing version, pinned bundle hash, app-release binding, rollback/emergency procedure, safe stale state. | Device/app rollback and compromised signing infrastructure. | Install/update/rollback matrix, corrupted signature/hash, older version, key rollover drill. |
| Denial of service via slow chip/reads | Chip/transport → scheduler | Existing 60-second outer deadline, per-call remaining time, cancellation closes transport, bounded retries owned by reducer. | Repeated user retries or RF stalls consume battery/time. | Slow/partial responses, never-returning mock, tag loss, cancel latency, retry exhaustion, battery/performance measurement. |
| Denial of service via CPU-heavy parsing/crypto | Parser/provider → worker | Byte/complexity limits, dedicated cancellable worker, elapsed-time checks, no main-thread work, aggregate budget. | Some third-party operations may not be cooperatively cancellable. | Pathological ASN.1/image/key parameters, main-thread/ANR detection, timeout and cleanup latency. |
| Real identity data enters fixtures/artifacts | Test/device → repository/CI | Runtime-only authorized passports; synthetic committed fixtures; no screenshots/dumps/APDU traces; sanitized pass/fail evidence only. | Human error during debugging. | Pre-commit/source/artifact scan, test-run procedure, reviewer inspection, incident deletion process. |
| M8 bypasses NFC security boundary | Portrait consumer → JMRTD/DG2/transport | M8 accepts only `PortraitArtifactRef`; module-dependency and source audits ban JMRTD/Scuba/`IsoDep`/DG2 parsing. | Future convenience changes could erode boundary. | Architecture/dependency tests and code review before M8; no M8 work in M7.2/M7.3. |

## 3. Security properties to prove in M7.3

- A PACE attempt makes BAC unreachable for that operation and retry history cannot change that fact.
- No untrusted length/count/dimension causes allocation before its relevant limit and overflow checks.
- No secret, APDU, identity value, portrait content, certificate identifier, library object, or raw exception reaches logs, state, UI, analytics, crash data, or test output.
- Cancellation, timeout, tag loss, late callbacks, and every terminal path close transport and clear all Atlas-owned artifacts idempotently.
- A reference from another session/store/kind can never resolve.
- PA reports DG hashes, SOD signature, DSC extraction, signer trust, trust freshness, and coverage independently.
- Missing/stale/uncovered/revoked/untrusted material can never become trusted evidence.
- Runtime uses no network even when an embedding host possesses network permission.
- M8 can receive only `PortraitArtifactRef`, never JMRTD/DG2/transport capability.

## 4. Required review gates

The eventual adapter and exact minified release artifact require independent security/crypto, privacy, architecture, Android/release, and PKI review. Fuzz results, memory/cancellation measurements, real-device evidence, SBOM/lock/verification metadata, and accepted residual risks must accompany that review.

This threat model is not a production-security claim. Unimplemented controls remain blockers, and a failed or missing review keeps Atlas at `PROTOCOL_UNSUPPORTED`.
