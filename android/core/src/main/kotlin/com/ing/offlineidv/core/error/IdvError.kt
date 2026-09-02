package com.ing.offlineidv.core.error

/** High-level subsystem in which an identity-verification error occurred. */
public enum class IdvErrorCategory {
    CONFIGURATION,
    SESSION,
    CAMERA,
    OCR,
    MRZ,
    NFC,
    FACE,
    STORAGE,
    VERIFICATION,
    INTERNAL,
}

/** Safe recovery guidance that callers may translate into product behavior. */
public enum class IdvRecovery {
    RETRY,
    REQUEST_PERMISSION,
    RESTART_SESSION,
    CHECK_DEVICE_SETTINGS,
    CONTACT_SUPPORT,
    NONE,
}

/**
 * A predefined, non-sensitive reason descriptor.
 *
 * Implementations must never contain captured input, raw platform exception messages, or identity
 * data. Stable codes are intended for local diagnostics and tests, not remote telemetry.
 */
public sealed interface IdvErrorReason {
    public val stableCode: String
    public val safeDescription: String
    public val recovery: IdvRecovery
}

/** Reasons for rejecting SDK configuration. */
public enum class ConfigurationFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    INVALID_SESSION_TIMEOUT(
        "configuration.invalid_session_timeout",
        "The session timeout is not valid.",
        IdvRecovery.NONE,
    ),
    EMPTY_REQUIREMENTS(
        "configuration.empty_requirements",
        "At least one verification requirement is required.",
        IdvRecovery.NONE,
    ),
    DEMO_MODE_REQUIRED(
        "configuration.demo_mode_required",
        "Synthetic engines require an explicitly selected Demo Mode scenario.",
        IdvRecovery.NONE,
    ),
}

/** Reasons for session lifecycle failures. */
public enum class SessionFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    INVALID_IDENTIFIER(
        "session.invalid_identifier",
        "The session identifier is not valid.",
        IdvRecovery.RESTART_SESSION,
    ),
    INVALID_EXPIRY(
        "session.invalid_expiry",
        "The session expiry is not valid.",
        IdvRecovery.RESTART_SESSION,
    ),
    EXPIRED(
        "session.expired",
        "The verification session has expired.",
        IdvRecovery.RESTART_SESSION,
    ),
}

/** Reasons surfaced by document or selfie camera boundaries. */
public enum class CameraFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    UNAVAILABLE("camera.unavailable", "A required camera is unavailable.", IdvRecovery.NONE),
    PERMISSION_DENIED(
        "camera.permission_denied",
        "Camera permission was not granted.",
        IdvRecovery.REQUEST_PERMISSION,
    ),
    CAPTURE_FAILED("camera.capture_failed", "Image capture failed.", IdvRecovery.RETRY),
    QUALITY_REJECTED(
        "camera.quality_rejected",
        "The captured image did not meet quality requirements.",
        IdvRecovery.RETRY,
    ),
}

/** Reasons surfaced by on-device optical character recognition. */
public enum class OcrFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    ENGINE_UNAVAILABLE(
        "ocr.engine_unavailable",
        "The on-device text recognizer is unavailable.",
        IdvRecovery.NONE,
    ),
    RECOGNITION_FAILED("ocr.recognition_failed", "Text recognition failed.", IdvRecovery.RETRY),
    NO_MRZ_CANDIDATE(
        "ocr.no_mrz_candidate",
        "No machine-readable zone was found.",
        IdvRecovery.RETRY,
    ),
}

/** Reasons surfaced by MRZ parsing and validation. */
public enum class MrzFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    MALFORMED("mrz.malformed", "The machine-readable zone is malformed.", IdvRecovery.RETRY),
    UNSUPPORTED_FORMAT(
        "mrz.unsupported_format",
        "The document format is not supported.",
        IdvRecovery.NONE,
    ),
    CHECKSUM_INVALID(
        "mrz.checksum_invalid",
        "A machine-readable zone checksum is invalid.",
        IdvRecovery.RETRY,
    ),
    DATE_INVALID("mrz.date_invalid", "A document date is invalid.", IdvRecovery.RETRY),
}

