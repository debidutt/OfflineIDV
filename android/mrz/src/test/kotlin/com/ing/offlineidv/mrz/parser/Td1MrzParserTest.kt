package com.ing.offlineidv.mrz.parser

import com.ing.offlineidv.mrz.fixtures.SyntheticTd1Fixtures
import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzField
import com.ing.offlineidv.mrz.model.MrzFormat
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.Td1ResidencePermitMrz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class Td1MrzParserTest {
    private val parser = Td1MrzParser()
    private val referenceDate = LocalDate.of(2026, 9, 21)

    @Test
    public fun `valid synthetic Netherlands TD1 permit parses all mandatory fields`() {
        val result = parsed(SyntheticTd1Fixtures.valid)

        assertEquals(MrzFormat.TD1, result.document.format)
        assertEquals("I<", result.document.documentCode)
        assertEquals("NLD", result.document.issuingState)
        assertEquals("X12T34567", result.document.documentNumber)
        assertEquals("UTO", result.document.nationality)
        assertEquals(LocalDate.of(1990, 1, 1), result.document.dateOfBirth?.preferredValue)
        assertEquals(LocalDate.of(2030, 1, 1), result.document.expiryDate?.preferredValue)
        assertEquals("SYNTHETIC", result.document.name.surname)
        assertEquals(listOf("RESIDENT", "TEST"), result.document.name.givenNames)
        assertTrue(result.validation.isFormatAndCheckDigitValid)
        listOf(
            MrzField.DOCUMENT_NUMBER,
            MrzField.DATE_OF_BIRTH,
            MrzField.EXPIRY_DATE,
            MrzField.COMPOSITE_CHECK_DIGIT,
        ).forEach { field ->
            assertEquals(CheckDigitStatus.VALID, result.validation.checkDigits[field]?.status)
        }
    }

    @Test
    public fun `single ninety character sequence is normalized into three lines`() {
        val oneLine = SyntheticTd1Fixtures.valid.replace("\n", "").lowercase()

        assertTrue(parsed(oneLine).validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `wrong composite digit is reported without rejecting readable fields`() {
        val lines = SyntheticTd1Fixtures.valid.lines().toMutableList()
        lines[1] = lines[1].replaceRange(29, 30, if (lines[1][29] == '9') "0" else "9")

        val result = parsed(lines.joinToString("\n"))

        assertFalse(result.validation.isFormatAndCheckDigitValid)
        assertTrue(
            result.validation.issues.any {
                it.type == MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH
            },
        )
    }

    @Test
    public fun `extended document number variant remains explicitly unsupported`() {
        val lines = SyntheticTd1Fixtures.valid.lines().toMutableList()
        lines[0] = lines[0].replaceRange(14, 15, "<")

        val result = parsed(lines.joinToString("\n"))

        assertFalse(result.validation.isFormatAndCheckDigitValid)
        assertTrue(result.validation.issues.any { it.type == MrzValidationIssueType.UNSUPPORTED_TD1_VARIANT })
    }

    @Test
    public fun `incorrect line lengths are rejected safely`() {
        val result =
            parser.parse(
                SyntheticTd1Fixtures.valid
                    .lines()
                    .dropLast(1)
                    .joinToString("\n"),
                referenceDate,
            )

        assertTrue(result is MrzParseResult.Rejected)
        val rejected = result as MrzParseResult.Rejected
        assertTrue(rejected.validation.issues.any { it.type == MrzValidationIssueType.INCORRECT_LINE_COUNT })
    }

    @Test
    public fun `TD1 objects never render identity values`() {
        val result = parsed(SyntheticTd1Fixtures.valid)

        listOf(result.toString(), result.document.toString(), result.validation.toString()).forEach { rendered ->
            assertFalse(rendered.contains("SYNTHETIC"))
            assertFalse(rendered.contains("X12T34567"))
        }
    }

    private fun parsed(input: String): MrzParseResult.Parsed<Td1ResidencePermitMrz> {
        val result = parser.parse(input, referenceDate)
        assertTrue(result is MrzParseResult.Parsed)
        @Suppress("UNCHECKED_CAST")
        return result as MrzParseResult.Parsed<Td1ResidencePermitMrz>
    }
}
