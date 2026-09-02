package com.ing.offlineidv.camera

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId

/** Safe request to capture a document for one active session. */
public data class DocumentCaptureRequest(
    public val sessionId: IdvSessionId,
)

/** Synthetic or platform-owned captured document kept outside verification state. */
public class DocumentCaptureArtifact(
    public val sourceToken: Int,
) {
    init {
        require(sourceToken > 0) { "sourceToken must be positive" }
    }

    override fun equals(other: Any?): Boolean = other is DocumentCaptureArtifact && sourceToken == other.sourceToken

    override fun hashCode(): Int = sourceToken

    override fun toString(): String = "DocumentCaptureArtifact(${Redaction.MARKER})"
}

/** Observation produced by a document capture engine. */
public sealed interface DocumentCaptureResult {
    public data class Captured(
        public val artifact: DocumentCaptureArtifact,
    ) : DocumentCaptureResult

    public data class Failed(
        public val error: IdvError,
    ) : DocumentCaptureResult
}

/** External document capture boundary; implementations do not control verification flow. */
public fun interface DocumentCaptureEngine {
    public fun capture(request: DocumentCaptureRequest): DocumentCaptureResult
}

/** Non-blocking document-capture boundary for lifecycle-backed platform adapters. */
public fun interface AsyncDocumentCaptureEngine {
    /** Starts capture and returns a handle that suppresses delivery after cancellation. */
    public fun capture(
        request: DocumentCaptureRequest,
        callback: (DocumentCaptureResult) -> Unit,
    ): CancellableOperation
}

/** Non-blocking camera preparation boundary kept separate from verification flow decisions. */
public fun interface AsyncCameraPreparationEngine {
    /** Binds the camera and reports only readiness or a safe technical observation. */
    public fun prepare(callback: (CameraPreparationResult) -> Unit): CancellableOperation
}

/** Result of lifecycle-safe camera preparation. */
public sealed interface CameraPreparationResult {
    public data object Ready : CameraPreparationResult

    public data class Failed(
        public val error: IdvError,
    ) : CameraPreparationResult
}

/** Observation produced by document-quality evaluation. */
public sealed interface DocumentQualityResult {
    public data object Accepted : DocumentQualityResult

    public data object Rejected : DocumentQualityResult

    public data class Failed(
        public val error: IdvError,
    ) : DocumentQualityResult
}

/** External document-quality boundary; it reports quality without deciding retry. */
public fun interface DocumentQualityEngine {
    public fun evaluate(artifact: DocumentCaptureArtifact): DocumentQualityResult
}

/** Non-blocking quality boundary used by real image adapters. */
public fun interface AsyncDocumentQualityEngine {
    /** Evaluates image quality without interpreting retry or verification policy. */
    public fun evaluate(
        artifact: DocumentCaptureArtifact,
        callback: (DocumentQualityResult) -> Unit,
    ): CancellableOperation
}
