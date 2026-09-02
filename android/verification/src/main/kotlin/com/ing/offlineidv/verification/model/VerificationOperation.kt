package com.ing.offlineidv.verification.model

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId

/** Deterministic external operation categories. */
public enum class VerificationStep {
    SESSION,
    INITIALIZATION,
    CAMERA_PERMISSION,
    CAMERA_PREPARATION,
    DOCUMENT_CAPTURE,
    DOCUMENT_QUALITY,
    OCR,
    MRZ,
    NFC_READ,
    CHIP_VALIDATION,
    PRINTED_CHIP_COMPARISON,
    SELFIE_CAPTURE,
    SELFIE_QUALITY,
    FACE_COMPARISON,
    DECISION,
}

/** Deterministic token echoed by effects, completion events, and step timeouts. */
public class VerificationOperationToken internal constructor(
    public val sessionId: IdvSessionId,
    public val step: VerificationStep,
    public val generation: Int,
) {
    init {
        require(generation >= 0) { "generation must not be negative" }
    }

    override fun equals(other: Any?): Boolean =
        other is VerificationOperationToken &&
            sessionId == other.sessionId &&
            step == other.step &&
            generation == other.generation

    override fun hashCode(): Int = 31 * (31 * sessionId.hashCode() + step.hashCode()) + generation

    override fun toString(): String = "VerificationOperationToken(sessionId=$sessionId, step=$step, generation=$generation)"

    internal companion object {
        fun sessionExpiry(sessionId: IdvSessionId): VerificationOperationToken =
            VerificationOperationToken(sessionId, VerificationStep.SESSION, 0)
    }
}

/** Kind of sensitive artifact addressed by an opaque reference. */
public enum class VerificationArtifactKind {
    DOCUMENT_CAPTURE,
    OCR_RESULT,
    MRZ_PRINTED_DATA,
    MRZ_ACCESS_KEY,
    NFC_CHIP_DATA,
    CHIP_PORTRAIT,
    SELFIE_CAPTURE,
}

/**
 * Opaque reference to sensitive data owned outside the state machine.
 *
 * The identifier is available only through scoped [useValue] and never appears in [toString].
 */
public class VerificationArtifactReference private constructor(
    public val kind: VerificationArtifactKind,
    private val value: String,
) {
    /** Supplies the identifier only to a trusted effect-handler boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(value)

    override fun equals(other: Any?): Boolean = other is VerificationArtifactReference && kind == other.kind && value == other.value

    override fun hashCode(): Int = 31 * kind.hashCode() + value.hashCode()

    override fun toString(): String = "VerificationArtifactReference(kind=$kind, value=${Redaction.MARKER})"

    public companion object {
        private val validIdentifier = Regex("[A-Za-z0-9][A-Za-z0-9_-]{7,127}")

        /** Validates an externally generated opaque identifier without rendering it on failure. */
        public fun parse(
            kind: VerificationArtifactKind,
            value: String,
        ): IdvResult<VerificationArtifactReference> =
            if (validIdentifier.matches(value)) {
                IdvResult.Success(VerificationArtifactReference(kind, value))
            } else {
                IdvResult.Failure(com.ing.offlineidv.core.error.IdvError.Internal)
            }
    }
}

/** Immutable references needed to continue or retry the active flow. */
public class VerificationArtifacts private constructor(
    references: Map<VerificationArtifactKind, VerificationArtifactReference>,
) {
    private val references: Map<VerificationArtifactKind, VerificationArtifactReference> = references.toMap()

    /** Returns the opaque reference for [kind], when one has been produced. */
    public fun reference(kind: VerificationArtifactKind): VerificationArtifactReference? = references[kind]

    /** Returns a new collection containing [reference]. */
    public fun plus(reference: VerificationArtifactReference): VerificationArtifacts =
        VerificationArtifacts(references + (reference.kind to reference))

    override fun equals(other: Any?): Boolean = other is VerificationArtifacts && references == other.references

    override fun hashCode(): Int = references.hashCode()

    override fun toString(): String = "VerificationArtifacts(kinds=${references.keys}, values=${Redaction.MARKER})"

    public companion object {
        /** Collection containing no artifact references. */
        public val EMPTY: VerificationArtifacts = VerificationArtifacts(emptyMap())
    }
}

/** Immutable safe progress carried only by active states. */
public class VerificationProgress internal constructor(
    public val sessionId: IdvSessionId,
    evidence: Set<VerificationEvidence> = emptySet(),
    public val retries: RetryCounter = RetryCounter.EMPTY,
    public val artifacts: VerificationArtifacts = VerificationArtifacts.EMPTY,
    public val activeOperation: VerificationOperationToken? = null,
    public val nextGeneration: Int = 1,
    public val sessionExpiryScheduled: Boolean = false,
) {
    public val evidence: Set<VerificationEvidence> = evidence.toSet()
    public val sessionExpiryToken: VerificationOperationToken = VerificationOperationToken.sessionExpiry(sessionId)

    init {
        require(nextGeneration > 0) { "nextGeneration must be positive" }
    }

    internal fun withEvidence(additions: Set<VerificationEvidence>): VerificationProgress = copy(evidence = evidence + additions)

    internal fun withArtifact(reference: VerificationArtifactReference): VerificationProgress = copy(artifacts = artifacts.plus(reference))

    internal fun recordAttempt(step: RetryableStep): VerificationProgress = copy(retries = retries.recordAttempt(step))

    internal fun beginOperation(step: VerificationStep): OperationStart {
        val token = VerificationOperationToken(sessionId, step, nextGeneration)
        return OperationStart(
            progress = copy(activeOperation = token, nextGeneration = nextGeneration + 1),
            token = token,
        )
    }

    internal fun completeOperation(): VerificationProgress = copy(activeOperation = null)

    internal fun markSessionExpiryScheduled(): VerificationProgress = copy(sessionExpiryScheduled = true)

    private fun copy(
        evidence: Set<VerificationEvidence> = this.evidence,
        retries: RetryCounter = this.retries,
        artifacts: VerificationArtifacts = this.artifacts,
        activeOperation: VerificationOperationToken? = this.activeOperation,
        nextGeneration: Int = this.nextGeneration,
        sessionExpiryScheduled: Boolean = this.sessionExpiryScheduled,
    ): VerificationProgress =
        VerificationProgress(
            sessionId = sessionId,
            evidence = evidence,
            retries = retries,
            artifacts = artifacts,
            activeOperation = activeOperation,
            nextGeneration = nextGeneration,
            sessionExpiryScheduled = sessionExpiryScheduled,
        )

    override fun equals(other: Any?): Boolean =
        other is VerificationProgress &&
            sessionId == other.sessionId &&
            evidence == other.evidence &&
            retries == other.retries &&
            artifacts == other.artifacts &&
            activeOperation == other.activeOperation &&
            nextGeneration == other.nextGeneration &&
            sessionExpiryScheduled == other.sessionExpiryScheduled

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + evidence.hashCode()
        result = 31 * result + retries.hashCode()
        result = 31 * result + artifacts.hashCode()
        result = 31 * result + (activeOperation?.hashCode() ?: 0)
        result = 31 * result + nextGeneration
        return 31 * result + sessionExpiryScheduled.hashCode()
    }

    override fun toString(): String =
        "VerificationProgress(sessionId=$sessionId, evidence=$evidence, retries=$retries, " +
            "artifacts=$artifacts, activeOperation=$activeOperation)"
}

internal data class OperationStart(
    val progress: VerificationProgress,
    val token: VerificationOperationToken,
)
