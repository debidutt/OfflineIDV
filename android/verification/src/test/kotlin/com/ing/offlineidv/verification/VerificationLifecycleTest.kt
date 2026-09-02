package com.ing.offlineidv.verification

import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.advanceToDecision
import com.ing.offlineidv.verification.fixtures.advanceToDocumentQuality
import com.ing.offlineidv.verification.fixtures.advanceToOcr
import com.ing.offlineidv.verification.fixtures.completeHappyPath
import com.ing.offlineidv.verification.fixtures.startAndInitialize
import com.ing.offlineidv.verification.model.ActiveVerificationState
import com.ing.offlineidv.verification.model.Cancelled
import com.ing.offlineidv.verification.model.Expired
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.TechnicalFailure
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState
import com.ing.offlineidv.verification.model.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationLifecycleTest {
    @Test
    public fun `cancel succeeds from every happy-path active state`() {
        val journey = StateMachineHarness()
        journey.completeHappyPath()
        val activeStates = journey.results.map { it.state }.filterIsInstance<ActiveVerificationState>()

        activeStates.forEach { state ->
            val result =
                DefaultVerificationStateMachine.transition(
                    state,
                    VerificationEvent.Cancel,
                    VerificationContext(),
                )
            assertTrue("Cancellation failed from ${state.javaClass.simpleName}", result.state is Cancelled)
            assertTerminalCleanup(result.effects)
        }
    }

    @Test
    public fun `cancel succeeds from recovery state`() {
        val harness = StateMachineHarness()
        harness.advanceToDocumentQuality()
        harness.dispatch(VerificationEvent.CaptureQualityRejected(harness.operation()))

        val result = harness.dispatch(VerificationEvent.Cancel)

        assertTrue(result.state is Cancelled)
        assertTerminalCleanup(result.effects)
    }

    @Test
    public fun `cancel from terminal state is ignored`() {
        val harness = StateMachineHarness()
        harness.completeHappyPath()
        val stateBefore = harness.state

        val result = harness.dispatch(VerificationEvent.Cancel)

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_DUPLICATE_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    public fun `matching session expiry produces expired outcome`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()

        val result = harness.dispatch(VerificationEvent.SessionExpired(VerificationFixtures.sessionId))

        assertTrue(result.state is Expired)
        assertTerminalCleanup(result.effects)
    }

    @Test
    public fun `foreign session expiry is stale`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()
        val stateBefore = harness.state

        val result = harness.dispatch(VerificationEvent.SessionExpired(VerificationFixtures.otherSessionId))

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_STALE_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    public fun `step timeout is distinct from session expiry`() {
        val harness = StateMachineHarness()
        harness.advanceToOcr()

        harness.dispatch(VerificationEvent.SessionTimedOut(harness.operation()))

        assertTrue(harness.state is com.ing.offlineidv.verification.model.RecoveryRequired)
        assertTrue(harness.state !is Expired)
    }

    @Test
    public fun `duplicate completion event cannot advance a newer operation`() {
        val harness = StateMachineHarness()
        harness.advanceToDocumentQuality()
        val stateBefore = harness.state
        val oldCaptureOperation =
            harness.results
                .first { it.state is com.ing.offlineidv.verification.model.CapturingDocument }
                .let { result ->
                    (result.state as com.ing.offlineidv.verification.model.CapturingDocument)
                        .progress.activeOperation!!
                }

        val result =
            harness.dispatch(
                VerificationEvent.DocumentCaptured(
                    oldCaptureOperation,
                    VerificationFixtures.documentReference,
                ),
            )

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_DUPLICATE_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    public fun `out-of-order engine event is safely illegal`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()
        val stateBefore = harness.state

        val result = harness.dispatch(VerificationEvent.SelfieRequested)

        assertSame(stateBefore, result.state)
        assertEquals(TransitionDisposition.IGNORED_ILLEGAL_EVENT, result.disposition)
        assertTrue(result.effects.isEmpty())
    }

    @Test
    public fun `same state event and context are deterministic`() {
        val harness = StateMachineHarness()
        harness.startAndInitialize()
        val state = harness.state
        val context = VerificationContext()

        val first = DefaultVerificationStateMachine.transition(state, VerificationEvent.PassportSelected, context)
        val second = DefaultVerificationStateMachine.transition(state, VerificationEvent.PassportSelected, context)

        assertEquals(first, second)
    }

    @Test
    public fun `decision execution failure is technical failure`() {
        val harness = StateMachineHarness()
        harness.advanceToDecision()

        val result =
            harness.dispatch(
                VerificationEvent.DecisionFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )

        assertTrue(result.state is TechnicalFailure)
        assertTerminalCleanup(result.effects)
    }

    @Test
    public fun `every terminal outcome requests cleanup and terminal emission`() {
        val terminalResults = mutableListOf<com.ing.offlineidv.verification.model.TransitionResult>()

        StateMachineHarness().also { harness ->
            harness.completeHappyPath()
            terminalResults += harness.results.last()
            assertTrue(harness.state is Verified)
        }
        StateMachineHarness().also { harness ->
            harness.advanceToDecision(FaceComparisonStatus.REJECTED)
            terminalResults += harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
            assertTrue(harness.state is Rejected)
        }
        StateMachineHarness().also { harness ->
            harness.advanceToDecision(FaceComparisonStatus.INCONCLUSIVE)
            terminalResults += harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
            assertTrue(harness.state is Inconclusive)
        }
        StateMachineHarness().also { harness ->
            harness.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
            terminalResults +=
                harness.dispatch(
                    VerificationEvent.InitializationFailed(
                        harness.operation(),
                        VerificationFixtures.safeTechnicalError,
                    ),
                )
            assertTrue(harness.state is TechnicalFailure)
        }
        StateMachineHarness().also { harness ->
            harness.startAndInitialize()
            terminalResults += harness.dispatch(VerificationEvent.Cancel)
            assertTrue(harness.state is Cancelled)
        }
        StateMachineHarness().also { harness ->
            harness.startAndInitialize()
            terminalResults += harness.dispatch(VerificationEvent.SessionExpired(VerificationFixtures.sessionId))
            assertTrue(harness.state is Expired)
        }

        terminalResults.forEach { result ->
            assertTrue(result.state is TerminalState)
            assertTerminalCleanup(result.effects)
        }
    }

    @Test
    public fun `reset is accepted only after terminal transition`() {
        val harness = StateMachineHarness()
        harness.completeHappyPath()

        val result = harness.dispatch(VerificationEvent.Reset)

        assertEquals(com.ing.offlineidv.verification.model.Idle, result.state)
        assertEquals(TransitionDisposition.APPLIED, result.disposition)
    }

    private fun assertTerminalCleanup(effects: List<VerificationEffect>) {
        assertTrue(effects.any { it is VerificationEffect.ClearSensitiveSessionData })
        assertTrue(effects.last() is VerificationEffect.EmitTerminalResult)
    }
}
