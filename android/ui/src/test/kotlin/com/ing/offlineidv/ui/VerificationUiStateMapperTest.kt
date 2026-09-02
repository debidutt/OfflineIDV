package com.ing.offlineidv.ui

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.verification.demo.DemoPromptMode
import com.ing.offlineidv.verification.demo.DemoVerificationFactory
import com.ing.offlineidv.verification.demo.DemoVerificationRunner
import com.ing.offlineidv.verification.demo.DemoVerificationRuntime
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CameraReady
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationUiStateMapperTest {
    @Test
    public fun `idle maps to welcome`() {
        assertEquals(AtlasUiState.Welcome, VerificationUiStateMapper.map(com.ing.offlineidv.verification.model.Idle))
    }

    @Test
    public fun `document selection maps to available document screen`() {
        assertTrue(mapSuccessState<DocumentSelection>() is AtlasUiState.DocumentSelection)
    }

    @Test
    public fun `camera ready maps to document capture`() {
        assertTrue(mapSuccessState<CameraReady>() is AtlasUiState.DocumentCapture)
    }

    @Test
    public fun `document capture and quality states map to document processing`() {
        val mapped = mappedSuccessStates()
        assertTrue(mapped.any { it is AtlasUiState.Processing && it.title == "Capturing passport" })
        assertTrue(mapped.any { it is AtlasUiState.Processing && it.title == "Checking image quality" })
    }

    @Test
    public fun `ocr and mrz states map to grouped safe processing`() {
        val titles = mappedSuccessStates().filterIsInstance<AtlasUiState.Processing>().map { it.title }
        assertTrue("Reading passport" in titles)
        assertTrue("Finding the MRZ" in titles)
        assertTrue("Checking the MRZ" in titles)
    }

    @Test
    public fun `awaiting nfc maps safe mrz evidence only`() {
        val mapped = mapSuccessState<AwaitingNfc>() as AtlasUiState.Nfc

        assertTrue(mapped.evidence.any { it.title == "MRZ structure" })
        assertFalse(mapped.toString().contains("passportNumber", ignoreCase = true))
    }

    @Test
    public fun `chip states map to chip progress`() {
        val titles = mappedSuccessStates().filterIsInstance<AtlasUiState.Processing>().map { it.title }
        assertTrue("Reading passport chip" in titles)
        assertTrue("Checking chip signals" in titles)
        assertTrue("Comparing passport signals" in titles)
    }

    @Test
    public fun `awaiting selfie maps safe completed evidence`() {
        val mapped = mapSuccessState<AwaitingSelfie>() as AtlasUiState.Selfie

        assertTrue(mapped.evidence.any { it.title == "Passport chip" })
    }

    @Test
    public fun `face states map to face progress`() {
        val titles = mappedSuccessStates().filterIsInstance<AtlasUiState.Processing>().map { it.title }
        assertTrue("Capturing selfie" in titles)
        assertTrue("Checking selfie quality" in titles)
        assertTrue("Comparing faces" in titles)
    }

    @Test
    public fun `recovery uses reducer retry decision`() {
        val runtime = interactiveRuntime(DemoScenario.NFC_TIMEOUT_THEN_SUCCESS)
        driveToNfc(runtime)
        runtime.orchestrator.dispatch(VerificationEvent.NfcRequested)

        val domain = runtime.orchestrator.state as RecoveryRequired
        val mapped = VerificationUiStateMapper.map(domain) as AtlasUiState.Recovery

        assertTrue(mapped.canRetry)
        assertEquals("Chip read needs attention", mapped.title)
    }

    @Test
    public fun `verified terminal maps to verified result`() {
        assertTerminal(DemoScenario.SUCCESS, VerificationOutcome.VERIFIED)
    }

    @Test
    public fun `rejected terminal maps to rejected result`() {
        assertTerminal(DemoScenario.FACE_MISMATCH, VerificationOutcome.REJECTED)
    }

    @Test
    public fun `inconclusive terminal maps to inconclusive result`() {
        assertTerminal(DemoScenario.FACE_INCONCLUSIVE, VerificationOutcome.INCONCLUSIVE)
    }

    @Test
    public fun `technical terminal maps to technical result`() {
        assertTerminal(DemoScenario.TECHNICAL_FAILURE, VerificationOutcome.TECHNICAL_FAILURE)
    }

    @Test
    public fun `cancelled and expired terminals remain distinct`() {
        assertTerminal(DemoScenario.USER_CANCELLED, VerificationOutcome.CANCELLED)
        assertTerminal(DemoScenario.SESSION_EXPIRED, VerificationOutcome.EXPIRED)
    }

    @Test
    public fun `same passive auth evidence renders the outcome selected by each policy`() {
        val optional = terminalResult(DemoScenario.PASSIVE_AUTH_FAILURE, VerificationPolicy())
        val required =
            terminalResult(
                DemoScenario.PASSIVE_AUTH_FAILURE,
                VerificationPolicy(requirePassiveAuthentication = true),
            )

        assertEquals(optional.evidence, required.evidence)
        assertTrue(optional.evidence.any { it.title == "Chip authentication" })
        assertEquals(VerificationOutcome.VERIFIED, optional.outcome)
        assertEquals(VerificationOutcome.REJECTED, required.outcome)
    }

    @Test
    public fun `same expired evidence renders the outcome selected by each policy`() {
        val strict = terminalResult(DemoScenario.EXPIRED_DOCUMENT, VerificationPolicy())
        val permissive =
            terminalResult(
                DemoScenario.EXPIRED_DOCUMENT,
                VerificationPolicy(rejectExpiredDocument = false),
            )

        val strictExpiry = strict.evidence.single { it.title == "Document expiry" }
        val permissiveExpiry = permissive.evidence.single { it.title == "Document expiry" }
        assertEquals(strictExpiry, permissiveExpiry)
        assertEquals(VerificationOutcome.REJECTED, strict.outcome)
        assertEquals(VerificationOutcome.VERIFIED, permissive.outcome)
    }

    @Test
    public fun `presentation model structurally excludes references and session identifiers`() {
        val forbiddenTypes = setOf(VerificationArtifactReference::class.java, IdvSessionId::class.java)

        AtlasUiState::class.java.declaredClasses.forEach { type ->
            assertTrue(type.declaredFields.none { it.type in forbiddenTypes })
        }
        mappedSuccessStates().forEach { mapped ->
            assertFalse(mapped.toString().contains("reference", ignoreCase = true))
            assertFalse(mapped.toString().contains("atlas_test_session"))
        }
    }

    private inline fun <reified T : VerificationState> mapSuccessState(): AtlasUiState {
        val domain = successStates().first { it is T }
        return VerificationUiStateMapper.map(domain)
    }

    private fun mappedSuccessStates(): List<AtlasUiState> = successStates().map(VerificationUiStateMapper::map)

    private fun successStates(): List<VerificationState> {
        val runtime = interactiveRuntime(DemoScenario.SUCCESS)
        val states = mutableListOf<VerificationState>()
        val observation = runtime.orchestrator.observe { states += it }
        runtime.orchestrator.dispatch(VerificationEvent.Start(runtime.sessionId))
        runtime.orchestrator.dispatch(VerificationEvent.PassportSelected)
        runtime.orchestrator.dispatch(VerificationEvent.CaptureRequested)
        runtime.orchestrator.dispatch(VerificationEvent.NfcRequested)
        runtime.orchestrator.dispatch(VerificationEvent.SelfieRequested)
        observation.close()
        return states
    }

    private fun assertTerminal(
        scenario: DemoScenario,
        expected: VerificationOutcome,
    ) {
        val mapped = terminalResult(scenario)
        assertEquals(expected, mapped.outcome)
    }

    private fun terminalResult(
        scenario: DemoScenario,
        policy: VerificationPolicy = VerificationPolicy(),
    ): AtlasUiState.Result {
        val runtime = automaticRuntime(scenario, policy)
        DemoVerificationRunner(runtime).run()
        return VerificationUiStateMapper.map(runtime.orchestrator.state as TerminalState) as AtlasUiState.Result
    }

    private fun driveToNfc(runtime: DemoVerificationRuntime) {
        runtime.orchestrator.dispatch(VerificationEvent.Start(runtime.sessionId))
        runtime.orchestrator.dispatch(VerificationEvent.PassportSelected)
        runtime.orchestrator.dispatch(VerificationEvent.CaptureRequested)
    }

    private fun interactiveRuntime(scenario: DemoScenario): DemoVerificationRuntime = runtime(scenario, DemoPromptMode.HOST_CONTROLLED)

    private fun automaticRuntime(
        scenario: DemoScenario,
        policy: VerificationPolicy = VerificationPolicy(),
    ): DemoVerificationRuntime = runtime(scenario, DemoPromptMode.AUTOMATIC, policy)

    private fun runtime(
        scenario: DemoScenario,
        promptMode: DemoPromptMode,
        policy: VerificationPolicy = VerificationPolicy(),
    ): DemoVerificationRuntime {
        val config = (IdvConfig.demo(scenario) as IdvResult.Success).value
        val session = (IdvSessionId.parse("atlas_test_session") as IdvResult.Success).value
        return (
            DemoVerificationFactory.create(
                config = config,
                sessionId = session,
                policy = policy,
                promptMode = promptMode,
            ) as IdvResult.Success
        ).value
    }
}
