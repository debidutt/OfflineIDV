package com.ing.offlineidv.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class PassportDataEnginesTest {
    @Test
    public fun `matching internal DG1 and printed fields produce match only`() {
        val result = Td3PrintedChipComparisonEngine.compare(PrintedPassportData(mrz()), chip(mrz()))

        assertEquals(PrintedChipComparisonResult.Match, result)
        assertFalse(result.toString().contains("A12B34567"))
    }

    @Test
    public fun `different selected identity field produces mismatch only`() {
        val result = Td3PrintedChipComparisonEngine.compare(PrintedPassportData(mrz()), chip(mrz(documentNumber = "Z98Y76543")))

        assertEquals(PrintedChipComparisonResult.Mismatch, result)
        assertFalse(result.toString().contains("Z98Y76543"))
    }

    @Test
    public fun `incomplete input produces inconclusive without identity`() {
        val result = Td3PrintedChipComparisonEngine.compare(PrintedPassportData("incomplete"), chip(mrz()))

        assertEquals(PrintedChipComparisonResult.Inconclusive, result)
        assertFalse(result.toString().contains("incomplete"))
    }

    @Test
    public fun `chip validation preserves six-state passive observation`() {
        PassiveAuthenticationObservation.entries.forEach { status ->
            val result = PassportChipValidationEngine.validate(chip(mrz(), status = status)) as ChipValidationResult.Validated

            assertEquals(status, result.observation.passiveAuthentication)
            assertTrue(result.observation.dg1Available)
        }
    }

    @Test
    public fun `DG2 availability requires and returns only opaque portrait artifact`() {
        val artifact =
            ChipDataArtifact.fromRead(
                dg1Value = mrz(),
                portraitBytes = byteArrayOf(1, 2, 3),
                passiveAuthentication = PassiveAuthenticationObservation.UNAVAILABLE,
            )

        val observation = (PassportChipValidationEngine.validate(artifact) as ChipValidationResult.Validated).observation

        assertTrue(observation.dg2Available)
        assertTrue(observation.portrait.toString().contains("[REDACTED]"))
        assertFalse(observation.toString().contains("1, 2, 3"))
    }

    @Test
    public fun `missing DG2 returns no portrait`() {
        val observation = (PassportChipValidationEngine.validate(chip(mrz())) as ChipValidationResult.Validated).observation

        assertFalse(observation.dg2Available)
        assertNull(observation.portrait)
    }

    @Test
    public fun `all NFC artifact strings redact sensitive material`() {
        val rendered =
            listOf(
                PassportAccessKey("A12B34567900101301231"),
                PrintedPassportData(mrz()),
                chip(mrz()),
                ChipPortraitArtifact("portrait"),
            ).joinToString()

        assertFalse(rendered.contains("A12B34567"))
        assertFalse(rendered.contains("portrait"))
        assertEquals(4, Regex("\\[REDACTED]").findAll(rendered).count())
    }

    private companion object {
        fun chip(
            dg1: String,
            status: PassiveAuthenticationObservation = PassiveAuthenticationObservation.NOT_PERFORMED,
        ): ChipDataArtifact = ChipDataArtifact.fromRead(dg1, null, status)

        fun mrz(documentNumber: String = "A12B34567"): String {
            val line1 = "P<UTO" + "ATLAS<<SYNTHETIC<PERSON".padEnd(39, '<')
            val line2 =
                documentNumber + "0" + "UTO" + "900101" + "0" + "X" + "301231" + "0" +
                    "<<<<<<<<<<<<<<" + "0" + "0"
            check(line1.length == 44 && line2.length == 44)
            return "$line1\n$line2"
        }
    }
}
