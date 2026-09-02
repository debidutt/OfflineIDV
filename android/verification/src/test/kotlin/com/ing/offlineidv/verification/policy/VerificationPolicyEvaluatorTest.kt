package com.ing.offlineidv.verification.policy

import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationTerminalReason
import org.junit.Assert.assertEquals
import org.junit.Test

public class VerificationPolicyEvaluatorTest {
    private val completeEvidence =
        setOf(
            VerificationEvidence.MRZ_STRUCTURE_VALID,
            VerificationEvidence.MRZ_CHECK_DIGITS_VALID,
            VerificationEvidence.NFC_CHIP_READ,
            VerificationEvidence.PRINTED_CHIP_DATA_MATCH,
            VerificationEvidence.PASSIVE_AUTHENTICATION_VALID,
            VerificationEvidence.FACE_MATCH_ACCEPTED,
        )

    @Test
    public fun `all required evidence verifies`() {
        assertEquals(
            VerificationOutcome.VERIFIED,
            VerificationPolicyEvaluator.evaluate(VerificationPolicy(), completeEvidence).outcome,
        )
    }

    @Test
    public fun `invalid required structure rejects`() {
        assertRejected(VerificationEvidence.MRZ_STRUCTURE_INVALID)
    }

    @Test
    public fun `invalid required checksum rejects`() {
        assertRejected(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID)
    }

    @Test
    public fun `expired document rejects only when configured`() {
        val expired = completeEvidence + VerificationEvidence.DOCUMENT_EXPIRED

        assertEquals(
            VerificationOutcome.REJECTED,
            VerificationPolicyEvaluator.evaluate(VerificationPolicy(), expired).outcome,
        )
        assertEquals(
            VerificationOutcome.VERIFIED,
            VerificationPolicyEvaluator
                .evaluate(
                    VerificationPolicy(rejectExpiredDocument = false),
                    expired,
                ).outcome,
        )
    }

    @Test
    public fun `printed chip mismatch rejects only when required`() {
        val evidence =
            completeEvidence - VerificationEvidence.PRINTED_CHIP_DATA_MATCH +
                VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH

        assertEquals(
            VerificationOutcome.REJECTED,
            VerificationPolicyEvaluator.evaluate(VerificationPolicy(), evidence).outcome,
        )
        assertEquals(
            VerificationOutcome.VERIFIED,
            VerificationPolicyEvaluator
                .evaluate(
                    VerificationPolicy(requirePrintedChipConsistency = false),
                    evidence,
                ).outcome,
        )
    }

    @Test
    public fun `face rejection is an explicit policy rejection`() {
        val evidence =
            completeEvidence - VerificationEvidence.FACE_MATCH_ACCEPTED +
                VerificationEvidence.FACE_MATCH_REJECTED

        assertEquals(
            VerificationOutcome.REJECTED,
            VerificationPolicyEvaluator.evaluate(VerificationPolicy(), evidence).outcome,
        )
    }

    @Test
    public fun `ambiguous MRZ is inconclusive rather than rejected`() {
        val decision =
            VerificationPolicyEvaluator.evaluate(
                VerificationPolicy(),
                completeEvidence + VerificationEvidence.MRZ_CHARACTER_AMBIGUITY,
            )

        assertEquals(VerificationOutcome.INCONCLUSIVE, decision.outcome)
        assertEquals(VerificationTerminalReason.INSUFFICIENT_EVIDENCE, decision.reason)
    }

    @Test
    public fun `unknown expiry is inconclusive when expiration matters`() {
        val decision =
            VerificationPolicyEvaluator.evaluate(
                VerificationPolicy(),
                completeEvidence + VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN,
            )

        assertEquals(VerificationOutcome.INCONCLUSIVE, decision.outcome)
    }

    @Test
    public fun `missing required evidence is inconclusive`() {
        val decision =
            VerificationPolicyEvaluator.evaluate(
                VerificationPolicy(),
                completeEvidence - VerificationEvidence.NFC_CHIP_READ,
            )

        assertEquals(VerificationOutcome.INCONCLUSIVE, decision.outcome)
    }

    @Test
    public fun `required unavailable capability is distinguishable`() {
        val decision =
            VerificationPolicyEvaluator.evaluate(
                VerificationPolicy(),
                completeEvidence +
                    setOf(
                        VerificationEvidence.CAPABILITY_UNAVAILABLE,
                        VerificationEvidence.REQUIRED_STEP_SKIPPED,
                    ),
            )

        assertEquals(VerificationOutcome.INCONCLUSIVE, decision.outcome)
        assertEquals(VerificationTerminalReason.REQUIRED_CAPABILITY_UNAVAILABLE, decision.reason)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `printed chip requirement cannot exist without NFC`() {
        VerificationPolicy(requireNfcRead = false, requirePrintedChipConsistency = true)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `passive authentication requirement cannot exist without NFC`() {
        VerificationPolicy(
            requireNfcRead = false,
            requirePrintedChipConsistency = false,
            requirePassiveAuthentication = true,
        )
    }

    private fun assertRejected(rejectingEvidence: VerificationEvidence) {
        val evidence =
            when (rejectingEvidence) {
                VerificationEvidence.MRZ_STRUCTURE_INVALID -> {
                    completeEvidence - VerificationEvidence.MRZ_STRUCTURE_VALID + rejectingEvidence
                }

                VerificationEvidence.MRZ_CHECK_DIGITS_INVALID -> {
                    completeEvidence - VerificationEvidence.MRZ_CHECK_DIGITS_VALID + rejectingEvidence
                }

                else -> {
                    completeEvidence + rejectingEvidence
                }
            }
        assertEquals(
            VerificationOutcome.REJECTED,
            VerificationPolicyEvaluator.evaluate(VerificationPolicy(), evidence).outcome,
        )
    }
}
