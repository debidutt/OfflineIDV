package com.ing.offlineidv.core.session

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.SessionFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.security.Redaction
import java.time.Instant

/**
 * Opaque, pseudonymous identifier for an active verification session.
 *
 * Its string form is always redacted. Use [useValue] only at a trusted persistence or IPC boundary
 * and never retain or log the supplied string.
 */
public class IdvSessionId private constructor(
    private val value: String,
) {
    /** Supplies the identifier to a scoped trusted-boundary operation. */
    public fun <R> useValue(block: (String) -> R): R = block(value)

    override fun equals(other: Any?): Boolean = other is IdvSessionId && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "IdvSessionId(${Redaction.MARKER})"

    public companion object {
        private val validIdentifier = Regex("[A-Za-z0-9][A-Za-z0-9_-]{7,127}")

        /** Validates and wraps a generated opaque identifier without exposing it in errors. */
        public fun parse(value: String): IdvResult<IdvSessionId> =
            if (validIdentifier.matches(value)) {
                IdvResult.Success(IdvSessionId(value))
            } else {
                IdvResult.Failure(IdvError.Session(SessionFailure.INVALID_IDENTIFIER))
            }
    }
}

/** Generates cryptographically unpredictable session identifiers in a platform composition root. */
public fun interface IdvSessionIdGenerator {
    /** Returns a new identifier or a structured local failure. */
    public fun generate(): IdvResult<IdvSessionId>
}

/** Immutable lifecycle metadata for one active verification session. */
public class IdvSession private constructor(
    public val id: IdvSessionId,
    public val startedAt: Instant,
    public val expiresAt: Instant,
) {
    /** A session is expired at, or after, its exclusive expiry boundary. */
    public fun isExpired(at: Instant): Boolean = !at.isBefore(expiresAt)

    override fun toString(): String = "IdvSession(id=$id, startedAt=$startedAt, expiresAt=$expiresAt)"

    public companion object {
        /** Creates a session only when [expiresAt] is strictly after [startedAt]. */
        public fun create(
            id: IdvSessionId,
            startedAt: Instant,
            expiresAt: Instant,
        ): IdvResult<IdvSession> =
            if (expiresAt.isAfter(startedAt)) {
                IdvResult.Success(IdvSession(id, startedAt, expiresAt))
            } else {
                IdvResult.Failure(IdvError.Session(SessionFailure.INVALID_EXPIRY))
            }
    }
}
