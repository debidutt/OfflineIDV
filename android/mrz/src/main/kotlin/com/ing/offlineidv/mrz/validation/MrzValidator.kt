package com.ing.offlineidv.mrz.validation

import com.ing.offlineidv.mrz.checkdigit.MrzCheckDigitCalculation
import com.ing.offlineidv.mrz.checkdigit.MrzCheckDigitCalculator
import com.ing.offlineidv.mrz.date.MrzDateFailure
import com.ing.offlineidv.mrz.date.MrzDateInterpretation
import com.ing.offlineidv.mrz.date.MrzDateInterpreter
import com.ing.offlineidv.mrz.date.WindowedMrzDateInterpreter
import com.ing.offlineidv.mrz.model.CheckDigitResult
import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzAmbiguity
import com.ing.offlineidv.mrz.model.MrzConfidence
import com.ing.offlineidv.mrz.model.MrzDate
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzField
import com.ing.offlineidv.mrz.model.MrzIssueSeverity
import com.ing.offlineidv.mrz.model.MrzName
import com.ing.offlineidv.mrz.model.MrzNormalizationResult
import com.ing.offlineidv.mrz.model.MrzParseError
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzSex
import com.ing.offlineidv.mrz.model.MrzValidationIssue
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.mrz.model.Td3PassportMrz
import java.time.LocalDate