/** Reasons surfaced by the ePassport NFC boundary. */
public enum class NfcFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    UNAVAILABLE("nfc.unavailable", "NFC is unavailable on this device.", IdvRecovery.NONE),
    DISABLED("nfc.disabled", "NFC is disabled.", IdvRecovery.CHECK_DEVICE_SETTINGS),
    UNSUPPORTED_TAG("nfc.unsupported_tag", "This NFC tag is not a supported passport chip.", IdvRecovery.RETRY),
    ISO_DEP_UNAVAILABLE("nfc.iso_dep_unavailable", "The passport tag does not support IsoDep.", IdvRecovery.RETRY),
    PROTOCOL_UNSUPPORTED(
        "nfc.protocol_unsupported",
        "The ePassport protocol is not available in this build.",
        IdvRecovery.NONE,
    ),
    CONNECTION_TIMEOUT("nfc.connection_timeout", "The passport connection timed out.", IdvRecovery.RETRY),
    COMMUNICATION_TIMEOUT("nfc.communication_timeout", "Passport communication timed out.", IdvRecovery.RETRY),
    TAG_LOST("nfc.tag_lost", "The passport connection was lost.", IdvRecovery.RETRY),
    TIMEOUT("nfc.timeout", "The passport read timed out.", IdvRecovery.RETRY),
    ACCESS_DENIED("nfc.access_denied", "Passport chip access was denied.", IdvRecovery.RETRY),
    READ_FAILED("nfc.read_failed", "The passport chip could not be read.", IdvRecovery.RETRY),
    TECHNICAL_ERROR("nfc.technical_error", "The NFC operation could not complete.", IdvRecovery.RETRY),
    CHIP_DATA_MISMATCH(
        "nfc.chip_data_mismatch",
        "Printed and chip data do not match.",
        IdvRecovery.NONE,
    ),
}

/** Reasons surfaced by face capture, quality, or comparison boundaries. */
public enum class FaceFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    NO_FACE("face.no_face", "No face was detected.", IdvRecovery.RETRY),
    MULTIPLE_FACES("face.multiple_faces", "Multiple faces were detected.", IdvRecovery.RETRY),
    QUALITY_REJECTED(
        "face.quality_rejected",
        "The face image did not meet quality requirements.",
        IdvRecovery.RETRY,
    ),
    COMPARISON_FAILED(
        "face.comparison_failed",
        "Face comparison could not be completed.",
        IdvRecovery.RETRY,
    ),
}

/** Reasons surfaced by private temporary storage and cleanup boundaries. */
public enum class StorageFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    ENCRYPTION_UNAVAILABLE(
        "storage.encryption_unavailable",
        "Secure temporary storage is unavailable.",
        IdvRecovery.NONE,
    ),
    WRITE_FAILED("storage.write_failed", "Temporary data could not be stored.", IdvRecovery.RETRY),
    READ_FAILED("storage.read_failed", "Temporary data could not be read.", IdvRecovery.RETRY),
    CLEANUP_FAILED(
        "storage.cleanup_failed",
        "Temporary data cleanup could not be confirmed.",
        IdvRecovery.CONTACT_SUPPORT,
    ),
}

