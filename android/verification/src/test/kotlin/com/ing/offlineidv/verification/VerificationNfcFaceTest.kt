package com.ing.offlineidv.verification

import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.advanceToAwaitingSelfie
import com.ing.offlineidv.verification.fixtures.advanceToChipValidation
import com.ing.offlineidv.verification.fixtures.advanceToDecision
import com.ing.offlineidv.verification.fixtures.advanceToFaceComparison
import com.ing.offlineidv.verification.fixtures.advanceToMrzValidation
import com.ing.offlineidv.verification.fixtures.advanceToNfcRead
import com.ing.offlineidv.verification.fixtures.advanceToPrintedChipComparison
import com.ing.offlineidv.verification.fixtures.advanceToSelfieQuality
import com.ing.offlineidv.verification.fixtures.completeMrz
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CapturingSelfie
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.EvaluatingSelfie
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.MakingDecision
import com.ing.offlineidv.verification.model.PassiveAuthenticationStatus
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.ReadingNfc
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.RetryPolicy
import com.ing.offlineidv.verification.model.RetryableStep
import com.ing.offlineidv.verification.model.TechnicalFailure
import com.ing.offlineidv.verification.model.ValidatingChipData
import com.ing.offlineidv.verification.model.VerificationCapabilities
import com.ing.offlineidv.verification.model.VerificationCapability
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationNfcFaceTest {
    @Test
    public fun `NFC read success advances to chip validation`() {
        val harness = StateMachineHarness()
        harness.advanceToNfcRead()

        val result =
            harness.dispatch(
                VerificationEvent.NfcReadSucceeded(harness.operation(), VerificationFixtures.chipReference),
            )

        assertTrue(result.state is ValidatingChipData)
        assertTrue(result.effects.any { it is VerificationEffect.ValidateChipData })
        assertTrue(
            VerificationEvidence.NFC_CHIP_READ in
                (result.state as ValidatingChipData).progress.evidence,
        )
    }

    @Test
    public fun `NFC timeout offers a deterministic NFC retry`() {
        val harness = StateMachineHarness()
        harness.advanceToNfcRead()

        harness.dispatch(VerificationEvent.SessionTimedOut(harness.operation()))

        assertTrue(harness.state is RecoveryRequired)
        assertEquals(RetryableStep.NFC, (harness.state as RecoveryRequired).failedStep)
        harness.dispatch(VerificationEvent.Retry)
        assertTrue(harness.state is ReadingNfc)
        assertEquals(
            2,
            (harness.state as ReadingNfc).progress.retries.attemptsFor(RetryableStep.NFC),
        )
    }

    @Test
    public fun `required NFC retries exhausted become technical failure`() {
        val harness =
            StateMachineHarness(
                context = VerificationContext(retryPolicy = RetryPolicy(nfcAttempts = 1)),
            )
        harness.advanceToNfcRead()

        val result = harness.dispatch(VerificationEvent.SessionTimedOut(harness.operation()))

        assertTrue(result.state is TechnicalFailure)
    }

    @Test
    public fun `required unavailable NFC reaches inconclusive decision`() {
        val capabilities =
            VerificationCapabilities(
                setOf(
                    VerificationCapability.CAMERA,
                    VerificationCapability.FACE_COMPARISON,
                ),
            )
        val harness = StateMachineHarness(context = VerificationContext(capabilities = capabilities))
        harness.advanceToMrzValidation()

        harness.completeMrz()
        assertTrue(harness.state is MakingDecision)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Inconclusive)
        assertTrue(
            VerificationEvidence.CAPABILITY_UNAVAILABLE in
                (harness.state as Inconclusive).summary.evidence,
        )
    }

    @Test
    public fun `optional unavailable NFC continues to selfie`() {
        val policy =
            VerificationPolicy(
                requireNfcRead = false,
                requirePrintedChipConsistency = false,
            )
        val capabilities =
            VerificationCapabilities(
                setOf(
                    VerificationCapability.CAMERA,
                    VerificationCapability.FACE_COMPARISON,
                ),
            )
        val harness =
            StateMachineHarness(
                context = VerificationContext(policy = policy, capabilities = capabilities),
            )
        harness.advanceToMrzValidation()

        harness.completeMrz()

        assertTrue(harness.state is AwaitingSelfie)
    }

    @Test
    public fun `printed chip match advances to selfie`() {
        val harness = StateMachineHarness()
        harness.advanceToPrintedChipComparison()

        val result =
            harness.dispatch(
                VerificationEvent.PrintedAndChipComparisonCompleted(
                    harness.operation(),
                    PrintedChipComparisonStatus.MATCH,
                ),
            )

        assertTrue(result.state is AwaitingSelfie)
        assertTrue(
            VerificationEvidence.PRINTED_CHIP_DATA_MATCH in
                (result.state as AwaitingSelfie).progress.evidence,
        )
    }

    @Test
    public fun `required printed chip mismatch rejects after decision`() {
        val harness = StateMachineHarness()
        harness.advanceToPrintedChipComparison()
        harness.dispatch(
            VerificationEvent.PrintedAndChipComparisonCompleted(
                harness.operation(),
                PrintedChipComparisonStatus.MISMATCH,
            ),
        )

        assertTrue(harness.state is MakingDecision)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
        assertTrue(harness.state is Rejected)
    }

    @Test
    public fun `printed chip engine failure offers reducer-controlled NFC retry`() {
        val harness = StateMachineHarness()
        harness.advanceToPrintedChipComparison()

        val result =
            harness.dispatch(
                VerificationEvent.PrintedAndChipComparisonFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is RecoveryRequired)
        assertEquals(RetryableStep.NFC, (result.state as RecoveryRequired).failedStep)
    }

    @Test
    public fun `optional passive authentication may be not performed`() {
        val harness = StateMachineHarness()
        harness.advanceToChipValidation()
        harness.dispatch(
            VerificationEvent.ChipValidationCompleted(
                harness.operation(),
                ChipValidationSummary(
                    dg1Available = true,
                    dg2Available = true,
                    passiveAuthentication = PassiveAuthenticationStatus.NOT_PERFORMED,
                    portraitReference = VerificationFixtures.portraitReference,
                ),
            ),
        )
        harness.dispatch(
            VerificationEvent.PrintedAndChipComparisonCompleted(
                harness.operation(),
                PrintedChipComparisonStatus.MATCH,
            ),
        )
        harness.dispatch(VerificationEvent.SelfieRequested)
        harness.dispatch(
            VerificationEvent.SelfieCaptured(harness.operation(), VerificationFixtures.selfieReference),
        )
        harness.dispatch(VerificationEvent.SelfieQualityAccepted(harness.operation()))
        harness.dispatch(
            VerificationEvent.FaceComparisonCompleted(
                harness.operation(),
                FaceComparisonStatus.ACCEPTED,
            ),
        )
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Verified)
        assertTrue(
            VerificationEvidence.PASSIVE_AUTHENTICATION_NOT_PERFORMED in
                (harness.state as Verified).summary.evidence,
        )
    }

    @Test
    public fun `unavailable unsupported and technical passive auth remain not performed evidence`() {
        listOf(
            PassiveAuthenticationStatus.UNAVAILABLE,
            PassiveAuthenticationStatus.UNSUPPORTED,
            PassiveAuthenticationStatus.TECHNICAL_ERROR,
        ).forEach { status ->
            val harness = StateMachineHarness()
            harness.advanceToChipValidation()

            val result =
                harness.dispatch(
                    VerificationEvent.ChipValidationCompleted(
                        harness.operation(),
                        ChipValidationSummary(
                            dg1Available = true,
                            dg2Available = false,
                            passiveAuthentication = status,
                        ),
                    ),
                )

            val evidence =
                (result.state as com.ing.offlineidv.verification.model.ActiveVerificationState).progress.evidence
            assertTrue(VerificationEvidence.PASSIVE_AUTHENTICATION_NOT_PERFORMED in evidence)
            assertTrue(VerificationEvidence.PASSIVE_AUTHENTICATION_VALID !in evidence)
        }
    }

    @Test
    public fun `required absent passive authentication is inconclusive`() {
        val policy = VerificationPolicy(requirePassiveAuthentication = true)
        val harness = StateMachineHarness(context = VerificationContext(policy = policy))
        harness.advanceToChipValidation()
        harness.dispatch(
            VerificationEvent.ChipValidationCompleted(
                harness.operation(),
                ChipValidationSummary(
                    dg1Available = true,
                    dg2Available = true,
                    passiveAuthentication = PassiveAuthenticationStatus.NOT_PERFORMED,
                    portraitReference = VerificationFixtures.portraitReference,
                ),
            ),
        )
        harness.dispatch(
            VerificationEvent.PrintedAndChipComparisonCompleted(
                harness.operation(),
                PrintedChipComparisonStatus.MATCH,
            ),
        )
        harness.dispatch(VerificationEvent.SelfieRequested)
        harness.dispatch(
            VerificationEvent.SelfieCaptured(harness.operation(), VerificationFixtures.selfieReference),
        )
        harness.dispatch(VerificationEvent.SelfieQualityAccepted(harness.operation()))
        harness.dispatch(
            VerificationEvent.FaceComparisonCompleted(
                harness.operation(),
                FaceComparisonStatus.ACCEPTED,
            ),
        )
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Inconclusive)
    }

    @Test
    public fun `selfie capture advances to quality evaluation`() {
        val harness = StateMachineHarness()
        harness.advanceToAwaitingSelfie()
        harness.dispatch(VerificationEvent.SelfieRequested)

        assertTrue(harness.state is CapturingSelfie)
        val result =
            harness.dispatch(
                VerificationEvent.SelfieCaptured(harness.operation(), VerificationFixtures.selfieReference),
            )
        assertTrue(result.state is EvaluatingSelfie)
    }

    @Test
    public fun `selfie quality rejection offers selfie retry`() {
        val harness = StateMachineHarness()
        harness.advanceToSelfieQuality()

        val result = harness.dispatch(VerificationEvent.SelfieQualityRejected(harness.operation()))

        assertTrue(result.state is RecoveryRequired)
        assertEquals(RetryableStep.SELFIE, (result.state as RecoveryRequired).failedStep)
    }

    @Test
    public fun `selfie quality engine failure offers reducer-controlled selfie retry`() {
        val harness = StateMachineHarness()
        harness.advanceToSelfieQuality()

        val result =
            harness.dispatch(
                VerificationEvent.SelfieQualityFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is RecoveryRequired)
        assertEquals(RetryableStep.SELFIE, (result.state as RecoveryRequired).failedStep)
    }

    @Test
    public fun `accepted face comparison reaches decision`() {
        val harness = StateMachineHarness()
        harness.advanceToFaceComparison()

        val result =
            harness.dispatch(
                VerificationEvent.FaceComparisonCompleted(
                    harness.operation(),
                    FaceComparisonStatus.ACCEPTED,
                ),
            )

        assertTrue(result.state is MakingDecision)
        assertTrue(
            VerificationEvidence.FACE_MATCH_ACCEPTED in
                (result.state as MakingDecision).progress.evidence,
        )
    }

    @Test
    public fun `required face rejection becomes rejected`() {
        val harness = StateMachineHarness()
        harness.advanceToDecision(FaceComparisonStatus.REJECTED)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Rejected)
    }

    @Test
    public fun `inconclusive face comparison remains inconclusive`() {
        val harness = StateMachineHarness()
        harness.advanceToDecision(FaceComparisonStatus.INCONCLUSIVE)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Inconclusive)
    }

    @Test
    public fun `face retries exhausted become technical failure`() {
        val harness =
            StateMachineHarness(
                context = VerificationContext(retryPolicy = RetryPolicy(selfieAttempts = 1)),
            )
        harness.advanceToFaceComparison()

        val result =
            harness.dispatch(
                VerificationEvent.FaceComparisonFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is TechnicalFailure)
    }
}
