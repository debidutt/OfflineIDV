package com.ing.offlineidv.demo

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.ui.AtlasRuntimeMode
import com.ing.offlineidv.ui.AtlasUiAction
import com.ing.offlineidv.ui.AtlasUiState
import com.ing.offlineidv.verification.demo.DemoPromptMode
import com.ing.offlineidv.verification.demo.DemoVerificationFactory
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.VerificationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class AtlasDemoControllerTest {
    @Test
    public fun `real camera mode can be selected only before a session`() {
        val controller = controller()

        controller.dispatch(AtlasUiAction.SelectRuntimeMode(AtlasRuntimeMode.REAL_ANDROID))

        assertEquals(AtlasRuntimeMode.REAL_ANDROID, controller.runtimeMode)
        assertEquals(AtlasUiState.Welcome, controller.state)
    }

    @Test
    public fun `runtime mode cannot change after demo session starts`() {
        val controller = controller()
        controller.dispatch(AtlasUiAction.Start)

        controller.dispatch(AtlasUiAction.SelectRuntimeMode(AtlasRuntimeMode.REAL_ANDROID))

        assertEquals(AtlasRuntimeMode.DEMO, controller.runtimeMode)
        assertTrue(controller.state is AtlasUiState.DocumentSelection)
    }

    @Test
    public fun `success moves from welcome to reducer verified result`() {
        val controller = controller()

        controller.dispatch(AtlasUiAction.Start)
        assertTrue(controller.state is AtlasUiState.DocumentSelection)
        driveToNfc(controller)
        assertTrue(controller.state is AtlasUiState.Nfc)
        controller.dispatch(AtlasUiAction.StartNfc)
        assertTrue(controller.state is AtlasUiState.Selfie)
        controller.dispatch(AtlasUiAction.CaptureSelfie)

        assertOutcome(controller, VerificationOutcome.VERIFIED)
    }

    @Test
    public fun `invalid mrz result is supplied by reducer policy`() {
        val controller = controller(DemoScenario.INVALID_MRZ)

        controller.dispatch(AtlasUiAction.Start)
        driveToCapture(controller)
        controller.dispatch(AtlasUiAction.CaptureDocument)

        assertOutcome(controller, VerificationOutcome.REJECTED)
    }

    @Test
    public fun `nfc timeout exposes reducer recovery and retry`() {
        val controller = controller(DemoScenario.NFC_TIMEOUT_THEN_SUCCESS)

        controller.dispatch(AtlasUiAction.Start)
        driveToNfc(controller)
        controller.dispatch(AtlasUiAction.StartNfc)

        val recovery = controller.state as AtlasUiState.Recovery
        assertTrue(recovery.canRetry)
        controller.dispatch(AtlasUiAction.Retry)
        assertTrue(controller.state is AtlasUiState.Selfie)
    }

    @Test
    public fun `face mismatch produces reducer rejected result`() {
        val controller = controller(DemoScenario.FACE_MISMATCH)

        controller.dispatch(AtlasUiAction.Start)
        driveToNfc(controller)
        controller.dispatch(AtlasUiAction.StartNfc)
        controller.dispatch(AtlasUiAction.CaptureSelfie)

        assertOutcome(controller, VerificationOutcome.REJECTED)
    }

    @Test
    public fun `cancel action dispatches host cancellation`() {
        val controller = controller()

        controller.dispatch(AtlasUiAction.Start)
        controller.dispatch(AtlasUiAction.Cancel)

        assertOutcome(controller, VerificationOutcome.CANCELLED)
        assertTrue(controller.activeRuntime?.effectHandler?.cleanupComplete == true)
    }

    @Test
    public fun `configured expiry stimulus reaches reducer expired result`() {
        val controller = controller(DemoScenario.SESSION_EXPIRED)

        controller.dispatch(AtlasUiAction.Start)

        assertOutcome(controller, VerificationOutcome.EXPIRED)
    }

    @Test
    public fun `restart creates a new session only after terminal cleanup`() {
        val controller = controller()
        controller.dispatch(AtlasUiAction.Start)
        driveToVerified(controller)
        val previous = controller.activeRuntime

        assertTrue(previous?.effectHandler?.cleanupComplete == true)
        controller.dispatch(AtlasUiAction.StartAgain)

        assertNotSame(previous, controller.activeRuntime)
        assertTrue(controller.state is AtlasUiState.DocumentSelection)
        assertTrue(previous?.registry?.isCleared == true)
    }

    @Test
    public fun `restart is ignored while a session is active`() {
        val controller = controller()
        controller.dispatch(AtlasUiAction.Start)
        val active = controller.activeRuntime

        controller.dispatch(AtlasUiAction.StartAgain)

        assertSame(active, controller.activeRuntime)
        assertFalse(active?.effectHandler?.cleanupComplete == true)
    }

    @Test
    public fun `scenario selection cannot mutate an active runtime`() {
        val controller = controller(DemoScenario.SUCCESS)
        controller.dispatch(AtlasUiAction.Start)
        val activeDefinition = controller.activeRuntime?.definition

        controller.dispatch(AtlasUiAction.SelectScenario(DemoScenario.FACE_MISMATCH))

        assertSame(activeDefinition, controller.activeRuntime?.definition)
        driveToVerified(controller)
        assertOutcome(controller, VerificationOutcome.VERIFIED)
    }

    @Test
    public fun `return home cancels and cleans an active session`() {
        val controller = controller()
        controller.dispatch(AtlasUiAction.Start)
        val active = controller.activeRuntime

        controller.dispatch(AtlasUiAction.ReturnHome)

        assertEquals(AtlasUiState.Welcome, controller.state)
        assertTrue(active?.effectHandler?.cleanupComplete == true)
        assertTrue(active?.orchestrator?.state is TerminalState)
    }

    private fun controller(scenario: DemoScenario = DemoScenario.SUCCESS): AtlasDemoController {
        var sequence = 0
        val controller =
            AtlasDemoController(
                runtimeFactory =
                    AtlasDemoRuntimeFactory { selected, sessionId ->
                        val config = (IdvConfig.demo(selected) as IdvResult.Success).value
                        (
                            DemoVerificationFactory.create(
                                config = config,
                                sessionId = sessionId,
                                promptMode = DemoPromptMode.HOST_CONTROLLED,
                            ) as IdvResult.Success
                        ).value
                    },
                sessionIdFactory =
                    AtlasDemoSessionIdFactory {
                        sequence += 1
                        (IdvSessionId.parse("atlas_test_${sequence.toString().padStart(4, '0')}") as IdvResult.Success).value
                    },
            )
        if (scenario != DemoScenario.SUCCESS) {
            controller.dispatch(AtlasUiAction.ShowScenarios)
            controller.dispatch(AtlasUiAction.SelectScenario(scenario))
        }
        return controller
    }

    private fun driveToCapture(controller: AtlasDemoController) {
        controller.dispatch(AtlasUiAction.SelectPassport)
        assertEquals(AtlasUiState.PassportInstructions, controller.state)
        controller.dispatch(AtlasUiAction.ContinuePassportInstructions)
        assertTrue(controller.state is AtlasUiState.DocumentCapture)
    }

    private fun driveToNfc(controller: AtlasDemoController) {
        driveToCapture(controller)
        controller.dispatch(AtlasUiAction.CaptureDocument)
    }

    private fun driveToVerified(controller: AtlasDemoController) {
        driveToNfc(controller)
        controller.dispatch(AtlasUiAction.StartNfc)
        controller.dispatch(AtlasUiAction.CaptureSelfie)
        assertOutcome(controller, VerificationOutcome.VERIFIED)
    }

    private fun assertOutcome(
        controller: AtlasDemoController,
        outcome: VerificationOutcome,
    ) {
        assertEquals(outcome, (controller.state as AtlasUiState.Result).outcome)
        assertEquals(outcome, (controller.activeRuntime?.orchestrator?.state as TerminalState).summary.outcome)
    }
}
