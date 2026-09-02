package com.ing.offlineidv.core.result

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.SessionFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class IdvResultTest {
    @Test
    public fun `success string representation redacts its value`() {
        val value = "synthetic-secret"
        val result = IdvResult.Success(value)

        assertFalse(result.toString().contains(value))
        assertTrue(result.toString().contains("REDACTED"))
    }

    @Test
    public fun `map transforms success and preserves failure`() {
        val success = IdvResult.Success(2).map { it * 3 }
        val failure = IdvResult.Failure(IdvError.Session(SessionFailure.EXPIRED))

        assertEquals(6, success.getOrNull())
        assertSame(failure, failure.map { "unused" })
        assertNull(failure.getOrNull())
    }

    @Test
    public fun `flatMap does not execute after failure`() {
        var executed = false
        val failure = IdvResult.Failure(IdvError.Session(SessionFailure.EXPIRED))

        val result =
            failure.flatMap {
                executed = true
                IdvResult.Success(Unit)
            }

        assertSame(failure, result)
        assertFalse(executed)
    }
}
