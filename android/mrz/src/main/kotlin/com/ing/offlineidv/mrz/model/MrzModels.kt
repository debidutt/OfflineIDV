package com.ing.offlineidv.mrz.model

import com.ing.offlineidv.core.security.Redaction
import java.time.LocalDate

/** MRZ formats understood by a domain document. */
public enum class MrzFormat {
    TD3,
}

/** Fixed TD3 fields, used for safe issue and checksum attribution. */
public enum class MrzField {
    DOCUMENT_CODE,
    ISSUING_STATE,
    NAME,
    DOCUMENT_NUMBER,
    DOCUMENT_NUMBER_CHECK_DIGIT,
    NATIONALITY,
    DATE_OF_BIRTH,
    DATE_OF_BIRTH_CHECK_DIGIT,
    SEX,
    EXPIRY_DATE,
    EXPIRY_DATE_CHECK_DIGIT,
    OPTIONAL_DATA,
    OPTIONAL_DATA_CHECK_DIGIT,
    COMPOSITE_CHECK_DIGIT,
}

/** Sex marker encoded by TD3. No inference beyond the MRZ marker is performed. */
public enum class MrzSex {
    MALE,
    FEMALE,
    UNSPECIFIED,
}

/** Evidence describing the resolved document-expiry relationship to the reference date. */
public enum class MrzExpiryStatus {
    VALID,
    EXPIRES_TODAY,
    EXPIRED,
    UNKNOWN,
}

/** Common contract for parsed MRZ documents. */
public interface MrzDocument {
    public val format: MrzFormat
}

/** Parsed MRZ name components with an always-redacted string representation. */
public class MrzName(
    public val surname: String,
    givenNames: List<String>,
) {
    public val givenNames: List<String> = givenNames.toList()

    override fun equals(other: Any?): Boolean = other is MrzName && surname == other.surname && givenNames == other.givenNames

    override fun hashCode(): Int = 31 * surname.hashCode() + givenNames.hashCode()

    override fun toString(): String = "MrzName(${Redaction.MARKER})"
}

/**
 * Policy-based interpretation of a six-digit MRZ date.
 *
 * [preferredValue] is deterministic, while [alternativeValues] preserves any unresolved century
 * ambiguity. Dates are sensitive and therefore never included in [toString].
 */
public class MrzDate(
    public val preferredValue: LocalDate,
    alternativeValues: List<LocalDate> = emptyList(),
) {
    public val alternativeValues: List<LocalDate> =
        alternativeValues.filterNot { it == preferredValue }.distinct().sorted()

    public val isAmbiguous: Boolean
        get() = alternativeValues.isNotEmpty()

    override fun equals(other: Any?): Boolean =
        other is MrzDate &&
            preferredValue == other.preferredValue &&
            alternativeValues == other.alternativeValues

    override fun hashCode(): Int = 31 * preferredValue.hashCode() + alternativeValues.hashCode()

    override fun toString(): String = "MrzDate(${Redaction.MARKER})"
}

/**
 * Parsed TD3 passport fields.
 *
 * The properties are sensitive domain data intended for trusted in-memory consumers. The complete
 * normalized lines are deliberately not retained, and [toString] never renders any field.
 */
public class Td3PassportMrz(
    public val documentCode: String,
    public val issuingState: String,
    public val name: MrzName,
    public val documentNumber: String,
    public val nationality: String,
    public val dateOfBirth: MrzDate?,
    public val sex: MrzSex,
    public val expiryDate: MrzDate?,
    public val personalNumber: String?,
) : MrzDocument {
    override val format: MrzFormat = MrzFormat.TD3

    override fun toString(): String = "Td3PassportMrz(${Redaction.MARKER})"
}
