package com.ing.offlineidv.verification.model

import java.time.Duration

/** Explicit product policy for interpreting completed verification evidence. */
public data class VerificationPolicy(
    public val requireMrzStructure: Boolean = true,
    public val requireMrzCheckDigits: Boolean = true,
    public val rejectExpiredDocument: Boolean = true,
    public val requireNfcRead: Boolean = true,
    public val requirePrintedChipConsistency: Boolean = true,
    public val requireFaceMatch: Boolean = true,
    public val requirePassiveAuthentication: Boolean = false,
    public val requireChipAuthentication: Boolean = false,
    public val allowRetry: Boolean = true,
) {
    init {
        require(!requirePrintedChipConsistency || requireNfcRead) {
            "Printed/chip consistency cannot be required without an NFC read."
        }
        require(!requirePassiveAuthentication || requireNfcRead) {
            "Passive authentication cannot be required without an NFC read."
        }
        require(!requireChipAuthentication || requirePassiveAuthentication) {
            "Chip authentication cannot be required without passive authentication."
        }
    }
}

/** Retryable user or engine work. Maximum attempts include the initial attempt. */
public enum class RetryableStep {
    DOCUMENT_CAPTURE,
    OCR,
    NFC,
    SELFIE,
}

/** Positive maximum attempt counts for retryable work. */
public data class RetryPolicy(
    public val cameraCaptureAttempts: Int = 2,
    public val ocrAttempts: Int = 2,
    public val nfcAttempts: Int = 3,
    public val selfieAttempts: Int = 2,
) {
    init {
        require(cameraCaptureAttempts > 0) { "cameraCaptureAttempts must be positive" }
        require(ocrAttempts > 0) { "ocrAttempts must be positive" }
        require(nfcAttempts > 0) { "nfcAttempts must be positive" }
        require(selfieAttempts > 0) { "selfieAttempts must be positive" }
    }

    /** Returns the maximum total attempts for [step], including its initial attempt. */
    public fun maximumAttempts(step: RetryableStep): Int =
        when (step) {
            RetryableStep.DOCUMENT_CAPTURE -> cameraCaptureAttempts
            RetryableStep.OCR -> ocrAttempts
            RetryableStep.NFC -> nfcAttempts
            RetryableStep.SELFIE -> selfieAttempts
        }
}

/** Immutable deterministic attempt counts. */
public class RetryCounter private constructor(
    attempts: Map<RetryableStep, Int>,
) {
    private val attempts: Map<RetryableStep, Int> = attempts.toMap()

    /** Returns total attempts already started for [step]. */
    public fun attemptsFor(step: RetryableStep): Int = attempts[step] ?: 0

    /** Returns a new counter after starting one more [step] attempt. */
    public fun recordAttempt(step: RetryableStep): RetryCounter = RetryCounter(attempts + (step to attemptsFor(step) + 1))

    override fun equals(other: Any?): Boolean = other is RetryCounter && attempts == other.attempts

    override fun hashCode(): Int = attempts.hashCode()

    override fun toString(): String = "RetryCounter(attempts=$attempts)"

    public companion object {
        /** Counter with no attempts started. */
        public val EMPTY: RetryCounter = RetryCounter(emptyMap())
    }
}

/** Whether the failed step may be retried. */
public enum class RetryDecision {
    RETRY_AVAILABLE,
    EXHAUSTED,
    DISABLED,
}

/** Safe reason for offering or refusing a retry. */
public enum class RetryReason {
    QUALITY_REJECTED,
    TECHNICAL_FAILURE,
    STEP_TIMEOUT,
    INCONCLUSIVE_COMPARISON,
}

/** External capabilities known before a transition; no engine object is retained. */
public enum class VerificationCapability {
    CAMERA,
    NFC,
    FACE_COMPARISON,
    PASSIVE_AUTHENTICATION,
    CHIP_AUTHENTICATION,
}

/** Immutable capability set supplied through deterministic transition context. */
public class VerificationCapabilities(
    available: Set<VerificationCapability>,
) {
    private val available: Set<VerificationCapability> = available.toSet()

    /** Whether [capability] is available to future effect handlers. */
    public fun isAvailable(capability: VerificationCapability): Boolean = capability in available

    override fun equals(other: Any?): Boolean = other is VerificationCapabilities && available == other.available

    override fun hashCode(): Int = available.hashCode()

    override fun toString(): String = "VerificationCapabilities(available=$available)"

    public companion object {
        /** Capability set used by a fully capable local host. */
        public val ALL: VerificationCapabilities = VerificationCapabilities(VerificationCapability.entries.toSet())
    }
}

/** Explicit timeouts used only to describe scheduling effects; the reducer never reads a clock. */
public data class VerificationTimeoutPolicy(
    public val session: Duration = Duration.ofMinutes(5),
    public val initialization: Duration = Duration.ofSeconds(15),
    public val cameraPreparation: Duration = Duration.ofSeconds(15),
    public val capture: Duration = Duration.ofSeconds(30),
    public val qualityEvaluation: Duration = Duration.ofSeconds(15),
    public val ocr: Duration = Duration.ofSeconds(30),
    public val mrz: Duration = Duration.ofSeconds(15),
    public val nfc: Duration = Duration.ofSeconds(60),
    public val chipValidation: Duration = Duration.ofSeconds(30),
    public val printedChipComparison: Duration = Duration.ofSeconds(15),
    public val selfieCapture: Duration = Duration.ofSeconds(30),
    public val selfieQuality: Duration = Duration.ofSeconds(15),
    public val faceComparison: Duration = Duration.ofSeconds(30),
    public val decision: Duration = Duration.ofSeconds(15),
) {
    init {
        listOf(
            session,
            initialization,
            cameraPreparation,
            capture,
            qualityEvaluation,
            ocr,
            mrz,
            nfc,
            chipValidation,
            printedChipComparison,
            selfieCapture,
            selfieQuality,
            faceComparison,
            decision,
        ).forEach { duration -> require(!duration.isZero && !duration.isNegative) { "Timeouts must be positive." } }
    }

    /** Returns the configured timeout for an asynchronous [step]. */
    public fun durationFor(step: VerificationStep): Duration =
        when (step) {
            VerificationStep.SESSION -> session
            VerificationStep.INITIALIZATION -> initialization
            VerificationStep.CAMERA_PERMISSION -> cameraPreparation
            VerificationStep.CAMERA_PREPARATION -> cameraPreparation
            VerificationStep.DOCUMENT_CAPTURE -> capture
            VerificationStep.DOCUMENT_QUALITY -> qualityEvaluation
            VerificationStep.OCR -> ocr
            VerificationStep.MRZ -> mrz
            VerificationStep.NFC_READ -> nfc
            VerificationStep.CHIP_VALIDATION -> chipValidation
            VerificationStep.PRINTED_CHIP_COMPARISON -> printedChipComparison
            VerificationStep.SELFIE_CAPTURE -> selfieCapture
            VerificationStep.SELFIE_QUALITY -> selfieQuality
            VerificationStep.FACE_COMPARISON -> faceComparison
            VerificationStep.DECISION -> decision
        }
}

/** Complete immutable context consumed by each reducer call. */
public data class VerificationContext(
    public val policy: VerificationPolicy = VerificationPolicy(),
    public val retryPolicy: RetryPolicy = RetryPolicy(),
    public val capabilities: VerificationCapabilities = VerificationCapabilities.ALL,
    public val timeoutPolicy: VerificationTimeoutPolicy = VerificationTimeoutPolicy(),
)
