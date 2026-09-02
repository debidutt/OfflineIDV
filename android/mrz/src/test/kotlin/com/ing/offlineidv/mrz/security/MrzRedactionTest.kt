package com.ing.offlineidv.mrz.security

import com.ing.offlineidv.core.error.IdvErrorCategory
import com.ing.offlineidv.core.security.Redaction
import com.ing.offlineidv.mrz.fixtures.SyntheticTd3Fixtures
import com.ing.offlineidv.mrz.model.MrzNormalizationResult
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.normalization.MrzNormalizer
import com.ing.offlineidv.mrz.parser.Td3MrzParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class MrzRedactionTest {
    private val referenceDate = LocalDate.of(2026, 8, 2)

    @Test
    public fun `document name date validation and parse result redact sensitive fields`() {
        val result =
            Td3MrzParser().parse(SyntheticTd3Fixtures.valid, referenceDate) as
                MrzParseResult.Parsed
        val rendered =
            listOf(
                result.document.toString(),
                result.document.name.toString(),
                result.document.dateOfBirth.toString(),
                result.document.expiryDate.toString(),
                result.validation.toString(),
                result.toString(),
            )

        rendered.forEach { value ->
            assertTrue(value.contains(Redaction.MARKER))
            assertContainsNoSyntheticIdentity(value)
        }
    }

    @Test
    public fun `normalization result does not render normalized lines`() {
        val result = MrzNormalizer().normalize(SyntheticTd3Fixtures.valid)

        assertTrue(result is MrzNormalizationResult.Success)
        assertTrue(result.toString().contains(Redaction.MARKER))
        assertContainsNoSyntheticIdentity(result.toString())
    }

    @Test
    public fun `fatal rejection renders only safe codes`() {
        val result = Td3MrzParser().parse("INCOMPLETE", referenceDate) as MrzParseResult.Rejected
        val rendered = result.toString()

        assertFalse(rendered.contains("INCOMPLETE"))
        assertTrue(rendered.contains(result.error.stableCode))
        assertTrue(rendered.contains(Redaction.MARKER))
    }

    @Test
    public fun `MRZ errors map to stable core category without raw context`() {
        val result = Td3MrzParser().parse("INCOMPLETE", referenceDate) as MrzParseResult.Rejected
        val error = result.error.toIdvError()

        assertEquals(IdvErrorCategory.MRZ, error.category)
        assertEquals("mrz.malformed", error.code)
        assertFalse(error.toString().contains("INCOMPLETE"))
    }

    @Test
    public fun `ambiguity rendering omits observed and selected characters`() {
        val base = SyntheticTd3Fixtures.build(birthDate = "000101")
        val input = SyntheticTd3Fixtures.replace(base, 1, 13, 'O')
        val result = Td3MrzParser().parse(input, referenceDate) as MrzParseResult.Parsed
        val rendered =
            result.validation.corrections
                .single()
                .toString()

        assertFalse(rendered.contains("observed"))
        assertFalse(rendered.contains("selected"))
    }

    private fun assertContainsNoSyntheticIdentity(rendered: String) {
        listOf("TESTER", "SYNTHETIC", "A12B34567", "900101", "301231", "UTO").forEach { fragment ->
            assertFalse(rendered.contains(fragment))
        }
    }
}
