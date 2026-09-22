package com.ing.offlineidv.demo

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Handler
import android.os.Looper
import androidx.camera.core.Preview
import androidx.lifecycle.ProcessLifecycleOwner
import com.ing.offlineidv.camera.AsyncCameraPreparationEngine
import com.ing.offlineidv.camera.AsyncDocumentCaptureEngine
import com.ing.offlineidv.camera.AsyncDocumentQualityEngine
import com.ing.offlineidv.camera.CameraPreparationResult
import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.camera.DocumentCaptureRequest
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.camera.real.CameraXDocumentCaptureEngine
import com.ing.offlineidv.camera.real.CameraXDocumentQualityEngine
import com.ing.offlineidv.camera.real.InMemoryCapturedImageStore
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.FaceFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.AsyncPassportNfcEngine
import com.ing.offlineidv.nfc.ChipAuthenticationObservation
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.ChipValidationEngine
import com.ing.offlineidv.nfc.ChipValidationResult
import com.ing.offlineidv.nfc.MrzPrintedChipComparisonEngine
import com.ing.offlineidv.nfc.NfcCapability
import com.ing.offlineidv.nfc.NfcReadProgress
import com.ing.offlineidv.nfc.NfcReadProgressObserver
import com.ing.offlineidv.nfc.NfcReadRequest
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.NfcSessionCoordinator
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PassportChipValidationEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonResult
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.nfc.real.AndroidNfcCapabilityDetector
import com.ing.offlineidv.nfc.real.AndroidNfcTagDiscovery
import com.ing.offlineidv.ocr.AsyncOcrEngine
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.OcrImage
import com.ing.offlineidv.ocr.OcrImageSource
import com.ing.offlineidv.ocr.real.MlKitOcrEngine
import com.ing.offlineidv.verification.artifact.SessionArtifactStore
import com.ing.offlineidv.verification.model.ChipAuthenticationStatus
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.NfcReadPhase
import com.ing.offlineidv.verification.model.PassiveAuthenticationStatus
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationCapabilities
import com.ing.offlineidv.verification.model.VerificationCapability
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationOperationToken
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.model.VerificationTerminalSummary
import com.ing.offlineidv.verification.orchestration.SerializedVerificationOrchestrator
import com.ing.offlineidv.verification.orchestration.VerificationEffectHandler
import com.ing.offlineidv.verification.orchestration.VerificationEventSink
import com.ing.offlineidv.verification.real.RealMrzPipeline
import com.ing.offlineidv.verification.real.RealMrzProcessor
import com.ing.offlineidv.verification.real.RealTd1MrzPipeline
import com.ing.offlineidv.verification.scheduling.VerificationScheduler
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Runtime camera-permission boundary that can be rebound across Activity recreation. */
internal class CameraPermissionGateway {
    private var requester: (() -> Unit)? = null
    private var pendingResult: ((Boolean) -> Unit)? = null

    @Synchronized
    fun attach(requester: () -> Unit) {
        this.requester = requester
    }

    @Synchronized
    fun detach(requester: () -> Unit) {
        if (this.requester === requester) this.requester = null
    }

    fun request(callback: (Boolean) -> Unit) {
        val request =
            synchronized(this) {
                pendingResult = callback
                requester
            }
        if (request == null) complete(false) else request()
    }

    fun complete(granted: Boolean) {
        val callback = synchronized(this) { pendingResult.also { pendingResult = null } }
        callback?.invoke(granted)
    }

    @Synchronized
    fun clear() {
        pendingResult = null
        requester = null
    }
}

/** Main-thread event bridge preserving one serialized orchestrator entry point. */
internal fun interface RealEventDispatcher {
    fun dispatch(block: () -> Unit)
}

/** Handler-backed scheduler for tokenized reducer timeouts. */
internal class MainThreadVerificationScheduler(
    private val handler: Handler,
) : VerificationScheduler {
    private val callbacks = ConcurrentHashMap<VerificationOperationToken, Runnable>()

    override fun schedule(
        operation: VerificationOperationToken,
        duration: Duration,
        onTimeout: () -> Unit,
    ) {
        val callback =
            Runnable {
                callbacks.remove(operation)
                onTimeout()
            }
        callbacks.put(operation, callback)?.let(handler::removeCallbacks)
        handler.postDelayed(callback, duration.toMillis())
    }

    override fun cancel(operation: VerificationOperationToken) {
        callbacks.remove(operation)?.let(handler::removeCallbacks)
    }

    override fun cancelAll(sessionId: IdvSessionId) {
        callbacks.keys.filter { it.sessionId == sessionId }.forEach(::cancel)
    }
}

