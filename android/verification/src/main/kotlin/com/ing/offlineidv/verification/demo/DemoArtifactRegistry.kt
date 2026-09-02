package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import java.util.IdentityHashMap

/**
 * Deterministic in-memory artifact owner scoped to exactly one Demo Mode session.
 *
 * Resolution uses reference identity as well as kind so an equal-looking reference created by
 * another registry cannot cross the session boundary.
 */
public class DemoArtifactRegistry(
    public val sessionId: IdvSessionId,
) {
    private val artifacts = IdentityHashMap<VerificationArtifactReference, Any>()
    private var nextIdentifier: Int = 1
    private var cleared: Boolean = false

    /** Registers [artifact] and returns a newly generated opaque reference. */
    public fun register(
        kind: VerificationArtifactKind,
        artifact: Any,
    ): IdvResult<VerificationArtifactReference> {
        if (cleared) return invalidReference()
        val value = "demo-artifact-${nextIdentifier.toString().padStart(8, '0')}"
        val parsed = VerificationArtifactReference.parse(kind, value)
        if (parsed is IdvResult.Failure) return parsed
        val reference = (parsed as IdvResult.Success).value
        nextIdentifier += 1
        artifacts[reference] = artifact
        return IdvResult.Success(reference)
    }

    /** Resolves [reference] only when this active registry issued it with the expected kind/type. */
    public fun <T : Any> resolve(
        reference: VerificationArtifactReference,
        expectedKind: VerificationArtifactKind,
        expectedType: Class<T>,
    ): IdvResult<T> {
        if (cleared || reference.kind != expectedKind) return invalidReference()
        val artifact = artifacts[reference] ?: return invalidReference()
        if (!expectedType.isInstance(artifact)) return invalidReference()
        val typedArtifact = expectedType.cast(artifact) ?: return invalidReference()
        return IdvResult.Success(typedArtifact)
    }

    /** Clears all content. Repeated cleanup is safe. */
    public fun clear() {
        artifacts.clear()
        cleared = true
    }

    /** Number of currently retained synthetic artifacts. */
    public val size: Int
        get() = artifacts.size

    /** Whether cleanup has completed for this single-use session registry. */
    public val isCleared: Boolean
        get() = cleared

    override fun toString(): String = "DemoArtifactRegistry(sessionId=$sessionId, size=$size, cleared=$cleared)"

    private fun invalidReference(): IdvResult.Failure =
        IdvResult.Failure(
            IdvError.Verification(VerificationFailure.ARTIFACT_REFERENCE_INVALID),
        )
}
