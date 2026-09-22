package com.ing.offlineidv.verification.model

/** Finite, non-sensitive evidence retained independently from the final outcome. */
public enum class VerificationEvidence {
    MRZ_STRUCTURE_VALID,
    MRZ_STRUCTURE_INVALID,
    MRZ_CHECK_DIGITS_VALID,
    MRZ_CHECK_DIGITS_INVALID,
    MRZ_CHARACTER_AMBIGUITY,
    MRZ_CENTURY_AMBIGUITY,
    DOCUMENT_EXPIRED,
    DOCUMENT_EXPIRY_UNKNOWN,
    NFC_CHIP_READ,
    NFC_READ_FAILED,
    DG1_AVAILABLE,
    DG2_AVAILABLE,
    PRINTED_CHIP_DATA_MATCH,
    PRINTED_CHIP_DATA_MISMATCH,
    PASSIVE_AUTHENTICATION_VALID,
    PASSIVE_AUTHENTICATION_FAILED,
    PASSIVE_AUTHENTICATION_NOT_PERFORMED,
    CHIP_AUTHENTICATION_SUCCEEDED,
    CHIP_AUTHENTICATION_FAILED,
    CHIP_AUTHENTICATION_NOT_PERFORMED,
    SELFIE_QUALITY_ACCEPTED,
    SELFIE_QUALITY_REJECTED,
    FACE_MATCH_ACCEPTED,
    FACE_MATCH_REJECTED,
    FACE_MATCH_INCONCLUSIVE,
    STEP_RETRIED,
    REQUIRED_STEP_SKIPPED,
    CAPABILITY_UNAVAILABLE,
}

/** Final product outcome; evidence remains available separately in the terminal summary. */
public enum class VerificationOutcome {
    VERIFIED,
    REJECTED,
    INCONCLUSIVE,
    TECHNICAL_FAILURE,
    CANCELLED,
    EXPIRED,
}

/** Safe reason for a non-success terminal outcome. */
public enum class VerificationTerminalReason {
    POLICY_REJECTED,
    INSUFFICIENT_EVIDENCE,
    REQUIRED_CAPABILITY_UNAVAILABLE,
    INITIALIZATION_FAILED,
    CAMERA_PERMISSION_DENIED,
    STEP_TIMEOUT,
    RETRY_EXHAUSTED,
    TECHNICAL_FAILURE,
    DECISION_FAILED,
    HOST_CANCELLED,
    SESSION_EXPIRED,
}

/** Signed LDS-data observation reported by the NFC boundary. */
public enum class PassiveAuthenticationStatus {
    VALID,
    FAILED,
    NOT_PERFORMED,
    UNAVAILABLE,
    UNSUPPORTED,
    TECHNICAL_ERROR,
}

/** Fresh chip-key possession evidence, separate from signed-data authenticity. */
public enum class ChipAuthenticationStatus {
    SUCCEEDED,
    AUTHENTICATION_FAILED,
    NOT_PERFORMED,
    PREREQUISITE_MISSING,
    UNSUPPORTED,
    SECURE_MESSAGING_FAILED,
    TECHNICAL_ERROR,
}

/** Printed-versus-chip comparison without either compared value. */
public enum class PrintedChipComparisonStatus {
    MATCH,
    MISMATCH,
    INCONCLUSIVE,
}

/** Face-comparison result without a score, image, or biometric template. */
public enum class FaceComparisonStatus {
    ACCEPTED,
    REJECTED,
    INCONCLUSIVE,
}
