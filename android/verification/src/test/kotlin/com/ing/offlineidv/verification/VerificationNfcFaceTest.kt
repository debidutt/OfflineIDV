package com.ing.offlineidv.verification

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
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
import com.ing.offlineidv.verification.fixtures.startAndInitialize
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CapturingSelfie
import com.ing.offlineidv.verification.model.ChipAuthenticationStatus
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.ComparingPrintedAndChipData
import com.ing.offlineidv.verification.model.EvaluatingSelfie
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.MakingDecision
import com.ing.offlineidv.verification.model.NfcReadPhase
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
    public fun `residence permit completes required NFC consistency without entering selfie flow`() {
        val policy =
            VerificationPolicy(
                requireNfcRead = true,
                requirePrintedChipConsistency = true,
                requireFaceMatch = false,
            )
        val capabilities =
            VerificationCapabilities(
                setOf(
                    VerificationCapability.CAMERA,
                    VerificationCapability.NFC,
                ),
            )
        val harness = StateMachineHarness(context = VerificationContext(policy = policy, capabilities = capabilities))
        harness.startAndInitialize()
        harness.dispatch(VerificationEvent.ResidencePermitSelected)
        harness.dispatch(VerificationEvent.CameraPermissionGranted)
        harness.dispatch(VerificationEvent.CameraReady(harness.operation()))
        harness.dispatch(VerificationEvent.CaptureRequested)
        harness.dispatch(VerificationEvent.DocumentCaptured(harness.operation(), VerificationFixtures.documentReference))
        harness.dispatch(VerificationEvent.CaptureQualityAccepted(harness.operation()))
        harness.dispatch(VerificationEvent.OcrSucceeded(harness.operation(), VerificationFixtures.ocrReference))
        harness.dispatch(VerificationEvent.MrzExtractionSucceeded(harness.operation()))
        harness.completeMrz()

        assertTrue(harness.state is AwaitingNfc)
        harness.dispatch(VerificationEvent.NfcRequested)
        harness.dispatch(VerificationEvent.NfcReadSucceeded(harness.operation(), VerificationFixtures.chipReference))
        harness.dispatch(
            VerificationEvent.ChipValidationCompleted(
                harness.operation(),
                VerificationFixtures.validChipSummary,
            ),
        )
        assertTrue(harness.state is ComparingPrintedAndChipData)
        harness.dispatch(
            VerificationEvent.PrintedAndChipComparisonCompleted(
                harness.operation(),
                PrintedChipComparisonStatus.MATCH,
            ),
        )

        assertTrue(harness.state is MakingDecision)
        assertTrue(harness.effects.none { it is VerificationEffect.PromptForSelfie })
    }

    @Test
    public fun `optional NFC and face policy still skips both without changing reducer rules`() {
        val policy =
            VerificationPolicy(
                requireNfcRead = false,
                requirePrintedChipConsistency = false,
                requireFaceMatch = false,
            )
        val capabilities = VerificationCapabilities(setOf(VerificationCapability.CAMERA))
        val harness = StateMachineHarness(context = VerificationContext(policy = policy, capabilities = capabilities))
        harness.startAndInitialize()
        harness.dispatch(VerificationEvent.ResidencePermitSelected)
        harness.dispatch(VerificationEvent.CameraPermissionGranted)
        harness.dispatch(VerificationEvent.CameraReady(harness.operation()))
        harness.dispatch(VerificationEvent.CaptureRequested)
        harness.dispatch(VerificationEvent.DocumentCaptured(harness.operation(), VerificationFixtures.documentReference))
        harness.dispatch(VerificationEvent.CaptureQualityAccepted(harness.operation()))
        harness.dispatch(VerificationEvent.OcrSucceeded(harness.operation(), VerificationFixtures.ocrReference))
        harness.dispatch(VerificationEvent.MrzExtractionSucceeded(harness.operation()))

        harness.completeMrz()

        assertTrue(harness.state is MakingDecision)
        assertTrue(harness.effects.none { it is VerificationEffect.PromptForNfc })
        assertTrue(harness.effects.none { it is VerificationEffect.PromptForSelfie })
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
        assertTrue(harness.state is Verified)
        assertTrue(VerificationEvidence.REQUIRED_STEP_SKIPPED !in (harness.state as Verified).summary.evidence)
    }

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
    public fun `NFC progress is reducer owned and monotonic`() {
        val harness = StateMachineHarness()
        harness.advanceToNfcRead()

        listOf(
            NfcReadPhase.READY_TO_SCAN,
            NfcReadPhase.CHIP_DETECTED,
            NfcReadPhase.CONNECTING,
            NfcReadPhase.SCAN_IN_PROGRESS,
        ).forEach { phase ->
            harness.dispatch(VerificationEvent.NfcProgressed(harness.operation(), phase))
            assertEquals(phase, (harness.state as ReadingNfc).phase)
        }

        harness.dispatch(VerificationEvent.NfcProgressed(harness.operation(), NfcReadPhase.CONNECTING))
        assertEquals(NfcReadPhase.SCAN_IN_PROGRESS, (harness.state as ReadingNfc).phase)
    }

    @Test
    public fun `connection loss preserves distinct error and retry restarts ready to scan`() {
        val harness = StateMachineHarness()
        harness.advanceToNfcRead()
        harness.dispatch(
            VerificationEvent.NfcReadFailed(
                harness.operation(),
                IdvError.Nfc(NfcFailure.TAG_LOST),
            ),
        )

        val recovery = harness.state as RecoveryRequired
        assertEquals(NfcFailure.TAG_LOST, recovery.error?.reason)
        harness.dispatch(VerificationEvent.Retry)

        assertEquals(NfcReadPhase.STARTING_READER, (harness.state as ReadingNfc).phase)
        assertEquals(2, (harness.state as ReadingNfc).progress.retries.attemptsFor(RetryableStep.NFC))
    }

    @Test
    public fun `access denial remains distinct from connection loss`() {
        val harness = StateMachineHarness()
        harness.advanceToNfcRead()

        harness.dispatch(
            VerificationEvent.NfcReadFailed(
                harness.operation(),
                IdvError.Nfc(NfcFailure.ACCESS_DENIED),
            ),
        )

        assertEquals(NfcFailure.ACCESS_DENIED, (harness.state as RecoveryRequired).error?.reason)
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
    public fun `signed data and live chip proof remain separate evidence`() {
        val harness = StateMachineHarness()
        harness.advanceToChipValidation()

        val result =
            harness.dispatch(
                VerificationEvent.ChipValidationCompleted(
                    harness.operation(),
                    ChipValidationSummary(
                        dg1Available = true,
                        dg2Available = false,
                        passiveAuthentication = PassiveAuthenticationStatus.VALID,
                        chipAuthentication = ChipAuthenticationStatus.AUTHENTICATION_FAILED,
                    ),
                ),
            )

        val evidence = (result.state as com.ing.offlineidv.verification.model.ActiveVerificationState).progress.evidence
        assertTrue(VerificationEvidence.PASSIVE_AUTHENTICATION_VALID in evidence)
        assertTrue(VerificationEvidence.CHIP_AUTHENTICATION_FAILED in evidence)
        assertTrue(VerificationEvidence.CHIP_AUTHENTICATION_SUCCEEDED !in evidence)
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