/** Validates an opaque normalized result and builds a TD3 domain model when structure permits. */
public class MrzValidator(
    private val dateInterpreter: MrzDateInterpreter = WindowedMrzDateInterpreter(),
) {
    /** Aggregates semantic, ambiguity, date, and checksum evidence without exposing raw lines. */
    public fun validate(
        normalization: MrzNormalizationResult,
        referenceDate: LocalDate,
    ): MrzParseResult<Td3PassportMrz> =
        when (normalization) {
            is MrzNormalizationResult.Failure -> reject(normalization)
            is MrzNormalizationResult.Success -> validateTd3(normalization, referenceDate)
        }

    private fun reject(normalization: MrzNormalizationResult.Failure): MrzParseResult.Rejected {
        val validation =
            MrzValidationResult(
                structurallyValid = false,
                checkDigits = emptyMap(),
                issues = normalization.issues,
                corrections = emptyList(),
                normalizationChanges = normalization.changes,
                expiryStatus = MrzExpiryStatus.UNKNOWN,
                confidence = MrzConfidence.INVALID,
            )
        val error =
            if (normalization.issues.any {
                    it.type == MrzValidationIssueType.UNSUPPORTED_CHARACTER
                }
            ) {
                MrzParseError.UNSUPPORTED_CHARACTER
            } else {
                MrzParseError.MALFORMED_INPUT
            }
        return MrzParseResult.Rejected(error, validation)
    }

    private fun validateTd3(
        normalization: MrzNormalizationResult.Success,
        referenceDate: LocalDate,
    ): MrzParseResult.Parsed<Td3PassportMrz> {
        val line1 = normalization.lines[0]
        val line2 = normalization.lines[1]
        val issues = mutableListOf<MrzValidationIssue>()
        val ambiguities = mutableListOf<MrzAmbiguity>()
        val checkDigits = linkedMapOf<MrzField, CheckDigitResult>()

        val documentCode = line1.substring(DOCUMENT_CODE_RANGE)
        validateDocumentCode(documentCode, issues)

        val issuingState = line1.substring(ISSUING_STATE_RANGE)
        if (!issuingState.isAlphabeticCode()) {
            issues += issue(MrzValidationIssueType.INVALID_ISSUING_STATE, MrzField.ISSUING_STATE)
        }

        val nameField = line1.substring(NAME_RANGE)
        val name = parseName(nameField, issues)

        val documentNumberField = line2.substring(DOCUMENT_NUMBER_RANGE)
        val documentNumber = documentNumberField.trimEnd(FILLER)
        if (documentNumber.isEmpty()) {
            issues += issue(MrzValidationIssueType.INVALID_DOCUMENT_NUMBER, MrzField.DOCUMENT_NUMBER)
        }

        val nationality = line2.substring(NATIONALITY_RANGE)
        if (!nationality.isAlphabeticCode()) {
            issues += issue(MrzValidationIssueType.INVALID_NATIONALITY, MrzField.NATIONALITY)
        }

        val birthCandidate =
            numericCandidate(
                line2.substring(BIRTH_DATE_RANGE),
                MrzField.DATE_OF_BIRTH,
                ambiguities,
                issues,
            )
        val birthInterpretation = dateInterpreter.interpretBirthDate(birthCandidate, referenceDate)
        val birthDate = dateFromInterpretation(birthInterpretation, MrzField.DATE_OF_BIRTH, issues)

        val sex = parseSex(line2[SEX_INDEX], issues)

        val expiryCandidate =
            numericCandidate(
                line2.substring(EXPIRY_DATE_RANGE),
                MrzField.EXPIRY_DATE,
                ambiguities,
                issues,
            )
        val expiryInterpretation = dateInterpreter.interpretExpiryDate(expiryCandidate, referenceDate)
        val expiryDate = dateFromInterpretation(expiryInterpretation, MrzField.EXPIRY_DATE, issues)
        val expiryStatus = expiryStatus(expiryInterpretation)

        val optionalDataField = line2.substring(OPTIONAL_DATA_RANGE)
        val personalNumber = optionalDataField.trimEnd(FILLER).ifEmpty { null }

        validateMandatoryCheckDigit(
            field = MrzField.DOCUMENT_NUMBER,
            source = documentNumberField,
            actual = line2[DOCUMENT_NUMBER_CHECK_INDEX],
            issues = issues,
            results = checkDigits,
        )
        validateMandatoryCheckDigit(
            field = MrzField.DATE_OF_BIRTH,
            source = birthCandidate,
            actual = line2[BIRTH_DATE_CHECK_INDEX],
            issues = issues,
            results = checkDigits,
        )
        validateMandatoryCheckDigit(
            field = MrzField.EXPIRY_DATE,
            source = expiryCandidate,
            actual = line2[EXPIRY_DATE_CHECK_INDEX],
            issues = issues,
            results = checkDigits,
        )
        validateOptionalCheckDigit(
            source = optionalDataField,
            actual = line2[OPTIONAL_DATA_CHECK_INDEX],
            issues = issues,
            results = checkDigits,
        )

        val compositeSource =
            documentNumberField +
                line2[DOCUMENT_NUMBER_CHECK_INDEX] +
                birthCandidate +
                line2[BIRTH_DATE_CHECK_INDEX] +
                expiryCandidate +
                line2[EXPIRY_DATE_CHECK_INDEX] +
                optionalDataField +
                line2[OPTIONAL_DATA_CHECK_INDEX]
        validateMandatoryCheckDigit(
            field = MrzField.COMPOSITE_CHECK_DIGIT,
            source = compositeSource,
            actual = line2[COMPOSITE_CHECK_INDEX],
            issues = issues,
            results = checkDigits,
            composite = true,
        )

        val structurallyValid =
            issues.none {
                it.type.severity == MrzIssueSeverity.ERROR &&
                    it.type != MrzValidationIssueType.CHECK_DIGIT_MISMATCH &&
                    it.type != MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH
            }
        val confidence =
            when {
                issues.any { it.type.severity == MrzIssueSeverity.ERROR } -> {
                    MrzConfidence.INVALID
                }

                ambiguities.isNotEmpty() ||
                    issues.any { it.type == MrzValidationIssueType.AMBIGUOUS_CENTURY } -> {
                    MrzConfidence.AMBIGUOUS
                }

                else -> {
                    MrzConfidence.HIGH
                }
            }
        val validation =
            MrzValidationResult(
                structurallyValid = structurallyValid,
                checkDigits = checkDigits,
                issues = issues,
                corrections = ambiguities,
                normalizationChanges = normalization.changes,
                expiryStatus = expiryStatus,
                confidence = confidence,
            )
        val document =
            Td3PassportMrz(
                documentCode = documentCode,
                issuingState = issuingState,
                name = name,
                documentNumber = documentNumber,
                nationality = nationality,
                dateOfBirth = birthDate,
                sex = sex,
                expiryDate = expiryDate,
                personalNumber = personalNumber,
            )
        return MrzParseResult.Parsed(document, validation)
    }

    private fun validateDocumentCode(
        documentCode: String,
        issues: MutableList<MrzValidationIssue>,
    ) {
        if (documentCode.first() != PASSPORT_DOCUMENT_CODE) {
            issues +=
                issue(
                    MrzValidationIssueType.UNSUPPORTED_TD3_VARIANT,
                    MrzField.DOCUMENT_CODE,
                )
        }
        if (documentCode[1] != FILLER && documentCode[1] !in 'A'..'Z') {
            issues += issue(MrzValidationIssueType.INVALID_DOCUMENT_CODE, MrzField.DOCUMENT_CODE)
        }
    }

    private fun parseName(
        field: String,
        issues: MutableList<MrzValidationIssue>,
    ): MrzName {
        val separatorIndex = field.indexOf(NAME_SEPARATOR)
        if (separatorIndex < 0) {
            issues += issue(MrzValidationIssueType.MISSING_NAME_SEPARATOR, MrzField.NAME)
            return MrzName(presentationWords(field), emptyList())
        }
        val surname = presentationWords(field.substring(0, separatorIndex))
        val givenNames = presentationWords(field.substring(separatorIndex + NAME_SEPARATOR.length))
        return MrzName(surname, givenNames.split(' ').filter { it.isNotEmpty() })
    }

    private fun presentationWords(value: String): String =
        value
            .replace(FILLER, ' ')
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(separator = " ")

    private fun parseSex(
        marker: Char,
        issues: MutableList<MrzValidationIssue>,
    ): MrzSex =
        when (marker) {
            'M' -> {
                MrzSex.MALE
            }

            'F' -> {
                MrzSex.FEMALE
            }

            FILLER -> {
                MrzSex.UNSPECIFIED
            }

            else -> {
                issues += issue(MrzValidationIssueType.INVALID_SEX_MARKER, MrzField.SEX)
                MrzSex.UNSPECIFIED
            }
        }

    private fun numericCandidate(
        value: String,
        field: MrzField,
        ambiguities: MutableList<MrzAmbiguity>,
        issues: MutableList<MrzValidationIssue>,
    ): String =
        value
            .mapIndexed { index, character ->
                when (character) {
                    in '0'..'9' -> {
                        character
                    }

                    'O' -> {
                        ambiguities +=
                            MrzAmbiguity(field, index, character, setOf('O', '0'), selected = '0')
                        issues +=
                            issue(
                                MrzValidationIssueType.AMBIGUOUS_CHARACTER,
                                field,
                                characterIndex = index,
                            )
                        '0'
                    }

                    'I' -> {
                        ambiguities +=
                            MrzAmbiguity(field, index, character, setOf('I', '1'), selected = '1')
                        issues +=
                            issue(
                                MrzValidationIssueType.AMBIGUOUS_CHARACTER,
                                field,
                                characterIndex = index,
                            )
                        '1'
                    }

                    else -> {
                        character
                    }
                }
            }.joinToString(separator = "")

    private fun dateFromInterpretation(
        interpretation: MrzDateInterpretation,
        field: MrzField,
        issues: MutableList<MrzValidationIssue>,
    ): MrzDate? =
        when (interpretation) {
            is MrzDateInterpretation.Resolved -> {
                interpretation.date
            }

            is MrzDateInterpretation.Ambiguous -> {
                issues += issue(MrzValidationIssueType.AMBIGUOUS_CENTURY, field)
                interpretation.date
            }

            is MrzDateInterpretation.Failure -> {
                val type =
                    when (interpretation.reason) {
                        MrzDateFailure.MALFORMED -> {
                            MrzValidationIssueType.INVALID_DATE_FORMAT
                        }

                        MrzDateFailure.IMPOSSIBLE -> {
                            MrzValidationIssueType.IMPOSSIBLE_DATE
                        }

                        MrzDateFailure.FUTURE_BIRTH_DATE -> {
                            MrzValidationIssueType.FUTURE_BIRTH_DATE
                        }
                    }
                issues += issue(type, field)
                null
            }
        }

    private fun expiryStatus(interpretation: MrzDateInterpretation): MrzExpiryStatus =
        when (interpretation) {
            is MrzDateInterpretation.Resolved -> interpretation.expiryStatus

            is MrzDateInterpretation.Ambiguous,
            is MrzDateInterpretation.Failure,
            -> MrzExpiryStatus.UNKNOWN
        }

    private fun validateMandatoryCheckDigit(
        field: MrzField,
        source: String,
        actual: Char,
        issues: MutableList<MrzValidationIssue>,
        results: MutableMap<MrzField, CheckDigitResult>,
        composite: Boolean = false,
    ) {
        if (actual !in '0'..'9') {
            results[field] = CheckDigitResult(field, CheckDigitStatus.MALFORMED, actual = actual)
            issues += issue(MrzValidationIssueType.INVALID_CHECK_DIGIT_CHARACTER, field)
            return
        }
        val expected =
            when (val calculation = MrzCheckDigitCalculator.calculate(source)) {
                is MrzCheckDigitCalculation.Success -> {
                    calculation.digit
                }

                MrzCheckDigitCalculation.UnsupportedCharacter -> {
                    results[field] = CheckDigitResult(field, CheckDigitStatus.MALFORMED, actual = actual)
                    issues += issue(MrzValidationIssueType.INVALID_CHECK_DIGIT_CHARACTER, field)
                    return
                }
            }
        val status = if (expected == actual) CheckDigitStatus.VALID else CheckDigitStatus.MISMATCH
        results[field] = CheckDigitResult(field, status, expected, actual)
        if (status == CheckDigitStatus.MISMATCH) {
            issues +=
                issue(
                    if (composite) {
                        MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH
                    } else {
                        MrzValidationIssueType.CHECK_DIGIT_MISMATCH
                    },
                    field,
                )
        }
    }

    private fun validateOptionalCheckDigit(
        source: String,
        actual: Char,
        issues: MutableList<MrzValidationIssue>,
        results: MutableMap<MrzField, CheckDigitResult>,
    ) {
        val field = MrzField.OPTIONAL_DATA
        if (source.all { it == FILLER } && actual == FILLER) {
            results[field] = CheckDigitResult(field, CheckDigitStatus.NOT_APPLICABLE, actual = actual)
            return
        }
        validateMandatoryCheckDigit(field, source, actual, issues, results)
    }

    private fun issue(
        type: MrzValidationIssueType,
        field: MrzField? = null,
        characterIndex: Int? = null,
    ): MrzValidationIssue =
        MrzValidationIssue(
            type = type,
            field = field,
            characterIndex = characterIndex,
        )

    private fun String.isAlphabeticCode(): Boolean = length == CODE_LENGTH && all { it in 'A'..'Z' }

    private companion object {
        private const val CODE_LENGTH: Int = 3
        private const val PASSPORT_DOCUMENT_CODE: Char = 'P'
        private const val FILLER: Char = '<'
        private const val NAME_SEPARATOR: String = "<<"

        private val DOCUMENT_CODE_RANGE: IntRange = 0 until 2
        private val ISSUING_STATE_RANGE: IntRange = 2 until 5
        private val NAME_RANGE: IntRange = 5 until 44
        private val DOCUMENT_NUMBER_RANGE: IntRange = 0 until 9
        private const val DOCUMENT_NUMBER_CHECK_INDEX: Int = 9
        private val NATIONALITY_RANGE: IntRange = 10 until 13
        private val BIRTH_DATE_RANGE: IntRange = 13 until 19
        private const val BIRTH_DATE_CHECK_INDEX: Int = 19
        private const val SEX_INDEX: Int = 20
        private val EXPIRY_DATE_RANGE: IntRange = 21 until 27
        private const val EXPIRY_DATE_CHECK_INDEX: Int = 27
        private val OPTIONAL_DATA_RANGE: IntRange = 28 until 42
        private const val OPTIONAL_DATA_CHECK_INDEX: Int = 42
        private const val COMPOSITE_CHECK_INDEX: Int = 43
    }
}
