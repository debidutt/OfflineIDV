package com.ing.offlineidv.demo

import android.app.Activity
import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.ui.AtlasRuntimeMode
import com.ing.offlineidv.ui.AtlasScenarioPresentationCatalog
import com.ing.offlineidv.ui.AtlasUiAction
import com.ing.offlineidv.ui.AtlasUiState
import com.ing.offlineidv.ui.VerificationUiStateMapper
import com.ing.offlineidv.verification.demo.DemoPromptMode
import com.ing.offlineidv.verification.demo.DemoVerificationFactory
import com.ing.offlineidv.verification.demo.DemoVerificationRuntime
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.Idle
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.orchestration.SerializedVerificationOrchestrator
import java.util.UUID

/** Creates one explicitly configured synthetic runtime per demo session. */
internal fun interface AtlasDemoRuntimeFactory {
    fun create(
        scenario: DemoScenario,
        sessionId: IdvSessionId,
    ): DemoVerificationRuntime
}

/** Generates opaque identifiers at the app composition boundary. */
internal fun interface AtlasDemoSessionIdFactory {
    fun create(): IdvSessionId
}

/** Creates one explicitly real Android runtime; no synthetic fallback is permitted. */
internal fun interface AtlasRealRuntimeFactory {
    fun create(sessionId: IdvSessionId): RealAndroidVerificationRuntime
}

/**
 * Presentation controller that translates UI intents into existing verification events.
 *
 * It does not inspect evidence to choose outcomes or progression. Those decisions remain in the
 * reducer and policy evaluator.
 */
