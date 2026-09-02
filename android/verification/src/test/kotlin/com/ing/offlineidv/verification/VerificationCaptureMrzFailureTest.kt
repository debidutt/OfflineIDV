package com.ing.offlineidv.verification

import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.advanceToCameraReady
import com.ing.offlineidv.verification.fixtures.advanceToDocumentQuality
import com.ing.offlineidv.verification.fixtures.advanceToMrzValidation
import com.ing.offlineidv.verification.fixtures.advanceToOcr
import com.ing.offlineidv.verification.fixtures.completeMrz
import com.ing.offlineidv.verification.fixtures.startAndInitialize
import com.ing.offlineidv.verification.model.CameraPermissionRequired
import com.ing.offlineidv.verification.model.CapturingDocument
import com.ing.offlineidv.verification.model.EvaluatingDocumentQuality
import com.ing.offlineidv.verification.model.ExtractingMrz
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.MakingDecision
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.RetryPolicy
import com.ing.offlineidv.verification.model.RetryableStep
import com.ing.offlineidv.verification.model.RunningOcr
import com.ing.offlineidv.verification.model.TechnicalFailure
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOperationToken
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.mrz.MrzEvidenceMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationCaptureMrzFailureTest {
    @Test
    public fun `initialization failure is a cleaned technical failure`() {
        val harness = StateMachineHarness()
        harness.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        val result =
            harness.dispatch(
                VerificationEvent.InitializationFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is TechnicalFailure)
        assertTrue(result.effects.any { it is VerificationEffect.ClearSensitiveSessionData })
    }

    @Test
    public fun `passport selection requests camera permission`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()

        val result = harness.dispatch(VerificationEvent.PassportSelected)

        assertTrue(result.state is CameraPermissionRequired)
        assertTrue(result.effects.single() is VerificationEffect.RequestCameraPermission)
    }

    @Test
    public fun `camera permission denial is inconclusive and cleaned`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()
        harness.dispatch(VerificationEvent.PassportSelected)

        val result = harness.dispatch(VerificationEvent.CameraPermissionDenied)

        assertTrue(result.state is Inconclusive)
        assertTrue(result.effects.any { it is VerificationEffect.ClearSensitiveSessionData })
    }

    @Test
    public fun `document capture starts a deterministic first attempt`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()

        val result = harness.dispatch(VerificationEvent.CaptureRequested)

        assertTrue(result.state is CapturingDocument)
        assertEquals(
            1,
            (result.state as CapturingDocument).progress.retries.attemptsFor(RetryableStep.DOCUMENT_CAPTURE),
        )
    }

    @Test
    public fun `captured document advances to quality evaluation`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        harness.dispatch(VerificationEvent.CaptureRequested)

        val result =
            harness.dispatch(
                VerificationEvent.DocumentCaptured(
                    harness.operation(),
                    VerificationFixtures.documentReference,
                ),
            )

        assertTrue(result.state is EvaluatingDocumentQuality)
        assertTrue(result.effects[0] is VerificationEffect.CancelTimeout)
        assertTrue(result.effects[1] is VerificationEffect.EvaluateDocumentQuality)
    }

    @Test
    public fun `quality rejection offers retry and retry retains evidence`() {
        val harness = StateMachineHarness()
        harness.advanceToDocumentQuality()
        harness.dispatch(VerificationEvent.CaptureQualityRejected(harness.operation()))

        assertTrue(harness.state is RecoveryRequired)
        val result = harness.dispatch(VerificationEvent.Retry)

        assertTrue(result.state is CapturingDocument)
        val progress = (result.state as CapturingDocument).progress
        assertEquals(2, progress.retries.attemptsFor(RetryableStep.DOCUMENT_CAPTURE))
        assertTrue(VerificationEvidence.STEP_RETRIED in progress.evidence)
    }

    @Test
    public fun `exhausted capture quality attempts become inconclusive`() {
        val context = VerificationContext(retryPolicy = RetryPolicy(cameraCaptureAttempts = 1))
        val harness = StateMachineHarness(context = context)
        harness.advanceToDocumentQuality()

        val result = harness.dispatch(VerificationEvent.CaptureQualityRejected(harness.operation()))

        assertTrue(result.state is Inconclusive)
        assertTrue(result.effects.any { it is VerificationEffect.ClearSensitiveSessionData })
    }

    @Test
    public fun `document quality engine failure offers reducer-controlled capture retry`() {
        val harness = StateMachineHarness()
        harness.advanceToDocumentQuality()

        val result =
            harness.dispatch(
                VerificationEvent.DocumentQualityFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is RecoveryRequired)
        assertEquals(RetryableStep.DOCUMENT_CAPTURE, (result.state as RecoveryRequired).failedStep)
    }

    @Test
    public fun `foreign-session capture result is stale and has no effect`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        harness.dispatch(VerificationEvent.CaptureRequested)
        val stateBefore = harness.state
        val current = harness.operation()
        val stale =
            VerificationOperationToken(
                VerificationFixtures.otherSessionId,
                VerificationStep.DOCUMENT_CAPTURE,
                current.generation,
            )

        val result =
            harness.dispatch(
                VerificationEvent.DocumentCaptured(stale, VerificationFixtures.documentReference),
            )

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_STALE_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    public fun `accepted document quality starts OCR`() {
        val harness = StateMachineHarness()
        harness.advanceToDocumentQuality()

        val result = harness.dispatch(VerificationEvent.CaptureQualityAccepted(harness.operation()))

        assertTrue(result.state is RunningOcr)
        assertTrue(result.effects.any { it is VerificationEffect.RunOcr })
    }

    @Test
    public fun `OCR success starts MRZ extraction`() {
        val harness = StateMachineHarness()
        harness.advanceToOcr()

        val result =
            harness.dispatch(
                VerificationEvent.OcrSucceeded(harness.operation(), VerificationFixtures.ocrReference),
            )

        assertTrue(result.state is ExtractingMrz)
        assertTrue(result.effects.any { it is VerificationEffect.ExtractAndValidateMrz })
    }

    @Test
    public fun `OCR technical failure offers only OCR retry`() {
        val harness = StateMachineHarness()
        harness.advanceToOcr()

        val result =
            harness.dispatch(
                VerificationEvent.OcrFailed(harness.operation(), VerificationFixtures.safeTechnicalError),
            )

        assertTrue(result.state is RecoveryRequired)
        assertEquals(RetryableStep.OCR, (result.state as RecoveryRequired).failedStep)
    }

    @Test
    public fun `malformed MRZ is rejected by decision policy`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(
                    structurallyValid = false,
                    checkDigitStatus = CheckDigitStatus.MALFORMED,
                ),
            )
        val harness = StateMachineHarness()
        harness.advanceToMrzValidation()
        harness.completeMrz(summary)

        assertTrue(harness.state is MakingDecision)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
        assertTrue(harness.state is Rejected)
    }

    @Test
    public fun `invalid checksum is rejected independently from structure`() {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(checkDigitStatus = CheckDigitStatus.MISMATCH),
            )
        val harness = StateMachineHarness()
        harness.advanceToMrzValidation()
        harness.completeMrz(summary)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Rejected)
        assertTrue(
            VerificationEvidence.MRZ_STRUCTURE_VALID in
                (harness.state as Rejected).summary.evidence,
        )
    }

    @Test
    public fun `character ambiguity reaches an inconclusive decision`() {
        assertAmbiguityIsInconclusive(MrzValidationIssueType.AMBIGUOUS_CHARACTER)
    }

    @Test
    public fun `century ambiguity reaches an inconclusive decision`() {
        assertAmbiguityIsInconclusive(MrzValidationIssueType.AMBIGUOUS_CENTURY)
    }

    @Test
    public fun `expired document is rejected by default policy`() {
        val harness = StateMachineHarness()
        harness.advanceToMrzValidation()
        harness.completeMrz(
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(expiryStatus = MrzExpiryStatus.EXPIRED),
            ),
        )
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Rejected)
    }

    @Test
    public fun `expired document continues when policy allows it`() {
        val context = VerificationContext(policy = VerificationPolicy(rejectExpiredDocument = false))
        val harness = StateMachineHarness(context = context)
        harness.advanceToMrzValidation()

        harness.completeMrz(
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(expiryStatus = MrzExpiryStatus.EXPIRED),
            ),
        )

        assertTrue(harness.state is com.ing.offlineidv.verification.model.AwaitingNfc)
        assertTrue(
            VerificationEvidence.DOCUMENT_EXPIRED in
                (harness.state as com.ing.offlineidv.verification.model.AwaitingNfc).progress.evidence,
        )
    }

    @Test
    public fun `unknown expiry produces an inconclusive policy result`() {
        val harness = StateMachineHarness()
        harness.advanceToMrzValidation()
        harness.completeMrz(
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(expiryStatus = MrzExpiryStatus.UNKNOWN),
            ),
        )
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Inconclusive)
    }

    private fun assertAmbiguityIsInconclusive(issueType: MrzValidationIssueType) {
        val summary =
            MrzEvidenceMapper.map(
                VerificationFixtures.validation(issues = listOf(VerificationFixtures.issue(issueType))),
            )
        val harness = StateMachineHarness()
        harness.advanceToMrzValidation()
        harness.completeMrz(summary)
        harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))

        assertTrue(harness.state is Inconclusive)
    }
}
