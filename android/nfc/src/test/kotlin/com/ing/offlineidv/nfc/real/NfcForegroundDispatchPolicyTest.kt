package com.ing.offlineidv.nfc.real

import android.nfc.NfcAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class NfcForegroundDispatchPolicyTest {
    @Test
    public fun `only NFC tag discovery actions enter the fallback`() {
        assertTrue(NfcForegroundDispatchPolicy.accepts(NfcAdapter.ACTION_TECH_DISCOVERED))
        assertTrue(NfcForegroundDispatchPolicy.accepts(NfcAdapter.ACTION_TAG_DISCOVERED))
        assertFalse(NfcForegroundDispatchPolicy.accepts(NfcAdapter.ACTION_NDEF_DISCOVERED))
        assertFalse(NfcForegroundDispatchPolicy.accepts(IntentActions.MAIN))
        assertFalse(NfcForegroundDispatchPolicy.accepts(null))
    }

    private object IntentActions {
        const val MAIN: String = "android.intent.action.MAIN"
    }
}
