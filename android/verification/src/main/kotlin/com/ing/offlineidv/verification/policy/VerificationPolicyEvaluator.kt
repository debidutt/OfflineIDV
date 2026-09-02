package com.ing.offlineidv.verification.policy

import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationTerminalReason

/** Safe deterministic policy decision whose evidence remains outside the outcome. */
public data class VerificationPolicyDecision(
    public val outcome: VerificationOutcome,
    public val reason: VerificationTerminalReason? = null,
) {
    init {
        require((outcome == VerificationOutcome.VERIFIED) == (reason == null)) {
            "Only a verified policy decision may omit a reason."
        }
        require(outcome in POLICY_OUTCOMES) { "Policy evaluation cannot create lifecycle outcomes." }
    }

    private companion object {
        val POLICY_OUTCOMES =
            setOf(
                VerificationOutcome.VERIFIED,
                VerificationOutcome.REJECTED,
                VerificationOutcome.INCONCLUSIVE,
            )
    }
}

/** Evaluates only finite evidence; it never sees an identity value or external engine. */
public object VerificationPolicyEvaluator {
    /** Applies [policy] to [evidence] with explicit reject-before-inconclusive precedence. */
    public fun evaluate(
        policy: VerificationPolicy,
        evidence: Set<VerificationEvidence>,
    ): VerificationPolicyDecision {
        if (hasRejectingEvidence(policy, evidence)) {
            return VerificationPolicyDecision(
                VerificationOutcome.REJECTED,
                VerificationTerminalReason.POLICY_REJECTED,
            )
        }
        if (hasUnavailableRequiredCapability(policy, evidence)) {
            return VerificationPolicyDecision(
                VerificationOutcome.INCONCLUSIVE,
                VerificationTerminalReason.REQUIRED_CAPABILITY_UNAVAILABLE,
            )
        }
        if (hasInsufficientEvidence(policy, evidence)) {
            return VerificationPolicyDecision(
                VerificationOutcome.INCONCLUSIVE,
                VerificationTerminalReason.INSUFFICIENT_EVIDENCE,
            )
        }
        return VerificationPolicyDecision(VerificationOutcome.VERIFIED)
    }

    /** Whether current evidence already determines a non-verified MRZ policy result. */
    public fun mrzRequiresEarlyDecision(
        policy: VerificationPolicy,
        evidence: Set<VerificationEvidence>,
    ): Boolean =
        (policy.requireMrzStructure && VerificationEvidence.MRZ_STRUCTURE_INVALID in evidence) ||
            (policy.requireMrzCheckDigits && VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in evidence) ||
            (policy.rejectExpiredDocument && VerificationEvidence.DOCUMENT_EXPIRED in evidence) ||
            VerificationEvidence.MRZ_CHARACTER_AMBIGUITY in evidence ||
            VerificationEvidence.MRZ_CENTURY_AMBIGUITY in evidence ||
            (policy.rejectExpiredDocument && VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN in evidence)

    private fun hasRejectingEvidence(
        policy: VerificationPolicy,
        evidence: Set<VerificationEvidence>,
    ): Boolean =
        (policy.requireMrzStructure && VerificationEvidence.MRZ_STRUCTURE_INVALID in evidence) ||
            (policy.requireMrzCheckDigits && VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in evidence) ||
            (policy.rejectExpiredDocument && VerificationEvidence.DOCUMENT_EXPIRED in evidence) ||
            (
                policy.requirePrintedChipConsistency &&
                    VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH in evidence
            ) ||
            (
                policy.requirePassiveAuthentication &&
                    VerificationEvidence.PASSIVE_AUTHENTICATION_FAILED in evidence
            ) ||
            (policy.requireFaceMatch && VerificationEvidence.FACE_MATCH_REJECTED in evidence)

    private fun hasUnavailableRequiredCapability(
        policy: VerificationPolicy,
        evidence: Set<VerificationEvidence>,
    ): Boolean =
        VerificationEvidence.CAPABILITY_UNAVAILABLE in evidence &&
            VerificationEvidence.REQUIRED_STEP_SKIPPED in evidence &&
            (policy.requireNfcRead || policy.requireFaceMatch)

    private fun hasInsufficientEvidence(
        policy: VerificationPolicy,
        evidence: Set<VerificationEvidence>,
    ): Boolean {
        if (
            VerificationEvidence.MRZ_CHARACTER_AMBIGUITY in evidence ||
            VerificationEvidence.MRZ_CENTURY_AMBIGUITY in evidence ||
            (policy.rejectExpiredDocument && VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN in evidence)
        ) {
            return true
        }
        if (policy.requireMrzStructure && VerificationEvidence.MRZ_STRUCTURE_VALID !in evidence) return true
        if (policy.requireMrzCheckDigits && VerificationEvidence.MRZ_CHECK_DIGITS_VALID !in evidence) return true
        if (policy.requireNfcRead && VerificationEvidence.NFC_CHIP_READ !in evidence) return true
        if (
            policy.requirePrintedChipConsistency &&
            VerificationEvidence.PRINTED_CHIP_DATA_MATCH !in evidence
        ) {
            return true
        }
        if (policy.requireFaceMatch && VerificationEvidence.FACE_MATCH_ACCEPTED !in evidence) return true
        if (
            policy.requirePassiveAuthentication &&
            VerificationEvidence.PASSIVE_AUTHENTICATION_VALID !in evidence
        ) {
            return true
        }
        return false
    }
}
