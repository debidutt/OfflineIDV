package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.demo.FakeDocumentCaptureEngine
import com.ing.offlineidv.camera.demo.FakeDocumentQualityEngine
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.config.IdvMode
import com.ing.offlineidv.core.error.ConfigurationFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.face.demo.FakeFaceMatchEngine
import com.ing.offlineidv.face.demo.FakeSelfieCaptureEngine
import com.ing.offlineidv.face.demo.FakeSelfieQualityEngine
import com.ing.offlineidv.nfc.demo.FakeChipValidationEngine
import com.ing.offlineidv.nfc.demo.FakePassportNfcEngine
import com.ing.offlineidv.nfc.demo.FakePrintedChipComparisonEngine
import com.ing.offlineidv.ocr.demo.FakeOcrEngine
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CameraReady
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.RetryDecision
import com.ing.offlineidv.verification.model.RetryPolicy
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.VerificationCapabilities
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.model.VerificationTimeoutPolicy
import com.ing.offlineidv.verification.orchestration.SerializedVerificationOrchestrator
import com.ing.offlineidv.verification.orchestration.VerificationStateObserver
import com.ing.offlineidv.verification.scheduling.DeterministicVerificationScheduler

/** Fully injected, single-session Demo Mode runtime. */
public class DemoVerificationRuntime internal constructor(
    public val sessionId: IdvSessionId,
    public val definition: DemoScenarioDefinition,
    public val registry: DemoArtifactRegistry,
    public val scheduler: DeterministicVerificationScheduler,
    public val terminalSink: RecordingDemoTerminalResultSink,
    public val effectHandler: DemoVerificationEffectHandler,
    public val orchestrator: SerializedVerificationOrchestrator,
) {
    /** Applies the scenario's configured host lifecycle stimulus after initialization, if any. */
    public fun applyConfiguredLifecycleTrigger() {
        when (definition.lifecycleTrigger) {
            DemoLifecycleTrigger.CANCEL_AFTER_INITIALIZATION -> {
                orchestrator.cancel()
            }

            DemoLifecycleTrigger.EXPIRE_AFTER_INITIALIZATION -> {
                val token = scheduler.scheduledOperations().singleOrNull { it.step == VerificationStep.SESSION }
                if (token != null) scheduler.fire(token)
            }

            DemoLifecycleTrigger.NONE -> {
                Unit
            }
        }
    }
}

/** Explicit factory that refuses to bind synthetic engines for Production Mode. */
public object DemoVerificationFactory {
    public fun create(
        config: IdvConfig,
        sessionId: IdvSessionId,
        policy: VerificationPolicy = VerificationPolicy(),
        retryPolicy: RetryPolicy = RetryPolicy(),
        promptMode: DemoPromptMode = DemoPromptMode.AUTOMATIC,
    ): IdvResult<DemoVerificationRuntime> {
        val scenario = config.demoScenario
        if (config.mode != IdvMode.DEMO || scenario == null) {
            return IdvResult.Failure(
                IdvError.Configuration(ConfigurationFailure.DEMO_MODE_REQUIRED),
            )
        }
        val definition = DemoScenarioCatalog.definitionFor(scenario)
        val registry = DemoArtifactRegistry(sessionId)
        val scheduler = DeterministicVerificationScheduler()
        val terminalSink = RecordingDemoTerminalResultSink()
        val capture = FakeDocumentCaptureEngine(definition.documentCapture)
        val documentQuality = FakeDocumentQualityEngine(definition.documentQuality)
        val ocr = FakeOcrEngine(definition.ocr)
        val nfc = FakePassportNfcEngine(definition.nfcRead)
        val chipValidation = FakeChipValidationEngine(definition.chipValidation)
        val printedChip = FakePrintedChipComparisonEngine(definition.printedChipComparison)
        val selfieCapture = FakeSelfieCaptureEngine(definition.selfieCapture)
        val selfieQuality = FakeSelfieQualityEngine(definition.selfieQuality)
        val faceMatch = FakeFaceMatchEngine(definition.faceMatch)
        val resources =
            listOf(
                DemoSessionResource(capture::reset),
                DemoSessionResource(documentQuality::reset),
                DemoSessionResource(ocr::reset),
                DemoSessionResource(nfc::reset),
                DemoSessionResource(chipValidation::reset),
                DemoSessionResource(printedChip::reset),
                DemoSessionResource(selfieCapture::reset),
                DemoSessionResource(selfieQuality::reset),
                DemoSessionResource(faceMatch::reset),
            )
        val handler =
            DemoVerificationEffectHandler(
                documentCaptureEngine = capture,
                documentQualityEngine = documentQuality,
                ocrEngine = ocr,
                nfcEngine = nfc,
                chipValidationEngine = chipValidation,
                printedChipComparisonEngine = printedChip,
                selfieCaptureEngine = selfieCapture,
                selfieQualityEngine = selfieQuality,
                faceMatchEngine = faceMatch,
                mrzPipeline = DemoMrzPipeline(registry, definition.mrzReferenceDate),
                registry = registry,
                scheduler = scheduler,
                terminalSink = terminalSink,
                sessionResources = resources,
                promptMode = promptMode,
            )
        val context =
            VerificationContext(
                policy = policy,
                retryPolicy = retryPolicy,
                capabilities = VerificationCapabilities(definition.capabilities),
                timeoutPolicy = VerificationTimeoutPolicy(session = config.sessionTimeout),
            )
        val orchestrator = SerializedVerificationOrchestrator(context, handler)
        return IdvResult.Success(
            DemoVerificationRuntime(
                sessionId = sessionId,
                definition = definition,
                registry = registry,
                scheduler = scheduler,
                terminalSink = terminalSink,
                effectHandler = handler,
                orchestrator = orchestrator,
            ),
        )
    }
}

