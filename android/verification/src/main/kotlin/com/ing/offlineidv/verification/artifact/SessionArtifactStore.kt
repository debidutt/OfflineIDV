package com.ing.offlineidv.verification.artifact

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import java.util.IdentityHashMap

/** Single-use, in-memory owner for sensitive real-session artifacts outside reducer state. */
public class SessionArtifactStore(
    public val sessionId: IdvSessionId,
) {
    private val artifacts = IdentityHashMap<VerificationArtifactReference, Any>()
    private var nextIdentifier: Int = 1

    @Volatile private var cleared: Boolean = false

    /** Registers a sensitive object and returns only an opaque reference. */
    public fun register(
        kind: VerificationArtifactKind,
        artifact: Any,
    ): IdvResult<VerificationArtifactReference> =
        synchronized(this) {
            if (cleared) return@synchronized invalidReference()
            val parsed =
                VerificationArtifactReference.parse(
                    kind,
                    "real-artifact-${nextIdentifier.toString().padStart(8, '0')}",
                )
            if (parsed is IdvResult.Failure) return@synchronized parsed
            val reference = (parsed as IdvResult.Success).value
            nextIdentifier += 1
            artifacts[reference] = artifact
            IdvResult.Success(reference)
        }

    /** Resolves only a reference instance issued by this store with the expected kind and type. */
    public fun <T : Any> resolve(
        reference: VerificationArtifactReference,
        expectedKind: VerificationArtifactKind,
        expectedType: Class<T>,
    ): IdvResult<T> =
        synchronized(this) {
            if (cleared || reference.kind != expectedKind) return@synchronized invalidReference()
            val artifact = artifacts[reference] ?: return@synchronized invalidReference()
            if (!expectedType.isInstance(artifact)) return@synchronized invalidReference()
            val typedArtifact = expectedType.cast(artifact) ?: return@synchronized invalidReference()
            IdvResult.Success(typedArtifact)
        }

    /** Drops all sensitive objects. Repeated cleanup is safe. */
    public fun clear() {
        synchronized(this) {
            if (cleared) return
            artifacts.values.forEach { artifact ->
                if (artifact is AutoCloseable) runCatching { artifact.close() }
            }
            artifacts.clear()
            cleared = true
        }
    }

    public val size: Int
        get() = synchronized(this) { artifacts.size }

    public val isCleared: Boolean
        get() = cleared

    override fun toString(): String = "SessionArtifactStore(sessionId=$sessionId, size=$size, cleared=$cleared)"

    private fun invalidReference(): IdvResult.Failure =
        IdvResult.Failure(IdvError.Verification(VerificationFailure.ARTIFACT_REFERENCE_INVALID))
}
