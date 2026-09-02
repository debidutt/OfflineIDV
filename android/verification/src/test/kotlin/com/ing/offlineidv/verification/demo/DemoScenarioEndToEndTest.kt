package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class DemoScenarioEndToEndTest {
    @Test
    public fun `all required demo scenarios reach reducer-selected terminal outcomes and cleanup`() {
        val expected =
            linkedMapOf(
                DemoScenario.SUCCESS to VerificationOutcome.VERIFIED,
                DemoScenario.INVALID_MRZ to VerificationOutcome.REJECTED,
                DemoScenario.MRZ_AMBIGUITY to VerificationOutcome.INCONCLUSIVE,
                DemoScenario.EXPIRED_DOCUMENT to VerificationOutcome.REJECTED,
                DemoScenario.NFC_TIMEOUT_THEN_SUCCESS to VerificationOutcome.VERIFIED,
                DemoScenario.NFC_TIMEOUT_EXHAUSTED to VerificationOutcome.TECHNICAL_FAILURE,
                DemoScenario.NFC_UNAVAILABLE to VerificationOutcome.INCONCLUSIVE,
                DemoScenario.CHIP_MISMATCH to VerificationOutcome.REJECTED,
                DemoScenario.PASSIVE_AUTH_FAILURE to VerificationOutcome.VERIFIED,
                DemoScenario.SELFIE_QUALITY_FAILURE to VerificationOutcome.INCONCLUSIVE,
                DemoScenario.FACE_MISMATCH to VerificationOutcome.REJECTED,
                DemoScenario.FACE_INCONCLUSIVE to VerificationOutcome.INCONCLUSIVE,
                DemoScenario.TECHNICAL_FAILURE to VerificationOutcome.TECHNICAL_FAILURE,
                DemoScenario.USER_CANCELLED to VerificationOutcome.CANCELLED,
                DemoScenario.SESSION_EXPIRED to VerificationOutcome.EXPIRED,
            )

        expected.forEach { (scenario, outcome) ->
            val execution = execute(scenario)
            assertEquals("Unexpected outcome for $scenario", outcome, execution.report.outcome)
            assertTrue("Cleanup not completed for $scenario", execution.report.cleanupComplete)
            assertTrue("Registry not cleared for $scenario", execution.runtime.registry.isCleared)
            assertEquals("Scheduler retained work for $scenario", 0, execution.runtime.scheduler.scheduledCount)
            assertEquals("Terminal result not emitted once for $scenario", 1, execution.runtime.terminalSink.results.size)
        }
    }

    @Test
    public fun `timeout then success is retried only by reducer`() {
        val report = execute(DemoScenario.NFC_TIMEOUT_THEN_SUCCESS).report

        assertEquals(VerificationOutcome.VERIFIED, report.outcome)
        assertTrue(VerificationEvidence.STEP_RETRIED in report.evidence)
        assertEquals(2, report.effectNames.count { it == "StartNfcRead" })
    }

    @Test
    public fun `expired observation changes outcome when product policy changes`() {
        val strict = execute(DemoScenario.EXPIRED_DOCUMENT).report
        val permissive =
            execute(
                DemoScenario.EXPIRED_DOCUMENT,
                VerificationPolicy(rejectExpiredDocument = false),
            ).report

        assertTrue(VerificationEvidence.DOCUMENT_EXPIRED in strict.evidence)
        assertTrue(VerificationEvidence.DOCUMENT_EXPIRED in permissive.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in strict.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in permissive.evidence)
        assertEquals(VerificationOutcome.REJECTED, strict.outcome)
        assertEquals(VerificationOutcome.VERIFIED, permissive.outcome)
    }

    @Test
    public fun `passive auth observation changes outcome when product policy changes`() {
        val optional = execute(DemoScenario.PASSIVE_AUTH_FAILURE).report
        val required =
            execute(
                DemoScenario.PASSIVE_AUTH_FAILURE,
                VerificationPolicy(requirePassiveAuthentication = true),
            ).report

        assertEquals(optional.evidence, required.evidence)
        assertTrue(VerificationEvidence.PASSIVE_AUTHENTICATION_FAILED in optional.evidence)
        assertEquals(VerificationOutcome.VERIFIED, optional.outcome)
        assertEquals(VerificationOutcome.REJECTED, required.outcome)
    }

    @Test
    public fun `scenario definition cannot bypass reducer policy effect`() {
        val report = execute(DemoScenario.SUCCESS).report

        assertTrue("EvaluateVerificationPolicy" in report.effectNames)
        assertTrue("EmitTerminalResult" in report.effectNames)
        assertFalse(report.effectNames.any { it.contains("ScenarioOutcome") })
    }

    @Test
    public fun `success routes every feature effect through handler`() {
        val report = execute(DemoScenario.SUCCESS).report
        val requiredFeatureEffects =
            setOf(
                "CaptureDocument",
                "EvaluateDocumentQuality",
                "RunOcr",
                "ExtractAndValidateMrz",
                "StartNfcRead",
                "ValidateChipData",
                "ComparePrintedAndChipData",
                "CaptureSelfie",
                "EvaluateSelfieQuality",
                "CompareFaces",
            )

        assertTrue(report.effectNames.toSet().containsAll(requiredFeatureEffects))
    }

    @Test
    public fun `same scenario produces identical safe report`() {
        val first = execute(DemoScenario.NFC_TIMEOUT_THEN_SUCCESS).report
        val second = execute(DemoScenario.NFC_TIMEOUT_THEN_SUCCESS).report

        assertEquals(first.stateNames, second.stateNames)
        assertEquals(first.effectNames, second.effectNames)
        assertEquals(first.evidence, second.evidence)
        assertEquals(first.outcome, second.outcome)
        assertEquals(first.cleanupComplete, second.cleanupComplete)
    }

    @Test
    public fun `cleanup remains idempotent after terminal completion`() {
        val execution = execute(DemoScenario.SUCCESS)

        execution.runtime.orchestrator.cancel()
        execution.runtime.registry.clear()
        execution.runtime.scheduler.cancelAll(VerificationFixtures.sessionId)

        assertTrue(execution.runtime.registry.isCleared)
        assertEquals(0, execution.runtime.scheduler.scheduledCount)
        assertEquals(1, execution.runtime.terminalSink.results.size)
    }

    private fun execute(
        scenario: DemoScenario,
        policy: VerificationPolicy = VerificationPolicy(),
    ): Execution {
        val config = success(IdvConfig.demo(scenario))
        val runtime = success(DemoVerificationFactory.create(config, VerificationFixtures.sessionId, policy))
        val report = success(DemoVerificationRunner(runtime).run())
        return Execution(runtime, report)
    }

    private fun <T> success(result: IdvResult<T>): T = (result as IdvResult.Success).value

    private data class Execution(
        val runtime: DemoVerificationRuntime,
        val report: DemoRunReport,
    )
}
