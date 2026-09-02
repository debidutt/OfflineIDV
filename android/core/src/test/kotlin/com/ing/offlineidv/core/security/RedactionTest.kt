package com.ing.offlineidv.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

public class RedactionTest {
    @Test
    public fun `suffix redaction never includes the complete source`() {
        val source = "SYNTHETIC1234"

        val redacted = Redaction.retainingSuffix(source, visibleCharacters = 4)

        assertEquals("${Redaction.MARKER}1234", redacted)
        assertFalse(redacted.contains(source))
    }

    @Test
    public fun `suffix redaction hides short values completely`() {
        assertEquals(Redaction.MARKER, Redaction.retainingSuffix("1234", visibleCharacters = 4))
        assertEquals(Redaction.MARKER, Redaction.retainingSuffix("1234", visibleCharacters = 0))
    }

    @Test
    public fun `negative suffix size is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Redaction.retainingSuffix("synthetic", visibleCharacters = -1)
        }
    }
}
