package com.ing.offlineidv.verification.mrz

import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.verification.model.VerificationEvidence

/** Safe MRZ summary that contains evidence only and cannot expose parsed identity fields. */
public class MrzVerificationSummary internal constructor(
    evidence: Set<VerificationEvidence>,
) {
    public val evidence: Set<VerificationEvidence> = evidence.toSet()

    override fun equals(other: Any?): Boolean = other is MrzVerificationSummary && evidence == other.evidence

    override fun hashCode(): Int = evidence.hashCode()

    override fun toString(): String = "MrzVerificationSummary(evidence=$evidence)"
}

/** Maps Milestone 2 validation evidence without accepting a parsed document or raw MRZ. */
public object MrzEvidenceMapper {
    /** Translates structural, checksum, ambiguity, and expiry evidence into safe verification terms. */
    public fun map(validation: MrzValidationResult): MrzVerificationSummary {
        val evidence = linkedSetOf<VerificationEvidence>()
        evidence +=
            if (validation.structurallyValid) {
                VerificationEvidence.MRZ_STRUCTURE_VALID
            } else {
                VerificationEvidence.MRZ_STRUCTURE_INVALID
            }

        val checkDigitsValid =
            validation.checkDigits.isNotEmpty() &&
                validation.checkDigits.values.all { result ->
                    result.status == CheckDigitStatus.VALID ||
                        result.status == CheckDigitStatus.NOT_APPLICABLE
                }
        evidence +=
            if (checkDigitsValid) {
                VerificationEvidence.MRZ_CHECK_DIGITS_VALID
            } else {
                VerificationEvidence.MRZ_CHECK_DIGITS_INVALID
            }

        if (
            validation.corrections.isNotEmpty() ||
            validation.issues.any { it.type == MrzValidationIssueType.AMBIGUOUS_CHARACTER }
        ) {
            evidence += VerificationEvidence.MRZ_CHARACTER_AMBIGUITY
        }
        if (validation.issues.any { it.type == MrzValidationIssueType.AMBIGUOUS_CENTURY }) {
            evidence += VerificationEvidence.MRZ_CENTURY_AMBIGUITY
        }
        when (validation.expiryStatus) {
            MrzExpiryStatus.EXPIRED -> evidence += VerificationEvidence.DOCUMENT_EXPIRED

            MrzExpiryStatus.UNKNOWN -> evidence += VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN

            MrzExpiryStatus.EXPIRES_TODAY,
            MrzExpiryStatus.VALID,
            -> Unit
        }
        return MrzVerificationSummary(evidence)
    }
}