/** Safe output of a complete synthetic run. */
public class DemoRunReport(
    stateNames: List<String>,
    effectNames: List<String>,
    public val outcome: VerificationOutcome,
    evidence: Set<VerificationEvidence>,
    public val cleanupComplete: Boolean,
) {
    public val stateNames: List<String> = stateNames.toList()
    public val effectNames: List<String> = effectNames.toList()
    public val evidence: Set<VerificationEvidence> = evidence.toSet()

    override fun toString(): String =
        "DemoRunReport(states=$stateNames, effects=$effectNames, outcome=$outcome, " +
            "evidence=$evidence, cleanupComplete=$cleanupComplete)"
}

/** Platform-neutral host simulator that obtains its outcome only from the reducer. */
public class DemoVerificationRunner(
    private val runtime: DemoVerificationRuntime,
) {
    public fun run(): IdvResult<DemoRunReport> {
        val stateNames = mutableListOf<String>()
        val observation =
            runtime.orchestrator.observe(
                VerificationStateObserver { state -> stateNames += state.javaClass.simpleName },
            )
        try {
            runtime.orchestrator.dispatch(VerificationEvent.Start(runtime.sessionId))
            runtime.applyConfiguredLifecycleTrigger()
            if (runtime.definition.lifecycleTrigger == DemoLifecycleTrigger.NONE) driveHostEvents()
        } finally {
            observation.close()
        }
        val terminal = runtime.orchestrator.state as? TerminalState ?: return IdvResult.Failure(IdvError.Internal)
        return IdvResult.Success(
            DemoRunReport(
                stateNames = stateNames,
                effectNames = runtime.effectHandler.effectNames,
                outcome = terminal.summary.outcome,
                evidence = terminal.summary.evidence,
                cleanupComplete = runtime.effectHandler.cleanupComplete,
            ),
        )
    }

    private fun driveHostEvents() {
        repeat(MAX_HOST_ACTIONS) {
            when (val current = runtime.orchestrator.state) {
                is TerminalState -> {
                    return
                }

                is DocumentSelection -> {
                    runtime.orchestrator.dispatch(VerificationEvent.PassportSelected)
                }

                is CameraReady -> {
                    runtime.orchestrator.dispatch(VerificationEvent.CaptureRequested)
                }

                is AwaitingNfc -> {
                    runtime.orchestrator.dispatch(VerificationEvent.NfcRequested)
                }

                is AwaitingSelfie -> {
                    runtime.orchestrator.dispatch(VerificationEvent.SelfieRequested)
                }

                is RecoveryRequired -> {
                    if (current.retryDecision == RetryDecision.RETRY_AVAILABLE) {
                        runtime.orchestrator.dispatch(VerificationEvent.Retry)
                    } else {
                        runtime.orchestrator.dispatch(VerificationEvent.AcknowledgeError)
                    }
                }

                else -> {
                    return
                }
            }
        }
    }

    private companion object {
        const val MAX_HOST_ACTIONS: Int = 64
    }
}
