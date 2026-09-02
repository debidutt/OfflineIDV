package com.ing.offlineidv.camera.real

import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.core.session.IdvSessionId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Sensitive encoded image retained only by a trusted, active-session boundary. */
public class CapturedImage internal constructor(
    encodedBytes: ByteArray,
    public val width: Int,
    public val height: Int,
    public val rotationDegrees: Int,
) {
    private val encodedBytes: ByteArray = encodedBytes.copyOf()

    init {
        require(encodedBytes.isNotEmpty()) { "Captured image must not be empty." }
        require(width > 0 && height > 0) { "Captured image dimensions must be positive." }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Rotation must be a right angle." }
    }

    /** Makes image bytes available only for the duration of trusted local processing. */
    public fun <R> useEncodedBytes(block: (ByteArray) -> R): R = block(encodedBytes)

    internal fun clear() {
        encodedBytes.fill(0)
    }

    override fun toString(): String = "CapturedImage(${Redaction.MARKER})"
}

/** Single-session, memory-only owner of captured images addressed by opaque numeric tokens. */
public class InMemoryCapturedImageStore(
    public val sessionId: IdvSessionId,
) {
    private val nextToken = AtomicInteger(1)
    private val images = ConcurrentHashMap<Int, CapturedImage>()

    @Volatile private var cleared: Boolean = false

    /** Stores an owned copy only when [requestSessionId] matches this active session. */
    public fun store(
        requestSessionId: IdvSessionId,
        encodedBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): IdvResult<DocumentCaptureArtifact> {
        if (cleared || requestSessionId != sessionId) return invalidReference()
        val token = nextToken.getAndIncrement()
        images[token] = CapturedImage(encodedBytes, width, height, rotationDegrees)
        return IdvResult.Success(DocumentCaptureArtifact(token))
    }

    /** Resolves only a token issued by this active store. */
    public fun resolve(artifact: DocumentCaptureArtifact): IdvResult<CapturedImage> {
        if (cleared) return invalidReference()
        val image = images[artifact.sourceToken] ?: return invalidReference()
        return IdvResult.Success(image)
    }

    /** Zeroes and removes every retained image. Repeated cleanup is safe. */
    public fun clear(requestSessionId: IdvSessionId) {
        if (requestSessionId != sessionId || cleared) return
        cleared = true
        images.values.forEach(CapturedImage::clear)
        images.clear()
    }

    public val size: Int
        get() = images.size

    public val isCleared: Boolean
        get() = cleared

    override fun toString(): String =
        "InMemoryCapturedImageStore(sessionId=$sessionId, size=$size, cleared=$cleared, payload=${Redaction.MARKER})"

    private fun invalidReference(): IdvResult.Failure =
        IdvResult.Failure(IdvError.Verification(VerificationFailure.ARTIFACT_REFERENCE_INVALID))
}
