package com.ing.offlineidv.verification

import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.advanceToDecision
import com.ing.offlineidv.verification.fixtures.advanceToDocumentQuality
import com.ing.offlineidv.verification.fixtures.completeHappyPath
import com.ing.offlineidv.verification.fixtures.startAndInitialize
import com.ing.offlineidv.verification.model.ActiveVerificationState
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationMatrixTest {
    private val context = VerificationContext()

    @Test
    public fun `representative fixtures cover every state type`() {
        val names = representativeStates().map { it.javaClass.simpleName }.toSet()

        assertEquals(
            setOf(
                "Idle",
                "Initializing",
                "DocumentSelection",
                "CameraPermissionRequired",
                "PreparingCamera",
                "CameraReady",
                "CapturingDocument",
                "EvaluatingDocumentQuality",
                "RunningOcr",
                "ExtractingMrz",
                "ValidatingMrz",
                "AwaitingNfc",
                "ReadingNfc",
                "ValidatingChipData",
                "ComparingPrintedAndChipData",
                "AwaitingSelfie",
                "CapturingSelfie",
                "EvaluatingSelfie",
                "ComparingFaces",
                "MakingDecision",
                "RecoveryRequired",
                "Verified",
                "Rejected",
                "Inconclusive",
                "TechnicalFailure",
                "Cancelled",
                "Expired",
            ),
            names,
        )
    }

    @Test
    public fun `all representative event types are deterministic against all states`() {
        representativeStates().forEach { state ->
            representativeEvents().forEach { event ->
                val first = DefaultVerificationStateMachine.transition(state, event, context)
                val second = DefaultVerificationStateMachine.transition(state, event, context)

                assertEquals(
                    "Non-deterministic ${event.javaClass.simpleName} from ${state.javaClass.simpleName}",
                    first,
                    second,
                )
                if (first.disposition != TransitionDisposition.APPLIED) {
                    assertSame(state, first.state)
                    assertTrue(first.effects.isEmpty())
                }
            }
        }
    }

    @Test
    public fun `terminal states are immutable except explicit reset`() {
        representativeStates().filterIsInstance<TerminalState>().forEach { state ->
            representativeEvents().filterNot { it == VerificationEvent.Reset }.forEach { event ->
                val result = DefaultVerificationStateMachine.transition(state, event, context)
                assertSame(state, result.state)
                assertTrue(result.effects.isEmpty())
            }
        }
    }

    @Test
    public fun `matching timeout never throws for any active state`() {
        representativeStates().filterIsInstance<ActiveVerificationState>().forEach { state ->
            state.progress.activeOperation?.let { operation ->
                DefaultVerificationStateMachine.transition(
                    state,
                    VerificationEvent.SessionTimedOut(operation),
                    context,
                )
            }
        }
    }

    @Test
    public fun `ignored events never emit cleanup or external work`() {
        representativeStates().forEach { state ->
            val result =
                DefaultVerificationStateMachine.transition(
                    state,
                    VerificationEvent.AcknowledgeError,
                    context,
                )
            if (result.disposition != TransitionDisposition.APPLIED) {
                assertTrue(result.effects.isEmpty())
                assertTrue(result.effects.none { it is VerificationEffect.ClearSensitiveSessionData })
            }
        }
    }

    private fun representativeStates(): List<VerificationState> {
        val states = mutableListOf<VerificationState>(com.ing.offlineidv.verification.model.Idle)
        StateMachineHarness().also { harness ->
            harness.completeHappyPath()
            states += harness.results.map { it.state }
        }
        StateMachineHarness().also { harness ->
            harness.advanceToDocumentQuality()
            harness.dispatch(VerificationEvent.CaptureQualityRejected(harness.operation()))
            states += harness.state
        }
        StateMachineHarness().also { harness ->
            harness.advanceToDecision(FaceComparisonStatus.REJECTED)
            harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
            states += harness.state
        }
        StateMachineHarness().also { harness ->
            harness.advanceToDecision(FaceComparisonStatus.INCONCLUSIVE)
            harness.dispatch(VerificationEvent.DecisionCompleted(harness.operation()))
            states += harness.state
        }
        StateMachineHarness().also { harness ->
            harness.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
            harness.dispatch(
                VerificationEvent.InitializationFailed(
                    harness.operation(),
                    VerificationFixtures.safeTechnicalError,
                ),
            )
            states += harness.state
        }
        StateMachineHarness().also { harness ->
            harness.startAndInitialize()
            harness.dispatch(VerificationEvent.Cancel)
            states += harness.state
        }
        StateMachineHarness().also { harness ->
            harness.startAndInitialize()
            harness.dispatch(VerificationEvent.SessionExpired(VerificationFixtures.sessionId))
            states += harness.state
        }
        return states.distinctBy { it.javaClass }
    }

    private fun representativeEvents(): List<VerificationEvent> {
        val start =
            DefaultVerificationStateMachine.transition(
                com.ing.offlineidv.verification.model.Idle,
                VerificationEvent.Start(VerificationFixtures.sessionId),
                context,
            )
        val operation = (start.state as ActiveVerificationState).progress.activeOperation!!
        return listOf(
            VerificationEvent.Start(VerificationFixtures.sessionId),
            VerificationEvent.InitializationSucceeded(operation),
            VerificationEvent.InitializationFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.Reset,
            VerificationEvent.Cancel,
            VerificationEvent.SessionTimedOut(operation),
            VerificationEvent.SessionExpired(VerificationFixtures.sessionId),
            VerificationEvent.PassportSelected,
            VerificationEvent.CameraPermissionRequired,
            VerificationEvent.CameraPermissionGranted,
            VerificationEvent.CameraPermissionDenied,
            VerificationEvent.CameraReady(operation),
            VerificationEvent.CaptureRequested,
            VerificationEvent.DocumentCaptured(operation, VerificationFixtures.documentReference),
            VerificationEvent.CaptureQualityAccepted(operation),
            VerificationEvent.CaptureQualityRejected(operation),
            VerificationEvent.DocumentQualityFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.CaptureFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.OcrStarted(operation),
            VerificationEvent.OcrSucceeded(operation, VerificationFixtures.ocrReference),
            VerificationEvent.OcrFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.MrzExtractionSucceeded(operation),
            VerificationEvent.MrzExtractionFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.MrzValidationCompleted(
                operation,
                VerificationFixtures.validMrzSummary,
                VerificationFixtures.printedReference,
                VerificationFixtures.accessKeyReference,
            ),
            VerificationEvent.MrzValidationFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.NfcRequested,
            VerificationEvent.NfcStarted(operation),
            VerificationEvent.NfcReadSucceeded(operation, VerificationFixtures.chipReference),
            VerificationEvent.NfcReadFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.ChipValidationCompleted(operation, VerificationFixtures.validChipSummary),
            VerificationEvent.ChipValidationFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.PrintedAndChipComparisonCompleted(
                operation,
                PrintedChipComparisonStatus.MATCH,
            ),
            VerificationEvent.PrintedAndChipComparisonFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.SelfieRequested,
            VerificationEvent.SelfieCaptured(operation, VerificationFixtures.selfieReference),
            VerificationEvent.SelfieCaptureFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.SelfieQualityAccepted(operation),
            VerificationEvent.SelfieQualityRejected(operation),
            VerificationEvent.SelfieQualityFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.FaceComparisonCompleted(operation, FaceComparisonStatus.ACCEPTED),
            VerificationEvent.FaceComparisonFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.DecisionRequested(operation),
            VerificationEvent.DecisionCompleted(operation),
            VerificationEvent.DecisionFailed(operation, VerificationFixtures.safeTechnicalError),
            VerificationEvent.Retry,
            VerificationEvent.Back,
            VerificationEvent.AcknowledgeError,
        )
    }
}
