package com.ing.offlineidv.verification.mrz

import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationEvidence
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class MrzEvidenceMapperTest {
    @Test
    public fun `valid structure and checksums remain separate evidence`() {
        val summary = MrzEvidenceMapper.map(VerificationFixtures.validation())

        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in summary.evidence)
    }

    @Test
    public fun `malformed structure is not labelled checksum authenticity`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(
                    structurallyValid = false,
                    checkDigitStatus = CheckDigitStatus.MALFORMED,
                ),
            )

        assertTrue(VerificationEvidence.MRZ_STRUCTURE_INVALID in summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in summary.evidence)
        assertFalse(summary.toString().contains("authentic", ignoreCase = true))
    }

    @Test
    public fun `checksum mismatch maps to explicit invalid evidence`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(checkDigitStatus = CheckDigitStatus.MISMATCH),
            )

        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in summary.evidence)
    }

    @Test
    public fun `character ambiguity remains distinct from failure`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(
                    issues = listOf(VerificationFixtures.issue(MrzValidationIssueType.AMBIGUOUS_CHARACTER)),
                ),
            )

        assertTrue(VerificationEvidence.MRZ_CHARACTER_AMBIGUITY in summary.evidence)
        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in summary.evidence)
    }

    @Test
    public fun `century ambiguity remains explicit`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(
                    issues = listOf(VerificationFixtures.issue(MrzValidationIssueType.AMBIGUOUS_CENTURY)),
                ),
            )

        assertTrue(VerificationEvidence.MRZ_CENTURY_AMBIGUITY in summary.evidence)
    }

    @Test
    public fun `expired document remains distinct from malformed MRZ`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(expiryStatus = MrzExpiryStatus.EXPIRED),
            )

        assertTrue(VerificationEvidence.DOCUMENT_EXPIRED in summary.evidence)
        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in summary.evidence)
    }

    @Test
    public fun `unknown expiry maps without inventing an expiration result`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(expiryStatus = MrzExpiryStatus.UNKNOWN),
            )

        assertTrue(VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN in summary.evidence)
        assertFalse(VerificationEvidence.DOCUMENT_EXPIRED in summary.evidence)
    }
}
