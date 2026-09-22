package com.ing.offlineidv.demo

import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.NfcDiagnosticEvent
import com.ing.offlineidv.nfc.NfcDiagnosticObservation
import com.ing.offlineidv.nfc.NfcDiagnosticStage
import com.ing.offlineidv.nfc.NfcDiagnosticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class NfcDebugDiagnosticSinkTest {
    @Test
    public fun `enabled sink logs only closed payload free NFC diagnostics`() {
        val lines = mutableListOf<String>()
        val sink = NfcDebugDiagnosticSink(enabled = true, logLine = lines::add)

        sink.record(
            NfcDiagnosticEvent(
                stage = NfcDiagnosticStage.PACE,
                status = NfcDiagnosticStatus.FAILED,
                failure = NfcFailure.ACCESS_DENIED,
            ),
        )
        sink.record(
            NfcDiagnosticEvent(
                stage = NfcDiagnosticStage.PASSIVE_AUTHENTICATION,
                status = NfcDiagnosticStatus.SUCCEEDED,
                observation = NfcDiagnosticObservation.PASSIVE_AUTH_VALID,
            ),
        )

        assertEquals(
            listOf(
                "NFC_DIAG STAGE=PACE STATUS=FAILED FAILURE=nfc.access_denied",
                "NFC_DIAG STAGE=PASSIVE_AUTHENTICATION STATUS=SUCCEEDED OBSERVATION=PASSIVE_AUTH_VALID",
            ),
            lines,
        )
        val output = lines.joinToString("\n")
        listOf("L898902C3", "740812", "120415", "00A4040C").forEach {
            assertFalse(output.contains(it))
        }
    }

    @Test
    public fun `disabled sink emits no NFC diagnostics`() {
        val lines = mutableListOf<String>()

        NfcDebugDiagnosticSink(enabled = false, logLine = lines::add).record(
            NfcDiagnosticEvent(NfcDiagnosticStage.TAG_DISCOVERY, NfcDiagnosticStatus.SUCCEEDED),
        )

        assertTrue(lines.isEmpty())
    }
}