/** Real CameraX/ML Kit effect translator; the reducer remains the sole flow authority. */
internal class RealVerificationEffectHandler(
    private val sessionId: IdvSessionId,
    private val permissionGateway: CameraPermissionGateway,
    private val cameraPreparationEngine: AsyncCameraPreparationEngine,
    private val documentCaptureEngine: AsyncDocumentCaptureEngine,
    private val documentQualityEngine: AsyncDocumentQualityEngine,
    private val ocrEngine: AsyncOcrEngine,
    private val mrzPipeline: RealMrzProcessor,
    private val nfcEngine: AsyncPassportNfcEngine,
    private val chipValidationEngine: ChipValidationEngine,
    private val printedChipComparisonEngine: PrintedChipComparisonEngine,
    private val artifactStore: SessionArtifactStore,
    private val imageStore: InMemoryCapturedImageStore,
    private val scheduler: VerificationScheduler,
    private val eventDispatcher: RealEventDispatcher,
    private val backgroundExecutor: ExecutorService,
    private val closeableResources: List<AutoCloseable>,
) : VerificationEffectHandler {
    private val operations = ConcurrentHashMap<VerificationOperationToken, OperationSlot>()
    private val terminalResults = mutableListOf<VerificationTerminalSummary>()

    @Volatile var cleanupComplete: Boolean = false
        private set

    override fun handle(
        effect: VerificationEffect,
        eventSink: VerificationEventSink,
    ) {
        when (effect) {
            is VerificationEffect.InitializeSession -> {
                emit(eventSink, VerificationEvent.InitializationSucceeded(effect.operation))
            }

            is VerificationEffect.RequestCameraPermission -> {
                permissionGateway.request { granted ->
                    emit(
                        eventSink,
                        if (granted) VerificationEvent.CameraPermissionGranted else VerificationEvent.CameraPermissionDenied,
                    )
                }
            }

            is VerificationEffect.PrepareCamera -> {
                start(effect.operation, eventSink) { complete ->
                    cameraPreparationEngine.prepare { result ->
                        complete(
                            when (result) {
                                CameraPreparationResult.Ready -> {
                                    VerificationEvent.CameraReady(effect.operation)
                                }

                                is CameraPreparationResult.Failed -> {
                                    VerificationEvent.CameraPreparationFailed(effect.operation, result.error)
                                }
                            },
                        )
                    }
                }
            }

            is VerificationEffect.CaptureDocument -> {
                capture(effect, eventSink)
            }

            is VerificationEffect.EvaluateDocumentQuality -> {
                quality(effect, eventSink)
            }

            is VerificationEffect.RunOcr -> {
                ocr(effect, eventSink)
            }

            is VerificationEffect.ExtractAndValidateMrz -> {
                mrz(effect, eventSink)
            }

            is VerificationEffect.PromptForNfc -> {
                Unit
            }

            is VerificationEffect.StartNfcRead -> {
                readNfc(effect, eventSink)
            }

            is VerificationEffect.ValidateChipData -> {
                validateChip(effect, eventSink)
            }

            is VerificationEffect.ComparePrintedAndChipData -> {
                comparePrintedChip(effect, eventSink)
            }

            is VerificationEffect.PromptForSelfie -> {
                Unit
            }

            is VerificationEffect.CaptureSelfie -> {
                emit(
                    eventSink,
                    VerificationEvent.SelfieCaptureFailed(effect.operation, IdvError.Face(FaceFailure.NO_FACE)),
                )
            }

            is VerificationEffect.EvaluateSelfieQuality -> {
                emit(
                    eventSink,
                    VerificationEvent.SelfieQualityFailed(effect.operation, IdvError.Face(FaceFailure.QUALITY_REJECTED)),
                )
            }

            is VerificationEffect.CompareFaces -> {
                emit(
                    eventSink,
                    VerificationEvent.FaceComparisonFailed(
                        effect.operation,
                        IdvError.Face(FaceFailure.COMPARISON_FAILED),
                    ),
                )
            }

            is VerificationEffect.EvaluateVerificationPolicy -> {
                emit(
                    eventSink,
                    VerificationEvent.DecisionCompleted(effect.operation),
                )
            }

            is VerificationEffect.ScheduleTimeout -> {
                scheduler.schedule(effect.operation, effect.duration) {
                    operations.remove(effect.operation)?.cancel()
                    emit(
                        eventSink,
                        if (effect.operation.step == VerificationStep.SESSION) {
                            VerificationEvent.SessionExpired(effect.operation.sessionId)
                        } else {
                            VerificationEvent.SessionTimedOut(effect.operation)
                        },
                    )
                }
            }

            is VerificationEffect.CancelTimeout -> {
                scheduler.cancel(effect.operation)
            }

            is VerificationEffect.ClearSensitiveSessionData -> {
                clear(effect.sessionId)
            }

            is VerificationEffect.EmitTerminalResult -> {
                synchronized(terminalResults) { terminalResults += effect.summary }
            }
        }
    }

    private fun capture(
        effect: VerificationEffect.CaptureDocument,
        sink: VerificationEventSink,
    ) {
        start(effect.operation, sink) { complete ->
            documentCaptureEngine.capture(DocumentCaptureRequest(effect.operation.sessionId)) { result ->
                when (result) {
                    is DocumentCaptureResult.Failed -> {
                        complete(VerificationEvent.CaptureFailed(effect.operation, result.error))
                    }

                    is DocumentCaptureResult.Captured -> {
                        when (val stored = artifactStore.register(VerificationArtifactKind.DOCUMENT_CAPTURE, result.artifact)) {
                            is IdvResult.Success -> {
                                complete(
                                    VerificationEvent.DocumentCaptured(effect.operation, stored.value),
                                )
                            }

                            is IdvResult.Failure -> {
                                complete(VerificationEvent.CaptureFailed(effect.operation, stored.error))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun quality(
        effect: VerificationEffect.EvaluateDocumentQuality,
        sink: VerificationEventSink,
    ) {
        val artifact = resolveDocument(effect.documentReference)
        if (artifact is IdvResult.Failure) {
            emit(sink, VerificationEvent.DocumentQualityFailed(effect.operation, artifact.error))
            return
        }
        start(effect.operation, sink) { complete ->
            documentQualityEngine.evaluate((artifact as IdvResult.Success).value) { result ->
                complete(
                    when (result) {
                        DocumentQualityResult.Accepted -> VerificationEvent.CaptureQualityAccepted(effect.operation)
                        DocumentQualityResult.Rejected -> VerificationEvent.CaptureQualityRejected(effect.operation)
                        is DocumentQualityResult.Failed -> VerificationEvent.DocumentQualityFailed(effect.operation, result.error)
                    },
                )
            }
        }
    }

    private fun ocr(
        effect: VerificationEffect.RunOcr,
        sink: VerificationEventSink,
    ) {
        val document = resolveDocument(effect.documentReference)
        if (document is IdvResult.Failure) {
            emit(sink, VerificationEvent.OcrFailed(effect.operation, document.error))
            return
        }
        val input = OcrDocumentInput((document as IdvResult.Success).value.sourceToken)
        start(effect.operation, sink) { complete ->
            ocrEngine.recognize(input) { result ->
                when (result) {
                    is OcrEngineResult.Failed -> {
                        complete(VerificationEvent.OcrFailed(effect.operation, result.error))
                    }

                    is OcrEngineResult.Recognized -> {
                        when (val stored = artifactStore.register(VerificationArtifactKind.OCR_RESULT, result.artifact)) {
                            is IdvResult.Success -> complete(VerificationEvent.OcrSucceeded(effect.operation, stored.value))
                            is IdvResult.Failure -> complete(VerificationEvent.OcrFailed(effect.operation, stored.error))
                        }
                    }
                }
            }
        }
    }

    private fun mrz(
        effect: VerificationEffect.ExtractAndValidateMrz,
        sink: VerificationEventSink,
    ) {
        start(effect.operation, sink) { complete ->
            val cancelled = AtomicBoolean(false)
            backgroundExecutor.execute {
                val result = mrzPipeline.process(effect.ocrReference)
                if (cancelled.get()) return@execute
                when (result) {
                    is IdvResult.Failure -> {
                        complete(VerificationEvent.MrzExtractionFailed(effect.operation, result.error))
                    }

                    is IdvResult.Success -> {
                        emit(sink, VerificationEvent.MrzExtractionSucceeded(effect.operation))
                        complete(
                            VerificationEvent.MrzValidationCompleted(
                                operation = effect.operation,
                                summary = result.value.summary,
                                printedDataReference = result.value.printedDataReference,
                                accessKeyReference = result.value.accessKeyReference,
                            ),
                        )
                    }
                }
            }
            CancellableOperation { cancelled.set(true) }
        }
    }

    private fun readNfc(
        effect: VerificationEffect.StartNfcRead,
        sink: VerificationEventSink,
    ) {
        val accessKey =
            artifactStore.resolve(
                effect.accessKeyReference,
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                PassportAccessKey::class.java,
            )
        if (accessKey is IdvResult.Failure) {
            emit(sink, VerificationEvent.NfcReadFailed(effect.operation, accessKey.error))
            return
        }
        emit(sink, VerificationEvent.NfcStarted(effect.operation))
        start(effect.operation, sink) { complete ->
            nfcEngine.read(
                NfcReadRequest(effect.operation.sessionId, (accessKey as IdvResult.Success).value),
                NfcReadProgressObserver { progress ->
                    emit(
                        sink,
                        VerificationEvent.NfcProgressed(
                            effect.operation,
                            progress.toVerificationPhase(),
                        ),
                    )
                },
            ) { result ->
                when (result) {
                    is NfcReadResult.Read -> {
                        when (val stored = artifactStore.register(VerificationArtifactKind.NFC_CHIP_DATA, result.artifact)) {
                            is IdvResult.Success -> {
                                complete(VerificationEvent.NfcReadSucceeded(effect.operation, stored.value))
                            }

                            is IdvResult.Failure -> {
                                result.artifact.close()
                                complete(VerificationEvent.NfcReadFailed(effect.operation, stored.error))
                            }
                        }
                    }

                    NfcReadResult.Timeout -> {
                        complete(VerificationEvent.SessionTimedOut(effect.operation))
                    }

                    NfcReadResult.Unavailable -> {
                        complete(VerificationEvent.NfcReadFailed(effect.operation, IdvError.Nfc(NfcFailure.UNAVAILABLE)))
                    }

                    is NfcReadResult.Failed -> {
                        complete(VerificationEvent.NfcReadFailed(effect.operation, result.error))
                    }
                }
            }
        }
    }

    private fun validateChip(
        effect: VerificationEffect.ValidateChipData,
        sink: VerificationEventSink,
    ) {
        val artifact =
            artifactStore.resolve(
                effect.chipReference,
                VerificationArtifactKind.NFC_CHIP_DATA,
                ChipDataArtifact::class.java,
            )
        if (artifact is IdvResult.Failure) {
            emit(sink, VerificationEvent.ChipValidationFailed(effect.operation, artifact.error))
            return
        }
        start(effect.operation, sink) { complete ->
            val cancelled = AtomicBoolean(false)
            backgroundExecutor.execute {
                if (cancelled.get()) return@execute
                when (val result = chipValidationEngine.validate((artifact as IdvResult.Success).value)) {
                    is ChipValidationResult.Failed -> {
                        complete(VerificationEvent.ChipValidationFailed(effect.operation, result.error))
                    }

                    is ChipValidationResult.Validated -> {
                        chipValidationEvent(effect.operation, result, complete)
                    }
                }
            }
            CancellableOperation { cancelled.set(true) }
        }
    }

    private fun chipValidationEvent(
        operation: VerificationOperationToken,
        result: ChipValidationResult.Validated,
        complete: (VerificationEvent) -> Unit,
    ) {
        val observation = result.observation
        val portraitReference =
            observation.portrait?.let { portrait ->
                artifactStore.register(VerificationArtifactKind.CHIP_PORTRAIT, portrait)
            }
        if (portraitReference is IdvResult.Failure) {
            observation.portrait?.close()
            complete(VerificationEvent.ChipValidationFailed(operation, portraitReference.error))
            return
        }
        complete(
            VerificationEvent.ChipValidationCompleted(
                operation,
                ChipValidationSummary(
                    dg1Available = observation.dg1Available,
                    dg2Available = observation.dg2Available,
                    passiveAuthentication = observation.passiveAuthentication.toVerificationStatus(),
                    chipAuthentication = observation.chipAuthentication.toVerificationStatus(),
                    portraitReference = (portraitReference as? IdvResult.Success)?.value,
                ),
            ),
        )
    }

    private fun comparePrintedChip(
        effect: VerificationEffect.ComparePrintedAndChipData,
        sink: VerificationEventSink,
    ) {
        val printed =
            artifactStore.resolve(
                effect.printedDataReference,
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                PrintedPassportData::class.java,
            )
        val chip =
            artifactStore.resolve(
                effect.chipReference,
                VerificationArtifactKind.NFC_CHIP_DATA,
                ChipDataArtifact::class.java,
            )
        val failure = listOf(printed, chip).filterIsInstance<IdvResult.Failure>().firstOrNull()
        if (failure != null) {
            emit(sink, VerificationEvent.PrintedAndChipComparisonFailed(effect.operation, failure.error))
            return
        }
        start(effect.operation, sink) { complete ->
            val cancelled = AtomicBoolean(false)
            backgroundExecutor.execute {
                if (cancelled.get()) return@execute
                val event =
                    when (
                        val result =
                            printedChipComparisonEngine.compare(
                                (printed as IdvResult.Success).value,
                                (chip as IdvResult.Success).value,
                            )
                    ) {
                        PrintedChipComparisonResult.Match -> {
                            VerificationEvent.PrintedAndChipComparisonCompleted(
                                effect.operation,
                                PrintedChipComparisonStatus.MATCH,
                            )
                        }

                        PrintedChipComparisonResult.Mismatch -> {
                            VerificationEvent.PrintedAndChipComparisonCompleted(
                                effect.operation,
                                PrintedChipComparisonStatus.MISMATCH,
                            )
                        }

                        PrintedChipComparisonResult.Inconclusive -> {
                            VerificationEvent.PrintedAndChipComparisonCompleted(
                                effect.operation,
                                PrintedChipComparisonStatus.INCONCLUSIVE,
                            )
                        }

                        is PrintedChipComparisonResult.Failed -> {
                            VerificationEvent.PrintedAndChipComparisonFailed(effect.operation, result.error)
                        }
                    }
                if (!cancelled.get()) complete(event)
            }
            CancellableOperation { cancelled.set(true) }
        }
    }

    private fun resolveDocument(reference: com.ing.offlineidv.verification.model.VerificationArtifactReference) =
        artifactStore.resolve(reference, VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact::class.java)

    private fun start(
        operation: VerificationOperationToken,
        sink: VerificationEventSink,
        block: ((VerificationEvent) -> Unit) -> CancellableOperation,
    ) {
        val slot = OperationSlot()
        operations.put(operation, slot)?.cancel()
        val handle =
            block { event ->
                if (operations.remove(operation, slot)) emit(sink, event)
            }
        slot.attach(handle)
    }

    private fun emit(
        sink: VerificationEventSink,
        event: VerificationEvent,
    ) {
        if (cleanupComplete) return
        eventDispatcher.dispatch { if (!cleanupComplete) sink.dispatch(event) }
    }

    private fun clear(requestSessionId: IdvSessionId) {
        if (requestSessionId != sessionId || cleanupComplete) return
        cleanupComplete = true
        operations.values.forEach(OperationSlot::cancel)
        operations.clear()
        scheduler.cancelAll(sessionId)
        artifactStore.clear()
        imageStore.clear(sessionId)
        permissionGateway.clear()
        closeableResources.forEach { resource -> runCatching { resource.close() } }
        backgroundExecutor.shutdownNow()
    }

    private class OperationSlot : CancellableOperation {
        private val cancelled = AtomicBoolean(false)

        @Volatile private var delegate: CancellableOperation? = null

        fun attach(delegate: CancellableOperation) {
            this.delegate = delegate
            if (cancelled.get()) delegate.cancel()
        }

        override fun cancel() {
            if (cancelled.compareAndSet(false, true)) delegate?.cancel()
        }
    }

    private fun PassiveAuthenticationObservation.toVerificationStatus(): PassiveAuthenticationStatus =
        when (this) {
            PassiveAuthenticationObservation.VALID -> PassiveAuthenticationStatus.VALID
            PassiveAuthenticationObservation.FAILED -> PassiveAuthenticationStatus.FAILED
            PassiveAuthenticationObservation.NOT_PERFORMED -> PassiveAuthenticationStatus.NOT_PERFORMED
            PassiveAuthenticationObservation.UNAVAILABLE -> PassiveAuthenticationStatus.UNAVAILABLE
            PassiveAuthenticationObservation.UNSUPPORTED -> PassiveAuthenticationStatus.UNSUPPORTED
            PassiveAuthenticationObservation.TECHNICAL_ERROR -> PassiveAuthenticationStatus.TECHNICAL_ERROR
        }

    private fun ChipAuthenticationObservation.toVerificationStatus(): ChipAuthenticationStatus =
        when (this) {
            ChipAuthenticationObservation.SUCCEEDED -> ChipAuthenticationStatus.SUCCEEDED
            ChipAuthenticationObservation.AUTHENTICATION_FAILED -> ChipAuthenticationStatus.AUTHENTICATION_FAILED
            ChipAuthenticationObservation.NOT_PERFORMED -> ChipAuthenticationStatus.NOT_PERFORMED
            ChipAuthenticationObservation.PREREQUISITE_MISSING -> ChipAuthenticationStatus.PREREQUISITE_MISSING
            ChipAuthenticationObservation.UNSUPPORTED -> ChipAuthenticationStatus.UNSUPPORTED
            ChipAuthenticationObservation.SECURE_MESSAGING_FAILED -> ChipAuthenticationStatus.SECURE_MESSAGING_FAILED
            ChipAuthenticationObservation.TECHNICAL_ERROR -> ChipAuthenticationStatus.TECHNICAL_ERROR
        }

    private fun NfcReadProgress.toVerificationPhase(): NfcReadPhase =
        when (this) {
            NfcReadProgress.TAG_DETECTED -> NfcReadPhase.CHIP_DETECTED
            NfcReadProgress.CONNECTING -> NfcReadPhase.CONNECTING
            NfcReadProgress.READING -> NfcReadPhase.SCAN_IN_PROGRESS
        }
}

/** Fully injected single-session real Android runtime. */
internal class RealAndroidVerificationRuntime(
    val sessionId: IdvSessionId,
    val cameraEngine: CameraXDocumentCaptureEngine,
    val effectHandler: RealVerificationEffectHandler,
    val orchestrator: SerializedVerificationOrchestrator,
    private val nfcTagDiscovery: AndroidNfcTagDiscovery,
    private val nfcCapabilityDetector: AndroidNfcCapabilityDetector,
) {
    fun attachPreview(surfaceProvider: Preview.SurfaceProvider?) {
        cameraEngine.attachPreview(surfaceProvider)
    }

    fun attachNfcHost(activity: Activity) {
        nfcTagDiscovery.attach(activity)
    }

    fun detachNfcHost(activity: Activity) {
        nfcTagDiscovery.detach(activity)
    }

    fun nfcCapability(): NfcCapability = nfcCapabilityDetector.detect()
}

/** Pure composition profile; document choice changes adapters and policy before the session starts. */
internal data class RealAndroidDocumentProfile(
    val policy: VerificationPolicy,
    val advertiseDetectedNfc: Boolean,
)

internal fun RealAndroidDocumentType.profile(): RealAndroidDocumentProfile =
    when (this) {
        RealAndroidDocumentType.PASSPORT_TD3 -> {
            RealAndroidDocumentProfile(
                policy = VerificationPolicy(),
                advertiseDetectedNfc = true,
            )
        }

        RealAndroidDocumentType.NETHERLANDS_RESIDENCE_PERMIT_TD1 -> {
            RealAndroidDocumentProfile(
                policy =
                    VerificationPolicy(
                        requireNfcRead = true,
                        requirePrintedChipConsistency = true,
                        requireFaceMatch = false,
                        requirePassiveAuthentication = true,
                        requireChipAuthentication = true,
                    ),
                advertiseDetectedNfc = true,
            )
        }
    }

/** Explicit real composition; it never falls back to synthetic engines. */
internal object RealAndroidVerificationFactory {
    fun create(
        context: Context,
        sessionId: IdvSessionId,
        permissionGateway: CameraPermissionGateway,
        documentType: RealAndroidDocumentType,
    ): RealAndroidVerificationRuntime {
        val profile = documentType.profile()
        val background = Executors.newSingleThreadExecutor()
        val imageStore = InMemoryCapturedImageStore(sessionId)
        val artifactStore = SessionArtifactStore(sessionId)
        val camera =
            CameraXDocumentCaptureEngine(
                context = context,
                lifecycleOwner = ProcessLifecycleOwner.get(),
                imageStore = imageStore,
                captureExecutor = background,
            )
        val quality = CameraXDocumentQualityEngine(imageStore, background)
        val imageSource =
            OcrImageSource { sourceToken ->
                when (val resolved = imageStore.resolve(DocumentCaptureArtifact(sourceToken))) {
                    is IdvResult.Failure -> {
                        resolved
                    }

                    is IdvResult.Success -> {
                        val image = resolved.value
                        image.useEncodedBytes { bytes ->
                            IdvResult.Success(OcrImage.copyOf(bytes, image.rotationDegrees))
                        }
                    }
                }
            }
        val ocr = MlKitOcrEngine(imageSource)
        val nfcCapabilityDetector = AndroidNfcCapabilityDetector(context)
        val nfcTagDiscovery =
            AndroidNfcTagDiscovery(
                context = context,
                capabilityDetector = nfcCapabilityDetector,
                diagnosticSink =
                    NfcDebugDiagnosticSink(
                        enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                    ),
            )
        val nfc = NfcSessionCoordinator(nfcCapabilityDetector, nfcTagDiscovery, background)
        val handlerThread = Handler(Looper.getMainLooper())
        val scheduler = MainThreadVerificationScheduler(handlerThread)
        val handler =
            RealVerificationEffectHandler(
                sessionId = sessionId,
                permissionGateway = permissionGateway,
                cameraPreparationEngine = camera,
                documentCaptureEngine = camera,
                documentQualityEngine = quality,
                ocrEngine = ocr,
                mrzPipeline =
                    when (documentType) {
                        RealAndroidDocumentType.PASSPORT_TD3 -> {
                            RealMrzPipeline(
                                artifactStore = artifactStore,
                                referenceDate = LocalDate.now(ZoneOffset.UTC),
                                diagnosticSink =
                                    MrzDebugDiagnosticSink(
                                        enabled =
                                            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                                    ),
                            )
                        }

                        RealAndroidDocumentType.NETHERLANDS_RESIDENCE_PERMIT_TD1 -> {
                            RealTd1MrzPipeline(
                                artifactStore = artifactStore,
                                referenceDate = LocalDate.now(ZoneOffset.UTC),
                                diagnosticSink =
                                    MrzDebugDiagnosticSink(
                                        enabled =
                                            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
                                    ),
                            )
                        }
                    },
                nfcEngine = nfc,
                chipValidationEngine = PassportChipValidationEngine,
                printedChipComparisonEngine = MrzPrintedChipComparisonEngine,
                artifactStore = artifactStore,
                imageStore = imageStore,
                scheduler = scheduler,
                eventDispatcher = RealEventDispatcher { block -> handlerThread.post(block) },
                backgroundExecutor = background,
                closeableResources = listOf(camera, ocr, nfc, nfcTagDiscovery),
            )
        val availableCapabilities = mutableSetOf(VerificationCapability.CAMERA)
        if (profile.advertiseDetectedNfc && nfcCapabilityDetector.detect() != NfcCapability.UNAVAILABLE) {
            availableCapabilities += VerificationCapability.NFC
            availableCapabilities += VerificationCapability.PASSIVE_AUTHENTICATION
            availableCapabilities += VerificationCapability.CHIP_AUTHENTICATION
        }
        val contextModel =
            VerificationContext(
                policy = profile.policy,
                capabilities = VerificationCapabilities(availableCapabilities),
            )
        return RealAndroidVerificationRuntime(
            sessionId = sessionId,
            cameraEngine = camera,
            effectHandler = handler,
            orchestrator = SerializedVerificationOrchestrator(contextModel, handler),
            nfcTagDiscovery = nfcTagDiscovery,
            nfcCapabilityDetector = nfcCapabilityDetector,
        )
    }
}
