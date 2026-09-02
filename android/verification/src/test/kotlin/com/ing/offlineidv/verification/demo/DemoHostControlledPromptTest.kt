package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CameraReady
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.VerificationEvent
import org.junit.Assert.assertTrue
import org.junit.Test

public class DemoHostControlledPromptTest {
    @Test
    public fun `host controlled prompts preserve interactive waiting states`() {
        val runtime = runtime(DemoScenario.SUCCESS)

        runtime.orchestrator.dispatch(VerificationEvent.Start(runtime.sessionId))
        assertTrue(runtime.orchestrator.state is DocumentSelection)
        runtime.orchestrator.dispatch(VerificationEvent.PassportSelected)
        assertTrue(runtime.orchestrator.state is CameraReady)
        runtime.orchestrator.dispatch(VerificationEvent.CaptureRequested)
        assertTrue(runtime.orchestrator.state is AwaitingNfc)
        runtime.orchestrator.dispatch(VerificationEvent.NfcRequested)
        assertTrue(runtime.orchestrator.state is AwaitingSelfie)
    }

    @Test
    public fun `automatic prompt mode remains the default for milestone four runner`() {
        val runtime = runtime(DemoScenario.SUCCESS, DemoPromptMode.AUTOMATIC)

        val report = (DemoVerificationRunner(runtime).run() as IdvResult.Success).value

        assertTrue(report.cleanupComplete)
    }

    @Test
    public fun `configured lifecycle stimulus is applied without UI policy`() {
        val runtime = runtime(DemoScenario.SESSION_EXPIRED)

        runtime.orchestrator.dispatch(VerificationEvent.Start(runtime.sessionId))
        runtime.applyConfiguredLifecycleTrigger()

        assertTrue(runtime.orchestrator.state is com.ing.offlineidv.verification.model.Expired)
        assertTrue(runtime.effectHandler.cleanupComplete)
    }

    private fun runtime(
        scenario: DemoScenario,
        promptMode: DemoPromptMode = DemoPromptMode.HOST_CONTROLLED,
    ): DemoVerificationRuntime {
        val config = (IdvConfig.demo(scenario) as IdvResult.Success).value
        return (
            DemoVerificationFactory.create(
                config = config,
                sessionId = VerificationFixtures.sessionId,
                promptMode = promptMode,
            ) as IdvResult.Success
        ).value
    }
}
