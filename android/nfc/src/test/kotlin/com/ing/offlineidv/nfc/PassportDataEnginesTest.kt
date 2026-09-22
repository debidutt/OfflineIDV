package com.ing.offlineidv.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class PassportDataEnginesTest {
    @Test
    public fun `matching TD3 fields produce match only`() {
        val result = MrzPrintedChipComparisonEngine.compare(printed(), chip())

        assertEquals(PrintedChipComparisonResult.Match, result)
        assertFalse(result.toString().contains("A12B34567"))
    }

    @Test
    public fun `different selected identity field produces mismatch only`() {
        val result = MrzPrintedChipComparisonEngine.compare(printed(), chip(documentNumber = "Z98Y76543"))

        assertEquals(PrintedChipComparisonResult.Mismatch, result)
        assertFalse(result.toString().contains("Z98Y76543"))
    }

    @Test
    public fun `incomplete input produces inconclusive without identity`() {
        val result = MrzPrintedChipComparisonEngine.compare(PrintedPassportData("incomplete"), chip())

        assertEquals(PrintedChipComparisonResult.Inconclusive, result)
        assertFalse(result.toString().contains("incomplete"))
    }

    @Test
    public fun `chip validation preserves six-state passive observation`() {
        PassiveAuthenticationObservation.entries.forEach { status ->
            val result = PassportChipValidationEngine.validate(chip(status = status)) as ChipValidationResult.Validated

            assertEquals(status, result.observation.passiveAuthentication)
            assertTrue(result.observation.dg1Available)
        }
    }

    @Test
    public fun `chip validation preserves chip authentication independently`() {
        ChipAuthenticationObservation.entries.forEach { status ->
            val artifact =
                ChipDataArtifact.fromRead(
                    dg1Value = encodedFields(),
                    portraitBytes = null,
                    passiveAuthentication = PassiveAuthenticationObservation.VALID,
                    chipAuthentication = status,
                )

            val result = PassportChipValidationEngine.validate(artifact) as ChipValidationResult.Validated

            assertEquals(PassiveAuthenticationObservation.VALID, result.observation.passiveAuthentication)
            assertEquals(status, result.observation.chipAuthentication)
        }
    }

    @Test
    public fun `DG2 availability requires and returns only opaque portrait artifact`() {
        val artifact =
            ChipDataArtifact.fromRead(
                dg1Value = encodedFields(),
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
        val observation = (PassportChipValidationEngine.validate(chip()) as ChipValidationResult.Validated).observation

        assertFalse(observation.dg2Available)
        assertNull(observation.portrait)
    }

    @Test
    public fun `all NFC artifact strings redact sensitive material`() {
        val rendered =
            listOf(
                PassportAccessKey("A12B34567900101301231"),
                printed(),
                chip(),
                ChipPortraitArtifact("portrait"),
            ).joinToString()

        assertFalse(rendered.contains("A12B34567"))
        assertFalse(rendered.contains("portrait"))
        assertEquals(4, Regex("\\[REDACTED]").findAll(rendered).count())
    }

    private companion object {
        fun printed(documentNumber: String = "A12B34567"): PrintedPassportData =
            PrintedPassportData.fromMrzFields(documentNumber, "UTO", "900101", "301231")

        fun chip(
            documentNumber: String = "A12B34567",
            status: PassiveAuthenticationObservation = PassiveAuthenticationObservation.NOT_PERFORMED,
        ): ChipDataArtifact = ChipDataArtifact.fromRead(encodedFields(documentNumber), null, status)

        fun encodedFields(documentNumber: String = "A12B34567"): String =
            PrintedPassportData
                .fromMrzFields(documentNumber, "UTO", "900101", "301231")
                .useValue { it }
    }
}
