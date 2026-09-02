package com.ing.offlineidv.mrz.parser

import com.ing.offlineidv.core.error.MrzFailure
import com.ing.offlineidv.mrz.date.MrzDatePolicy
import com.ing.offlineidv.mrz.date.WindowedMrzDateInterpreter
import com.ing.offlineidv.mrz.fixtures.SyntheticTd3Fixtures
import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzConfidence
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzField
import com.ing.offlineidv.mrz.model.MrzNormalizationChangeType
import com.ing.offlineidv.mrz.model.MrzParseError
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzSex
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.Td3PassportMrz
import com.ing.offlineidv.mrz.validation.MrzValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class Td3MrzParserTest {
    private val referenceDate = LocalDate.of(2026, 8, 2)
    private val parser = Td3MrzParser()

    @Test
    public fun `valid synthetic TD3 parses every field`() {
        val result = parsed(SyntheticTd3Fixtures.valid)

        assertEquals("P<", result.document.documentCode)
        assertEquals("UTO", result.document.issuingState)
        assertEquals("TESTER", result.document.name.surname)
        assertEquals(listOf("SYNTHETIC", "ALPHA"), result.document.name.givenNames)
        assertEquals("A12B34567", result.document.documentNumber)
        assertEquals("UTO", result.document.nationality)
        assertEquals(LocalDate.of(1990, 1, 1), result.document.dateOfBirth?.preferredValue)
        assertEquals(MrzSex.FEMALE, result.document.sex)
        assertEquals(LocalDate.of(2030, 12, 31), result.document.expiryDate?.preferredValue)
        assertEquals("SYNTHETIC1", result.document.personalNumber?.trimEnd('<'))
        assertTrue(result.validation.isFormatAndCheckDigitValid)
        assertEquals(MrzConfidence.HIGH, result.validation.confidence)
        assertEquals(MrzExpiryStatus.VALID, result.validation.expiryStatus)
    }

    @Test
    public fun `filler-heavy optional data validates`() {
        val result = parsed(SyntheticTd3Fixtures.fillerHeavy)

        assertTrue(result.validation.isFormatAndCheckDigitValid)
        assertEquals(CheckDigitStatus.VALID, result.validation.checkDigits[MrzField.OPTIONAL_DATA]?.status)
    }

    @Test
    public fun `missing given names and filler optional data are supported`() {
        val result = parsed(SyntheticTd3Fixtures.noGivenNames)

        assertEquals("SOLO", result.document.name.surname)
        assertTrue(
            result.document.name.givenNames
                .isEmpty(),
        )
        assertNull(result.document.personalNumber)
        assertEquals(
            CheckDigitStatus.NOT_APPLICABLE,
            result.validation.checkDigits[MrzField.OPTIONAL_DATA]?.status,
        )
        assertEquals(MrzExpiryStatus.EXPIRES_TODAY, result.validation.expiryStatus)
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `invalid document-number check digit is aggregated`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 9, '0')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.CHECK_DIGIT_MISMATCH, MrzField.DOCUMENT_NUMBER)
        assertEquals(CheckDigitStatus.MISMATCH, result.validation.checkDigits[MrzField.DOCUMENT_NUMBER]?.status)
        assertEquals(MrzFailure.CHECKSUM_INVALID, result.validation.toIdvErrorOrNull()?.reason)
    }

    @Test
    public fun `invalid birth-date check digit is aggregated`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 19, '0')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.CHECK_DIGIT_MISMATCH, MrzField.DATE_OF_BIRTH)
    }

    @Test
    public fun `invalid expiry-date check digit is aggregated`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 27, '0')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.CHECK_DIGIT_MISMATCH, MrzField.EXPIRY_DATE)
    }

    @Test
    public fun `invalid optional-data check digit is aggregated`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 42, '0')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.CHECK_DIGIT_MISMATCH, MrzField.OPTIONAL_DATA)
    }

    @Test
    public fun `invalid composite check digit is distinct`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 43, '0')
        val result = parsed(input)

        assertIssue(
            result,
            MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH,
            MrzField.COMPOSITE_CHECK_DIGIT,
        )
    }

    @Test
    public fun `mandatory check digit filler is malformed rather than repaired`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 1, 9, '<')
        val result = parsed(input)

        assertIssue(
            result,
            MrzValidationIssueType.INVALID_CHECK_DIGIT_CHARACTER,
            MrzField.DOCUMENT_NUMBER,
        )
    }

    @Test
    public fun `impossible birth date is separate from checksum evidence`() {
        val result = parsed(SyntheticTd3Fixtures.build(birthDate = "901332"))

        assertIssue(result, MrzValidationIssueType.IMPOSSIBLE_DATE, MrzField.DATE_OF_BIRTH)
        assertNull(result.document.dateOfBirth)
        assertEquals(MrzFailure.DATE_INVALID, result.validation.toIdvErrorOrNull()?.reason)
    }

    @Test
    public fun `impossible expiry date is reported`() {
        val result = parsed(SyntheticTd3Fixtures.build(expiryDate = "301332"))

        assertIssue(result, MrzValidationIssueType.IMPOSSIBLE_DATE, MrzField.EXPIRY_DATE)
        assertNull(result.document.expiryDate)
        assertEquals(MrzExpiryStatus.UNKNOWN, result.validation.expiryStatus)
    }

    @Test
    public fun `future birth is reported under explicit strict policy`() {
        val strictParser =
            Td3MrzParser(
                validator =
                    MrzValidator(
                        WindowedMrzDateInterpreter(MrzDatePolicy(maximumBirthAgeYears = 90)),
                    ),
            )
        val result =
            strictParser.parse(
                SyntheticTd3Fixtures.build(birthDate = "270101"),
                referenceDate,
            ) as MrzParseResult.Parsed

        assertIssue(result, MrzValidationIssueType.FUTURE_BIRTH_DATE, MrzField.DATE_OF_BIRTH)
        assertNull(result.document.dateOfBirth)
    }

    @Test
    public fun `expired passport remains structurally and cryptographically consistent`() {
        val result = parsed(SyntheticTd3Fixtures.build(expiryDate = "250101"))

        assertEquals(MrzExpiryStatus.EXPIRED, result.validation.expiryStatus)
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `birth century ambiguity is visible and deterministic`() {
        val result = parsed(SyntheticTd3Fixtures.build(birthDate = "200101"))

        assertIssue(result, MrzValidationIssueType.AMBIGUOUS_CENTURY, MrzField.DATE_OF_BIRTH)
        assertEquals(LocalDate.of(2020, 1, 1), result.document.dateOfBirth?.preferredValue)
        assertEquals(MrzConfidence.AMBIGUOUS, result.validation.confidence)
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `O to zero ambiguity is corrected only in numeric date field`() {
        val base = SyntheticTd3Fixtures.build(birthDate = "000101")
        val input = SyntheticTd3Fixtures.replace(base, 1, 13, 'O')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.AMBIGUOUS_CHARACTER, MrzField.DATE_OF_BIRTH)
        assertEquals(
            'O',
            result.validation.corrections
                .single()
                .observed,
        )
        assertEquals(
            '0',
            result.validation.corrections
                .single()
                .selected,
        )
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `I to one ambiguity is corrected only in numeric date field`() {
        val base = SyntheticTd3Fixtures.build(birthDate = "910101")
        val input = SyntheticTd3Fixtures.replace(base, 1, 14, 'I')
        val result = parsed(input)

        assertIssue(result, MrzValidationIssueType.AMBIGUOUS_CHARACTER, MrzField.DATE_OF_BIRTH)
        assertEquals(
            '1',
            result.validation.corrections
                .single()
                .selected,
        )
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `digit in issuing-state field is not converted`() {
        val result = parsed(SyntheticTd3Fixtures.build(issuingState = "UT0"))

        assertIssue(result, MrzValidationIssueType.INVALID_ISSUING_STATE, MrzField.ISSUING_STATE)
        assertFalse(result.validation.structurallyValid)
    }

    @Test
    public fun `digit in nationality field is not converted`() {
        val result = parsed(SyntheticTd3Fixtures.build(nationality = "UT0"))

        assertIssue(result, MrzValidationIssueType.INVALID_NATIONALITY, MrzField.NATIONALITY)
    }

    @Test
    public fun `name without double filler separator is reported`() {
        val nameWithoutSeparator = List(20) { "A" }.joinToString("<")
        val result = parsed(SyntheticTd3Fixtures.build(name = nameWithoutSeparator))

        assertEquals(39, nameWithoutSeparator.length)
        assertIssue(result, MrzValidationIssueType.MISSING_NAME_SEPARATOR, MrzField.NAME)
    }

    @Test
    public fun `sex markers M F and filler map without inference`() {
        val expectations =
            mapOf(
                'M' to MrzSex.MALE,
                'F' to MrzSex.FEMALE,
                '<' to MrzSex.UNSPECIFIED,
            )

        expectations.forEach { (marker, expected) ->
            assertEquals(expected, parsed(SyntheticTd3Fixtures.build(sex = marker)).document.sex)
        }
    }

    @Test
    public fun `invalid sex marker is reported`() {
        val result = parsed(SyntheticTd3Fixtures.build(sex = '1'))

        assertIssue(result, MrzValidationIssueType.INVALID_SEX_MARKER, MrzField.SEX)
    }

    @Test
    public fun `non-passport TD3 variant is unsupported`() {
        val result = parsed(SyntheticTd3Fixtures.build(documentCode = "X<"))

        assertIssue(result, MrzValidationIssueType.UNSUPPORTED_TD3_VARIANT, MrzField.DOCUMENT_CODE)
        assertEquals(MrzFailure.UNSUPPORTED_FORMAT, result.validation.toIdvErrorOrNull()?.reason)
    }

    @Test
    public fun `malformed second document-code character is reported`() {
        val result = parsed(SyntheticTd3Fixtures.build(documentCode = "P0"))

        assertIssue(result, MrzValidationIssueType.INVALID_DOCUMENT_CODE, MrzField.DOCUMENT_CODE)
    }

    @Test
    public fun `empty document number is reported`() {
        val result = parsed(SyntheticTd3Fixtures.build(documentNumber = "<<<<<<<<<"))

        assertIssue(result, MrzValidationIssueType.INVALID_DOCUMENT_NUMBER, MrzField.DOCUMENT_NUMBER)
    }

    @Test
    public fun `one-line lowercase and spaced input remains traceable`() {
        val oneLine = SyntheticTd3Fixtures.valid.replace("\n", "").lowercase()
        val result = parsed(SyntheticTd3Fixtures.addOcrSpaces(oneLine))

        val changes =
            result.validation.normalizationChanges
                .map { it.type }
                .toSet()
        assertTrue(MrzNormalizationChangeType.SINGLE_SEQUENCE_SPLIT in changes)
        assertTrue(MrzNormalizationChangeType.ASCII_CASE_FOLDED in changes)
        assertTrue(MrzNormalizationChangeType.OCR_SPACES_REMOVED in changes)
        assertTrue(result.validation.isFormatAndCheckDigitValid)
    }

    @Test
    public fun `incorrect line count is a safe fatal rejection`() {
        val input = SyntheticTd3Fixtures.lines(SyntheticTd3Fixtures.valid).first()
        val result = rejected(input)

        assertEquals(MrzParseError.MALFORMED_INPUT, result.error)
        assertIssue(result, MrzValidationIssueType.INCORRECT_LINE_COUNT)
    }

    @Test
    public fun `unsupported character is a safe fatal rejection`() {
        val input = SyntheticTd3Fixtures.replace(SyntheticTd3Fixtures.valid, 0, 12, '@')
        val result = rejected(input)

        assertEquals(MrzParseError.UNSUPPORTED_CHARACTER, result.error)
        assertEquals(MrzFailure.MALFORMED, result.error.toIdvError().reason)
    }

    private fun parsed(input: String): MrzParseResult.Parsed<Td3PassportMrz> {
        val result = parser.parse(input, referenceDate)
        assertTrue(result is MrzParseResult.Parsed)
        @Suppress("UNCHECKED_CAST")
        return result as MrzParseResult.Parsed<Td3PassportMrz>
    }

    private fun rejected(input: String): MrzParseResult.Rejected {
        val result = parser.parse(input, referenceDate)
        assertTrue(result is MrzParseResult.Rejected)
        return result as MrzParseResult.Rejected
    }

    private fun assertIssue(
        result: MrzParseResult.Parsed<Td3PassportMrz>,
        type: MrzValidationIssueType,
        field: MrzField? = null,
    ) {
        assertTrue(result.validation.issues.any { it.type == type && it.field == field })
    }

    private fun assertIssue(
        result: MrzParseResult.Rejected,
        type: MrzValidationIssueType,
    ) {
        assertTrue(result.validation.issues.any { it.type == type })
    }
}
