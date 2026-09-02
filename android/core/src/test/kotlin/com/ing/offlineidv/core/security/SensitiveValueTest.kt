package com.ing.offlineidv.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

public class SensitiveValueTest {
    @Test
    public fun `string representation never exposes held bytes`() {
        val secret = "synthetic-secret".toByteArray(StandardCharsets.UTF_8)
        val sensitive = SensitiveValue.bytes(secret)

        val rendered = sensitive.toString()

        assertFalse(rendered.contains("synthetic-secret"))
        assertTrue(rendered.contains(Redaction.MARKER))
        sensitive.close()
    }

    @Test
    public fun `byte factory owns a copy and overwrites it on close`() {
        val source = byteArrayOf(1, 2, 3, 4)
        var heldBuffer: ByteArray? = null
        val sensitive = SensitiveValue.bytes(source)
        sensitive.use { heldBuffer = it }

        sensitive.close()

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), source)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), heldBuffer)
        assertTrue(sensitive.isCleared)
    }

    @Test
    public fun `cleanup runs once and future access is rejected`() {
        var cleanupCount = 0
        val sensitive =
            SensitiveValue.withCleaner(charArrayOf('x')) {
                it.fill('\u0000')
                cleanupCount += 1
            }

        sensitive.close()
        sensitive.close()

        assertTrue(sensitive.isCleared)
        assertTrue(cleanupCount == 1)
        assertThrows(IllegalStateException::class.java) { sensitive.use { Unit } }
    }
}
