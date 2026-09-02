package com.ing.offlineidv.nfc.demo

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.ChipValidationResult
import com.ing.offlineidv.nfc.NfcReadRequest
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedChipComparisonResult
import com.ing.offlineidv.nfc.PrintedPassportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeNfcEnginesTest {
    @Test
    public fun `successful read is deterministic across fresh engines`() {
        val first = FakePassportNfcEngine(DemoNfcReadBehavior.SUCCESS).read(request)
        val second = FakePassportNfcEngine(DemoNfcReadBehavior.SUCCESS).read(request)

        assertEquals((first as NfcReadResult.Read).artifact, (second as NfcReadResult.Read).artifact)
    }

    @Test
    public fun `timeout then success reports observations only`() {
        val engine = FakePassportNfcEngine(DemoNfcReadBehavior.TIMEOUT_THEN_SUCCESS)

        assertEquals(NfcReadResult.Timeout, engine.read(request))
        assertTrue(engine.read(request) is NfcReadResult.Read)
    }

    @Test
    public fun `repeated timeout never decides retry exhaustion`() {
        val engine = FakePassportNfcEngine(DemoNfcReadBehavior.ALWAYS_TIMEOUT)

        repeat(4) { assertEquals(NfcReadResult.Timeout, engine.read(request)) }
    }

    @Test
    public fun `unavailable and technical failure remain distinct`() {
        assertEquals(NfcReadResult.Unavailable, FakePassportNfcEngine(DemoNfcReadBehavior.UNAVAILABLE).read(request))
        val failed = FakePassportNfcEngine(DemoNfcReadBehavior.TECHNICAL_FAILURE).read(request) as NfcReadResult.Failed
        assertEquals("nfc.read_failed", failed.error.code)
    }

    @Test
    public fun `chip validation returns configured external observations`() {
        val behavior =
            DemoChipValidationBehavior(
                passiveAuthentication = PassiveAuthenticationObservation.NOT_PERFORMED,
            )
        val result = FakeChipValidationEngine(behavior).validate(ChipDataArtifact("synthetic-chip"))
        val observation = (result as ChipValidationResult.Validated).observation

        assertTrue(observation.dg1Available)
        assertTrue(observation.dg2Available)
        assertEquals(PassiveAuthenticationObservation.NOT_PERFORMED, observation.passiveAuthentication)
    }

    @Test
    public fun `comparison engine reports match mismatch and inconclusive`() {
        val printed = PrintedPassportData("synthetic-printed")
        val chip = ChipDataArtifact("synthetic-chip")

        assertEquals(
            PrintedChipComparisonResult.Match,
            FakePrintedChipComparisonEngine(DemoPrintedChipComparisonBehavior.MATCH).compare(printed, chip),
        )
        assertEquals(
            PrintedChipComparisonResult.Mismatch,
            FakePrintedChipComparisonEngine(DemoPrintedChipComparisonBehavior.MISMATCH).compare(printed, chip),
        )
        assertEquals(
            PrintedChipComparisonResult.Inconclusive,
            FakePrintedChipComparisonEngine(DemoPrintedChipComparisonBehavior.INCONCLUSIVE).compare(printed, chip),
        )
    }

    @Test
    public fun `reset restores timeout-then-success sequence`() {
        val engine = FakePassportNfcEngine(DemoNfcReadBehavior.TIMEOUT_THEN_SUCCESS)
        engine.read(request)
        engine.read(request)

        engine.reset()

        assertEquals(NfcReadResult.Timeout, engine.read(request))
    }

    @Test
    public fun `NFC artifact and key strings are redacted`() {
        val rendered = listOf(ChipDataArtifact("secret"), PassportAccessKey("secret")).joinToString()

        assertTrue(rendered.contains("[REDACTED]"))
        assertFalse(rendered.contains("secret"))
    }

    private companion object {
        val request =
            NfcReadRequest(
                sessionId = (IdvSessionId.parse("nfc-demo-session") as IdvResult.Success).value,
                accessKey = PassportAccessKey("synthetic-access"),
            )
    }
}
