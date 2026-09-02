# Project Atlas implementation guidance

## Purpose and scope

Project Atlas is an Android-first, offline identity-verification prototype with native Android and iOS implementations aligned through shared architecture and contracts. Work only within the active milestone in `docs/implementation-plan.md`; do not pull later-milestone implementation forward.

## Architecture rules

- Keep dependency direction `app-demo -> ui -> verification -> feature contracts -> core`.
- Keep domain contracts free of Android framework types. Platform APIs stay behind module interfaces.
- UI code must never instantiate CameraX, ML Kit, `IsoDep`, biometric, analytics, or storage implementations directly.
- Use constructor injection at boundaries. Hilt may be introduced with the first Android composition root that needs it.
- Keep MRZ parsing pure Kotlin and deterministic when Milestone 2 begins.
- Model orchestration as an explicit deterministic state machine when Milestone 3 begins.
- Keep verification evidence separate from the final product outcome.

## Security and privacy

- Treat document images, MRZ/NFC data, portraits, selfies, biometric material, and identifiers as sensitive.
- Never log or interpolate sensitive values. Diagnostics must use stable codes and safe, predefined descriptions.
- Prefer memory; never use public storage. Temporary persistence must be private, encrypted, expiring, and explicitly cleaned.
- Do not add custom cryptography, remote services, production biometric claims, or real identity fixtures.
- Demo engines must be explicit, injected, synthetic, and impossible to enable silently in production configuration.

## Module ownership

- `core`: shared configuration, session, result, error, signal, clock, and sensitive-data contracts.
- `camera`, `ocr`, `mrz`, `nfc`, `face`: feature contracts and their platform implementations.
- `verification`: orchestration, deterministic transitions, policy, and decision evidence.
- `storage`: private temporary persistence, expiry, and cleanup.
- `analytics`: non-sensitive local-only events.
- `accessibility`: shared accessibility behavior and guidance.
- `ui`: Compose screens and presentation state.
- `app-demo`: Android composition root and demo packaging only.

## Testing and definition of done

- Add deterministic tests with synthetic data for every behavior change and every error/illegal path.
- Run `./gradlew spotlessCheck`, `./gradlew test`, `./gradlew lint`, and
  `./gradlew assembleDebug` as separate invocations before completing a milestone.
- Run `./gradlew :android:app-demo:assembleDebug` as the demo-app assembly check.
- Fix failures caused by the active change; do not suppress warnings without a documented reason.
- A milestone is done only when its scope, tests, documentation, build, lint, and formatting checks pass and remaining risks are reported.

## Working conventions

- Inspect existing patterns and local instructions before editing.
- Use immutable models and sealed types for finite domains; keep functions focused and cancellation cooperative.
- Add KDoc to public SDK contracts and explain security boundaries.
- Avoid version churn and justify every dependency.
- Do not delete substantial behavior, rename public packages, change the namespace/build system/history, or add paid/network/cryptographic dependencies without approval.
