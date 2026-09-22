package com.ing.offlineidv.nfc.real

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

public class BoundedLdsReadTest {
    @Test
    public fun `read at the exact limit succeeds`() {
        val source = byteArrayOf(1, 2, 3, 4)
        val input = CloseTrackingInputStream(source)

        val observed = input.useBoundedBytes(4) { it.readBytes() }

        assertArrayEquals(source, observed)
        assertTrue(input.closed)
    }

    @Test
    public fun `read above the limit fails before parser handoff`() {
        val input = CloseTrackingInputStream(byteArrayOf(1, 2, 3, 4, 5))

        try {
            input.useBoundedBytes(4) { it.readBytes() }
        } catch (_: LdsReadLimitExceeded) {
            assertTrue(input.closed)
            return
        }
        error("Expected LdsReadLimitExceeded")
    }

    @Test
    public fun `owned bounded bytes are overwritten after the block`() {
        var captured: ByteArray? = null

        ByteArrayInputStream(byteArrayOf(1, 2, 3)).useBoundedByteArray(3) { bytes ->
            captured = bytes
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
        }

        assertArrayEquals(byteArrayOf(0, 0, 0), captured)
    }

    private class CloseTrackingInputStream(
        bytes: ByteArray,
    ) : ByteArrayInputStream(bytes) {
        var closed: Boolean = false

        override fun close() {
            closed = true
            super.close()
        }
    }
}