/** Reasons surfaced by orchestration and decision boundaries. */
public enum class VerificationFailure(
    override val stableCode: String,
    override val safeDescription: String,
    override val recovery: IdvRecovery,
) : IdvErrorReason {
    ILLEGAL_TRANSITION(
        "verification.illegal_transition",
        "The requested verification action is not valid in the current state.",
        IdvRecovery.NONE,
    ),
    REQUIREMENTS_UNSATISFIED(
        "verification.requirements_unsatisfied",
        "Required verification evidence is incomplete.",
        IdvRecovery.NONE,
    ),
    STEP_TIMEOUT(
        "verification.step_timeout",
        "A verification step timed out.",
        IdvRecovery.RETRY,
    ),
    RETRY_EXHAUSTED(
        "verification.retry_exhausted",
        "The permitted attempts for a verification step are exhausted.",
        IdvRecovery.RESTART_SESSION,
    ),
    REQUIRED_CAPABILITY_UNAVAILABLE(
        "verification.required_capability_unavailable",
        "A required device capability is unavailable.",
        IdvRecovery.NONE,
    ),
    INCONCLUSIVE(
        "verification.inconclusive",
        "The available evidence is insufficient for a verification decision.",
        IdvRecovery.NONE,
    ),
    POLICY_REJECTED(
        "verification.policy_rejected",
        "The completed evidence does not satisfy verification policy.",
        IdvRecovery.NONE,
    ),
    ARTIFACT_REFERENCE_INVALID(
        "verification.artifact_reference_invalid",
        "A verification artifact reference is unknown, stale, or belongs to another session.",
        IdvRecovery.RESTART_SESSION,
    ),
    CANCELLED("verification.cancelled", "Verification was cancelled.", IdvRecovery.NONE),
    TECHNICAL_FAILURE(
        "verification.technical_failure",
        "Verification could not be completed.",
        IdvRecovery.RESTART_SESSION,
    ),
}

/**
 * Structured SDK error that cannot carry arbitrary sensitive diagnostics.
 *
 * Platform adapters should map exceptions to one of these predefined reasons at their boundary and
 * keep the original exception in a strictly local, non-sensitive debugging path when needed.
 */
public sealed class IdvError protected constructor(
    public val category: IdvErrorCategory,
    public val reason: IdvErrorReason,
) {
    public val code: String = reason.stableCode
    public val safeDescription: String = reason.safeDescription
    public val recovery: IdvRecovery = reason.recovery

    /** Configuration error with no captured configuration value. */
    public class Configuration(
        reason: ConfigurationFailure,
    ) : IdvError(IdvErrorCategory.CONFIGURATION, reason)

    /** Session lifecycle error with no captured identifier. */
    public class Session(
        reason: SessionFailure,
    ) : IdvError(IdvErrorCategory.SESSION, reason)

    /** Camera boundary error with no captured image or platform exception. */
    public class Camera(
        reason: CameraFailure,
    ) : IdvError(IdvErrorCategory.CAMERA, reason)

    /** OCR boundary error with no captured recognized text. */
    public class Ocr(
        reason: OcrFailure,
    ) : IdvError(IdvErrorCategory.OCR, reason)

    /** MRZ boundary error with no captured MRZ content. */
    public class Mrz(
        reason: MrzFailure,
    ) : IdvError(IdvErrorCategory.MRZ, reason)

    /** NFC boundary error with no captured APDU or data-group content. */
    public class Nfc(
        reason: NfcFailure,
    ) : IdvError(IdvErrorCategory.NFC, reason)

    /** Face boundary error with no captured image, template, or score. */
    public class Face(
        reason: FaceFailure,
    ) : IdvError(IdvErrorCategory.FACE, reason)

    /** Storage boundary error with no captured key, path, or payload. */
    public class Storage(
        reason: StorageFailure,
    ) : IdvError(IdvErrorCategory.STORAGE, reason)

    /** Orchestration error with no captured event payload. */
    public class Verification(
        reason: VerificationFailure,
    ) : IdvError(IdvErrorCategory.VERIFICATION, reason)

    /** Unclassified safe failure used only after a boundary has removed sensitive context. */
    public data object Internal : IdvError(
        IdvErrorCategory.INTERNAL,
        InternalFailure,
    )

    final override fun toString(): String = "IdvError(category=$category, code=$code, recovery=$recovery)"

    private data object InternalFailure : IdvErrorReason {
        override val stableCode: String = "internal.unexpected"
        override val safeDescription: String = "An unexpected local error occurred."
        override val recovery: IdvRecovery = IdvRecovery.RESTART_SESSION
    }
}
