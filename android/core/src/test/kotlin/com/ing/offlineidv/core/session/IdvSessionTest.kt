package com.ing.offlineidv.core.session

import com.ing.offlineidv.core.error.SessionFailure
import com.ing.offlineidv.core.result.IdvResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

public class IdvSessionTest {
    private val sessionId =
        (IdvSessionId.parse("synthetic_session_001") as IdvResult.Success).value
    private val startedAt: Instant = Instant.parse("2026-01-01T12:00:00Z")
    private val expiresAt: Instant = Instant.parse("2026-01-01T12:10:00Z")

    @Test
    public fun `session identifier string form is redacted`() {
        val rendered = sessionId.toString()

        assertFalse(rendered.contains("synthetic_session_001"))
        assertTrue(rendered.contains("REDACTED"))
    }

    @Test
    public fun `invalid identifier returns an error without the supplied value`() {
        val suppliedValue = "bad value"

        val result = IdvSessionId.parse(suppliedValue)

        val failure = result as IdvResult.Failure
        assertEquals(SessionFailure.INVALID_IDENTIFIER.stableCode, failure.error.code)
        assertFalse(failure.toString().contains(suppliedValue))
    }

    @Test
    public fun `session expires at its exclusive expiry boundary`() {
        val session = (IdvSession.create(sessionId, startedAt, expiresAt) as IdvResult.Success).value

        assertFalse(session.isExpired(expiresAt.minusNanos(1)))
        assertTrue(session.isExpired(expiresAt))
        assertTrue(session.isExpired(expiresAt.plusSeconds(1)))
    }

    @Test
    public fun `expiry must be after session start`() {
        val result = IdvSession.create(sessionId, startedAt, startedAt)

        val failure = result as IdvResult.Failure
        assertEquals(SessionFailure.INVALID_EXPIRY.stableCode, failure.error.code)
    }
}
