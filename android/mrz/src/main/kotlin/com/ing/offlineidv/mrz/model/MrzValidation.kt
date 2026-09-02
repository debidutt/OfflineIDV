package com.ing.offlineidv.mrz.model

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.MrzFailure
import com.ing.offlineidv.core.security.Redaction

/** Severity of a structured MRZ validation issue. */
public enum class MrzIssueSeverity {
    ERROR,
    WARNING,
}

/** Closed set of safe validation issue types. */
public enum class MrzValidationIssueType(
    public val stableCode: String,
    public val severity: MrzIssueSeverity,
) {
    INCORRECT_LINE_COUNT("mrz.structure.incorrect_line_count", MrzIssueSeverity.ERROR),
    INCORRECT_LINE_LENGTH("mrz.structure.incorrect_line_length", MrzIssueSeverity.ERROR),
    UNSUPPORTED_CHARACTER("mrz.structure.unsupported_character", MrzIssueSeverity.ERROR),
    INVALID_DOCUMENT_CODE("mrz.field.invalid_document_code", MrzIssueSeverity.ERROR),
    INVALID_ISSUING_STATE("mrz.field.invalid_issuing_state", MrzIssueSeverity.ERROR),
    INVALID_NATIONALITY("mrz.field.invalid_nationality", MrzIssueSeverity.ERROR),
    INVALID_SEX_MARKER("mrz.field.invalid_sex_marker", MrzIssueSeverity.ERROR),
    INVALID_DOCUMENT_NUMBER("mrz.field.invalid_document_number", MrzIssueSeverity.ERROR),
    INVALID_DATE_FORMAT("mrz.date.invalid_format", MrzIssueSeverity.ERROR),
    IMPOSSIBLE_DATE("mrz.date.impossible", MrzIssueSeverity.ERROR),
    FUTURE_BIRTH_DATE("mrz.date.future_birth", MrzIssueSeverity.ERROR),
    CHECK_DIGIT_MISMATCH("mrz.check_digit.mismatch", MrzIssueSeverity.ERROR),
    COMPOSITE_CHECK_DIGIT_MISMATCH("mrz.check_digit.composite_mismatch", MrzIssueSeverity.ERROR),
    INVALID_CHECK_DIGIT_CHARACTER("mrz.check_digit.invalid_character", MrzIssueSeverity.ERROR),
    AMBIGUOUS_CHARACTER("mrz.ambiguity.character", MrzIssueSeverity.WARNING),
    AMBIGUOUS_CENTURY("mrz.ambiguity.century", MrzIssueSeverity.WARNING),
    MISSING_NAME_SEPARATOR("mrz.name.missing_separator", MrzIssueSeverity.ERROR),
    UNSUPPORTED_TD3_VARIANT("mrz.structure.unsupported_td3_variant", MrzIssueSeverity.ERROR),
}

/**
 * Non-sensitive validation issue metadata.
 *
 * Locations use zero-based indexes and never contain the character or field value at that location.
 */
public data class MrzValidationIssue(
    public val type: MrzValidationIssueType,
    public val field: MrzField? = null,
    public val lineIndex: Int? = null,
    public val characterIndex: Int? = null,
    public val actualLength: Int? = null,
) {
    override fun toString(): String =
        "MrzValidationIssue(code=${type.stableCode}, field=$field, " +
            "lineIndex=$lineIndex, characterIndex=$characterIndex)"
}

/** A visible, field-scoped OCR ambiguity and the deterministic candidate selected by policy. */
public class MrzAmbiguity(
    public val field: MrzField,
    public val characterIndex: Int,
    public val observed: Char,
    candidates: Set<Char>,
    public val selected: Char,
) {
    public val candidates: Set<Char> = candidates.toSet()

    override fun equals(other: Any?): Boolean =
        other is MrzAmbiguity &&
            field == other.field &&
            characterIndex == other.characterIndex &&
            observed == other.observed &&
            candidates == other.candidates &&
            selected == other.selected

    override fun hashCode(): Int {
        var result = field.hashCode()
        result = 31 * result + characterIndex
        result = 31 * result + observed.hashCode()
        result = 31 * result + candidates.hashCode()
        return 31 * result + selected.hashCode()
    }

    override fun toString(): String = "MrzAmbiguity(field=$field, characterIndex=$characterIndex, candidates=${candidates.size})"
}

/** Confidence in a format/checksum interpretation, not document authenticity. */
public enum class MrzConfidence {
    HIGH,
    AMBIGUOUS,
    INVALID,
}

/** Status of one ICAO check-digit comparison. */
public enum class CheckDigitStatus {
    VALID,
    MISMATCH,
    NOT_APPLICABLE,
    MALFORMED,
}

/** Safe result for one check-digit field. */
public class CheckDigitResult(
    public val field: MrzField,
    public val status: CheckDigitStatus,
    public val expected: Char? = null,
    public val actual: Char? = null,
) {
    override fun toString(): String = "CheckDigitResult(field=$field, status=$status)"
}

/** Aggregated format/checksum evidence for one TD3 candidate. */
public class MrzValidationResult(
    public val structurallyValid: Boolean,
    checkDigits: Map<MrzField, CheckDigitResult>,
    issues: List<MrzValidationIssue>,
    corrections: List<MrzAmbiguity>,
    normalizationChanges: List<MrzNormalizationChange>,
    public val expiryStatus: MrzExpiryStatus,
    public val confidence: MrzConfidence,
) {
    public val checkDigits: Map<MrzField, CheckDigitResult> = checkDigits.toMap()
    public val issues: List<MrzValidationIssue> = issues.toList()
    public val corrections: List<MrzAmbiguity> = corrections.toList()
    public val normalizationChanges: List<MrzNormalizationChange> = normalizationChanges.toList()

    /** Whether structure, semantic fields, dates, and every applicable checksum are consistent. */
    public val isFormatAndCheckDigitValid: Boolean
        get() =
            structurallyValid &&
                issues.none { it.type.severity == MrzIssueSeverity.ERROR } &&
                checkDigits.values.all {
                    it.status == CheckDigitStatus.VALID ||
                        it.status == CheckDigitStatus.NOT_APPLICABLE
                }

    /** Maps invalid evidence to the existing safe SDK error hierarchy. */
    public fun toIdvErrorOrNull(): IdvError? {
        if (isFormatAndCheckDigitValid) return null
        val types = issues.map { it.type }.toSet()
        val reason =
            when {
                MrzValidationIssueType.UNSUPPORTED_TD3_VARIANT in types -> MrzFailure.UNSUPPORTED_FORMAT

                MrzValidationIssueType.INVALID_DATE_FORMAT in types ||
                    MrzValidationIssueType.IMPOSSIBLE_DATE in types ||
                    MrzValidationIssueType.FUTURE_BIRTH_DATE in types -> MrzFailure.DATE_INVALID

                MrzValidationIssueType.CHECK_DIGIT_MISMATCH in types ||
                    MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH in types -> MrzFailure.CHECKSUM_INVALID

                else -> MrzFailure.MALFORMED
            }
        return IdvError.Mrz(reason)
    }

    override fun toString(): String =
        "MrzValidationResult(structurallyValid=$structurallyValid, " +
            "issueCodes=${issues.map { it.type.stableCode }}, " +
            "corrections=${corrections.size}, expiryStatus=$expiryStatus, confidence=$confidence, " +
            "document=${Redaction.MARKER})"
}
