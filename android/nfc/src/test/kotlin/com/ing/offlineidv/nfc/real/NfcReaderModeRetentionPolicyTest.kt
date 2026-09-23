package com.ing.offlineidv.nfc.real

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class NfcReaderModeRetentionPolicyTest {
    @Test
    public fun `reader mode stays enabled after tag claim while read remains active`() {
        assertTrue(
            NfcReaderModeRetentionPolicy.shouldEnable(
                closed = false,
                hasActiveRead = true,
                hostAttached = true,
            ),
        )
    }

    @Test
    public fun `reader mode stops when read completes host detaches or adapter closes`() {
        assertFalse(
            NfcReaderModeRetentionPolicy.shouldEnable(
                closed = false,
                hasActiveRead = false,
                hostAttached = true,
            ),
        )
        assertFalse(
            NfcReaderModeRetentionPolicy.shouldEnable(
                closed = false,
                hasActiveRead = true,
                hostAttached = false,
            ),
        )
        assertFalse(
            NfcReaderModeRetentionPolicy.shouldEnable(
                closed = true,
                hasActiveRead = true,
                hostAttached = true,
            ),
        )
    }
}
