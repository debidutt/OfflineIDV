# ADR 0013: Bundle OCR and keep camera artifacts session-owned

## Status

Accepted for Milestone 6.

## Context

The real Android path must show CameraX preview, capture a passport, run OCR in airplane mode, and preserve the existing reducer/effect contracts. CameraX and ML Kit are asynchronous and use Android-owned image objects. Passing those objects into state/UI would break platform isolation and sensitive-data ownership. ML Kit offers bundled and dynamically downloaded model options.

## Decision

- Pin CameraX 1.5.3, the compatible stable patch for the repository's reviewed compileSdk 35 baseline. A compileSdk migration is separate maintenance work.
- Bind one CameraX adapter to process lifecycle and attach/detach the Activity-owned `PreviewView` surface through the app composition root.
- Copy in-memory capture output into a single-session camera store, close every `ImageProxy`, expose only opaque tokens, overwrite owned byte arrays on cleanup, and use no files or public storage.
- Add asynchronous sibling contracts and cooperative cancellation handles without changing the synchronous fake contracts.
- Use bundled ML Kit Latin Text Recognition 16.0.1 so recognition is immediately available offline and never depends on a first-use model download.
- Remove the dependency's optional `INTERNET` and `ACCESS_NETWORK_STATE` manifest contributions and audit the merged manifest, not only Atlas's source manifest.
- Keep OCR text in a protected artifact, use a pure candidate extractor only for ranking likely TD3 pairs, and delegate validity to the existing parser/validator.
- Route all adapter completions through the effect handler with the exact reducer token; adapters contain no policy, retry, state, event dispatch, or outcome logic.

## Consequences

The APK is larger than an unbundled-model build (Google documents roughly 4 MB per bundled script architecture), but conference and airplane-mode behavior do not depend on Play Services download state. Encoded buffers are copied briefly between camera and OCR ownership, increasing peak memory; the copies are bounded to the active session and cleared promptly. JVM strings, bitmap internals, and ML Kit internals cannot guarantee zeroization.

Process lifecycle avoids retaining a destroyed Activity and allows preview surfaces to reattach, but full camera vendor/orientation/recreation behavior still requires device coverage. The basic brightness/edge-energy heuristic is an extension point, not a production quality claim.

No NFC, face, persistence, Hilt, network, or custom cryptography decision is made here.
