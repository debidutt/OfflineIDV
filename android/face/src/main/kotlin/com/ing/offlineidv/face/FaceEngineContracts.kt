package com.ing.offlineidv.face

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId

/** Safe request for selfie capture in one active session. */
public data class SelfieCaptureRequest(
    public val sessionId: IdvSessionId,
)

/** Synthetic or platform-owned selfie retained outside verification state. */
public class SelfieArtifact(
    private val value: String,
) {
    /** Supplies selfie material only inside a trusted quality/face boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(value)

    override fun equals(other: Any?): Boolean = other is SelfieArtifact && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SelfieArtifact(${Redaction.MARKER})"
}

/** Document portrait input translated from trusted chip material. */
public class DocumentPortraitArtifact(
    private val value: String,
) {
    /** Supplies portrait material only inside a trusted face boundary. */
    public fun <R> useValue(block: (String) -> R): R = block(value)

    override fun equals(other: Any?): Boolean = other is DocumentPortraitArtifact && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "DocumentPortraitArtifact(${Redaction.MARKER})"
}

/** External selfie-capture observation. */
public sealed interface SelfieCaptureResult {
    public data class Captured(
        public val artifact: SelfieArtifact,
    ) : SelfieCaptureResult

    public data class Failed(
        public val error: IdvError,
    ) : SelfieCaptureResult
}

/** External selfie-capture boundary. */
public fun interface SelfieCaptureEngine {
    public fun capture(request: SelfieCaptureRequest): SelfieCaptureResult
}

/** External selfie-quality observation. */
public sealed interface SelfieQualityResult {
    public data object Accepted : SelfieQualityResult

    public data object Rejected : SelfieQualityResult

    public data class Failed(
        public val error: IdvError,
    ) : SelfieQualityResult
}

/** External selfie-quality boundary; it does not decide retry. */
public fun interface SelfieQualityEngine {
    public fun evaluate(artifact: SelfieArtifact): SelfieQualityResult
}

/** External face-comparison observation without scores, templates, or product decisions. */
public sealed interface FaceMatchResult {
    public data object Accepted : FaceMatchResult

    public data object Rejected : FaceMatchResult

    public data object Inconclusive : FaceMatchResult

    public data class Failed(
        public val error: IdvError,
    ) : FaceMatchResult
}

/** External face-comparison boundary; implementations make no identity-verification claim. */
public fun interface FaceMatchEngine {
    public fun compare(
        documentPortrait: DocumentPortraitArtifact,
        selfie: SelfieArtifact,
    ): FaceMatchResult
}
