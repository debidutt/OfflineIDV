package com.ing.offlineidv.verification

import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.completeHappyPath
import com.ing.offlineidv.verification.model.Initializing
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationHappyPathTest {
    @Test
    public fun `full successful flow reaches verified`() {
        val harness = StateMachineHarness()

        harness.completeHappyPath()

        assertTrue(harness.state is Verified)
        assertEquals(
            VerificationOutcome.VERIFIED,
            (harness.state as Verified).summary.outcome,
        )
    }

    @Test
    public fun `principal external intents are emitted in journey order`() {
        val harness = StateMachineHarness()

        harness.completeHappyPath()

        val principalEffects =
            harness.effects
                .filterNot {
                    it is VerificationEffect.ScheduleTimeout ||
                        it is VerificationEffect.CancelTimeout
                }.map { it.javaClass.simpleName }
        assertEquals(
            listOf(
                "InitializeSession",
                "RequestCameraPermission",
                "PrepareCamera",
                "CaptureDocument",
                "EvaluateDocumentQuality",
                "RunOcr",
                "ExtractAndValidateMrz",
                "PromptForNfc",
                "StartNfcRead",
                "ValidateChipData",
                "ComparePrintedAndChipData",
                "PromptForSelfie",
                "CaptureSelfie",
                "EvaluateSelfieQuality",
                "CompareFaces",
                "EvaluateVerificationPolicy",
                "ClearSensitiveSessionData",
                "EmitTerminalResult",
            ),
            principalEffects,
        )
    }

    @Test
    public fun `verified summary retains required evidence separately`() {
        val harness = StateMachineHarness()

        harness.completeHappyPath()

        val summary = (harness.state as Verified).summary
        assertEquals(VerificationOutcome.VERIFIED, summary.outcome)
        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in summary.evidence)
        assertTrue(VerificationEvidence.NFC_CHIP_READ in summary.evidence)
        assertTrue(VerificationEvidence.PRINTED_CHIP_DATA_MATCH in summary.evidence)
        assertTrue(VerificationEvidence.FACE_MATCH_ACCEPTED in summary.evidence)
    }

    @Test
    public fun `completion cancels operation and session timeouts before cleanup and emission`() {
        val harness = StateMachineHarness()

        harness.completeHappyPath()

        assertTrue(harness.effects.takeLast(4)[0] is VerificationEffect.CancelTimeout)
        assertTrue(harness.effects.takeLast(4)[1] is VerificationEffect.CancelTimeout)
        assertTrue(harness.effects.takeLast(4)[2] is VerificationEffect.ClearSensitiveSessionData)
        assertTrue(harness.effects.takeLast(4)[3] is VerificationEffect.EmitTerminalResult)
    }

    @Test
    public fun `start from idle initializes and schedules a timeout`() {
        val result =
            DefaultVerificationStateMachine.transition(
                com.ing.offlineidv.verification.model.Idle,
                VerificationEvent.Start(VerificationFixtures.sessionId),
                com.ing.offlineidv.verification.model
                    .VerificationContext(),
            )

        assertTrue(result.state is Initializing)
        assertTrue(result.effects[0] is VerificationEffect.InitializeSession)
        assertTrue(result.effects[1] is VerificationEffect.ScheduleTimeout)
    }

    @Test
    public fun `duplicate start is ignored without effects`() {
        val harness = StateMachineHarness()
        harness.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
        val stateBefore = harness.state

        val result = harness.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_DUPLICATE_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }
}
