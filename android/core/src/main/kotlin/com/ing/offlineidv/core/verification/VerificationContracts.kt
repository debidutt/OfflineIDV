package com.ing.offlineidv.core.verification

/** Verification capabilities that a product policy may require. */
public enum class VerificationRequirement {
    MRZ_CHECKSUM,
    NFC_READ,
    MRZ_CHIP_CONSISTENCY,
    FACE_MATCH,
    PASSIVE_AUTHENTICATION,
}

/** Type of offline evidence recorded without carrying the underlying identity data. */
public enum class VerificationSignalType {
    MRZ_CHECKSUM,
    NFC_CHIP_READ,
    MRZ_CHIP_CONSISTENCY,
    FACE_COMPARISON,
    PASSIVE_AUTHENTICATION,
}

/** Safe, finite status for a verification signal. */
public enum class VerificationSignalStatus {
    SATISFIED,
    NOT_SATISFIED,
    NOT_PERFORMED,
    TECHNICAL_FAILURE,
}

/**
 * Non-sensitive evidence metadata used by the future decision engine.
 *
 * Raw MRZ, NFC, image, score, or biometric values must not be added to this contract.
 */
public data class VerificationSignal(
    public val type: VerificationSignalType,
    public val status: VerificationSignalStatus,
)