internal class AtlasDemoController(
    private val runtimeFactory: AtlasDemoRuntimeFactory,
    private val sessionIdFactory: AtlasDemoSessionIdFactory,
    private val realRuntimeFactory: AtlasRealRuntimeFactory? = null,
) : AutoCloseable {
    private val observers = mutableListOf<(AtlasUiState) -> Unit>()
    private var runtime: DemoVerificationRuntime? = null
    private var realRuntime: RealAndroidVerificationRuntime? = null
    private var stateObservation: AutoCloseable? = null
    private var selectedScenario: DemoScenario = DemoScenario.SUCCESS
    private var nfcHost: Activity? = null

    var runtimeMode: AtlasRuntimeMode = AtlasRuntimeMode.DEMO
        private set

    var state: AtlasUiState = AtlasUiState.Welcome
        private set

    internal val activeRuntime: DemoVerificationRuntime?
        get() = runtime

    internal val activeRealRuntime: RealAndroidVerificationRuntime?
        get() = realRuntime

    fun observe(observer: (AtlasUiState) -> Unit): AutoCloseable {
        observers += observer
        observer(state)
        return AutoCloseable { observers.remove(observer) }
    }

    fun dispatch(action: AtlasUiAction) {
        when (action) {
            is AtlasUiAction.SelectRuntimeMode -> selectRuntimeMode(action.mode)
            AtlasUiAction.Start -> start()
            AtlasUiAction.ShowScenarios -> showScenarios()
            AtlasUiAction.CloseScenarios -> closeScenarios()
            is AtlasUiAction.SelectScenario -> selectScenario(action.scenario)
            AtlasUiAction.SelectPassport -> showPassportInstructions()
            AtlasUiAction.ContinuePassportInstructions -> continuePassportInstructions()
            AtlasUiAction.CaptureDocument -> activeOrchestrator?.dispatch(VerificationEvent.CaptureRequested)
            AtlasUiAction.StartNfc -> activeOrchestrator?.dispatch(VerificationEvent.NfcRequested)
            AtlasUiAction.CaptureSelfie -> activeOrchestrator?.dispatch(VerificationEvent.SelfieRequested)
            AtlasUiAction.Retry -> activeOrchestrator?.dispatch(VerificationEvent.Retry)
            AtlasUiAction.Cancel -> cancel()
            AtlasUiAction.StartAgain -> startAgain()
            AtlasUiAction.ReturnHome -> returnHome()
        }
    }

    private fun start() {
        if (hasRuntime) return
        val sessionId = sessionIdFactory.create()
        val orchestrator =
            when (runtimeMode) {
                AtlasRuntimeMode.DEMO -> {
                    val created = runtimeFactory.create(selectedScenario, sessionId)
                    runtime = created
                    created.orchestrator
                }

                AtlasRuntimeMode.REAL_ANDROID -> {
                    val created = realRuntimeFactory?.create(sessionId) ?: return
                    realRuntime = created
                    nfcHost?.let(created::attachNfcHost)
                    created.orchestrator
                }
            }
        stateObservation =
            orchestrator.observe { domainState ->
                update(VerificationUiStateMapper.map(domainState))
            }
        orchestrator.dispatch(VerificationEvent.Start(sessionId))
        runtime?.applyConfiguredLifecycleTrigger()
    }

    private fun showScenarios() {
        if (!hasRuntime && runtimeMode == AtlasRuntimeMode.DEMO) update(AtlasUiState.ScenarioSelector(selectedScenario))
    }

    private fun closeScenarios() {
        if (!hasRuntime) update(AtlasUiState.Welcome)
    }

    private fun selectScenario(scenario: DemoScenario) {
        if (hasRuntime || runtimeMode != AtlasRuntimeMode.DEMO || scenario !in supportedScenarios) return
        selectedScenario = scenario
        update(AtlasUiState.ScenarioSelector(selectedScenario))
    }

    private fun showPassportInstructions() {
        if (activeOrchestrator?.state is DocumentSelection) {
            update(AtlasUiState.PassportInstructions)
        }
    }

    private fun continuePassportInstructions() {
        val active = activeOrchestrator ?: return
        if (state != AtlasUiState.PassportInstructions) return
        active.dispatch(VerificationEvent.PassportSelected)
    }

    private fun cancel() {
        val active = activeOrchestrator
        if (active == null) {
            update(AtlasUiState.Welcome)
        } else if (active.state !is TerminalState) {
            active.cancel()
        }
    }

    private fun startAgain() {
        val active = activeOrchestrator ?: return
        if (active.state !is TerminalState || !cleanupComplete) return
        releaseRuntime()
        start()
    }

    private fun returnHome() {
        val active = activeOrchestrator
        if (active != null && active.state !is TerminalState && active.state !is Idle) {
            active.cancel()
        }
        releaseRuntime()
        update(AtlasUiState.Welcome)
    }

    private fun update(newState: AtlasUiState) {
        state = newState
        observers.toList().forEach { observer -> observer(newState) }
    }

    private fun releaseRuntime() {
        stateObservation?.close()
        stateObservation = null
        runtime = null
        realRuntime = null
    }

    override fun close() {
        val active = activeOrchestrator
        if (active != null && active.state !is TerminalState && active.state !is Idle) {
            active.cancel()
        }
        releaseRuntime()
        nfcHost = null
        observers.clear()
    }

    fun attachPreview(surfaceProvider: androidx.camera.core.Preview.SurfaceProvider?) {
        realRuntime?.attachPreview(surfaceProvider)
    }

    fun attachNfcHost(activity: Activity) {
        nfcHost = activity
        realRuntime?.attachNfcHost(activity)
    }

    fun detachNfcHost(activity: Activity) {
        if (nfcHost === activity) nfcHost = null
        realRuntime?.detachNfcHost(activity)
    }

    fun nfcCapability(): com.ing.offlineidv.nfc.NfcCapability? = realRuntime?.nfcCapability()

    private fun selectRuntimeMode(mode: AtlasRuntimeMode) {
        if (hasRuntime || state != AtlasUiState.Welcome) return
        runtimeMode = mode
        update(AtlasUiState.Welcome)
    }

    private val hasRuntime: Boolean
        get() = runtime != null || realRuntime != null

    private val activeOrchestrator: SerializedVerificationOrchestrator?
        get() = runtime?.orchestrator ?: realRuntime?.orchestrator

    private val cleanupComplete: Boolean
        get() = runtime?.effectHandler?.cleanupComplete ?: realRuntime?.effectHandler?.cleanupComplete ?: false

    private companion object {
        val supportedScenarios: Set<DemoScenario> =
            AtlasScenarioPresentationCatalog.groups
                .flatMap { it.items }
                .map { it.scenario }
                .toSet()
    }
}

/** Sole app-demo composition root. No composable constructs runtime or feature implementations. */
internal object AtlasDemoCompositionRoot {
    fun createController(
        context: android.content.Context? = null,
        permissionGateway: CameraPermissionGateway? = null,
    ): AtlasDemoController =
        AtlasDemoController(
            runtimeFactory =
                AtlasDemoRuntimeFactory { scenario, sessionId ->
                    val config = (IdvConfig.demo(scenario) as IdvResult.Success).value
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
                    val value = "atlas_${UUID.randomUUID().toString().replace("-", "")}"
                    (IdvSessionId.parse(value) as IdvResult.Success).value
                },
            realRuntimeFactory =
                if (context != null && permissionGateway != null) {
                    AtlasRealRuntimeFactory { sessionId ->
                        RealAndroidVerificationFactory.create(context, sessionId, permissionGateway)
                    }
                } else {
                    null
                },
        )
}
