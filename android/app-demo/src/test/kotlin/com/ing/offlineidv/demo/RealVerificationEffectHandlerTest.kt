package com.ing.offlineidv.demo

import com.ing.offlineidv.camera.AsyncCameraPreparationEngine
import com.ing.offlineidv.camera.AsyncDocumentCaptureEngine
import com.ing.offlineidv.camera.AsyncDocumentQualityEngine
import com.ing.offlineidv.camera.CameraPreparationResult
import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.camera.real.InMemoryCapturedImageStore
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.NfcReadRequest
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassportChipValidationEngine
import com.ing.offlineidv.nfc.Td3PrintedChipComparisonEngine
import com.ing.offlineidv.ocr.AsyncOcrEngine
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.verification.artifact.SessionArtifactStore
import com.ing.offlineidv.verification.model.ActiveVerificationState
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.Cancelled
import com.ing.offlineidv.verification.model.CapturingDocument
import com.ing.offlineidv.verification.model.EvaluatingDocumentQuality
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.RetryableStep
import com.ing.offlineidv.verification.model.RunningOcr
import com.ing.offlineidv.verification.model.VerificationCapabilities
import com.ing.offlineidv.verification.model.VerificationCapability
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationPolicy
import com.ing.offlineidv.verification.model.Verified
import com.ing.offlineidv.verification.orchestration.SerializedVerificationOrchestrator
import com.ing.offlineidv.verification.real.RealMrzPipeline
import com.ing.offlineidv.verification.scheduling.DeterministicVerificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

public class RealVerificationEffectHandlerTest {
    @Test
    public fun `real camera success enters reducer through effect handler`() {
        val harness = Harness()
        harness.startToCameraReady()

        harness.orchestrator.dispatch(VerificationEvent.CaptureRequested)
        harness.captureCallbacks.single()(DocumentCaptureResult.Captured(DocumentCaptureArtifact(1)))

        assertTrue(harness.orchestrator.state is EvaluatingDocumentQuality)
        assertEquals(1, harness.qualityCallbacks.size)
    }

    @Test
    public fun `OCR success enters existing MRZ parser and reducer decision`() {
        val harness = Harness(autoQuality = DocumentQualityResult.Accepted)
        harness.startCapture()

        harness.ocrCallbacks.single()(OcrEngineResult.Recognized(OcrTextArtifact(validMrz())))

        val terminal = harness.orchestrator.state as Inconclusive
        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in terminal.summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in terminal.summary.evidence)
        assertTrue(harness.handler.cleanupComplete)
    }

    @Test
    public fun `OCR failure uses reducer recovery semantics`() {
        val harness = Harness(autoQuality = DocumentQualityResult.Accepted)
        harness.startCapture()

        harness.ocrCallbacks.single()(
            OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.RECOGNITION_FAILED)),
        )

        val recovery = harness.orchestrator.state as RecoveryRequired
        assertEquals(RetryableStep.OCR, recovery.failedStep)
    }

    @Test
    public fun `quality rejection uses reducer controlled capture retry`() {
        val harness = Harness(autoQuality = DocumentQualityResult.Rejected)
        harness.startCapture()

        val recovery = harness.orchestrator.state as RecoveryRequired
        assertEquals(RetryableStep.DOCUMENT_CAPTURE, recovery.failedStep)
        assertTrue(recovery.canRetry())
    }

    @Test
    public fun `camera permission denial follows existing reducer path`() {
        val harness = Harness(permissionGranted = false)
        harness.orchestrator.dispatch(VerificationEvent.Start(harness.sessionId))

        harness.orchestrator.dispatch(VerificationEvent.PassportSelected)

        assertTrue(harness.orchestrator.state is Inconclusive)
        assertEquals(0, harness.prepareCalls)
    }

    @Test
    public fun `stale camera callback is ignored after reducer retry`() {
        val harness = Harness()
        harness.startToCameraReady()
        harness.orchestrator.dispatch(VerificationEvent.CaptureRequested)
        val first = harness.captureCallbacks.single()
        harness.fireActiveTimeout()
        harness.orchestrator.dispatch(VerificationEvent.Retry)
        val current = harness.orchestrator.state

        first(DocumentCaptureResult.Captured(DocumentCaptureArtifact(1)))

        assertSame(current, harness.orchestrator.state)
        assertTrue(harness.orchestrator.state is CapturingDocument)
        assertEquals(2, harness.captureCallbacks.size)
    }

    @Test
    public fun `stale OCR callback is ignored after reducer retry`() {
        val harness = Harness(autoQuality = DocumentQualityResult.Accepted)
        harness.startCapture()
        val first = harness.ocrCallbacks.single()
        harness.fireActiveTimeout()
        harness.orchestrator.dispatch(VerificationEvent.Retry)
        val current = harness.orchestrator.state

        first(OcrEngineResult.Recognized(OcrTextArtifact(validMrz())))

        assertSame(current, harness.orchestrator.state)
        assertTrue(harness.orchestrator.state is RunningOcr)
        assertEquals(2, harness.ocrCallbacks.size)
    }

    @Test
    public fun `late OCR callback after cleanup is ignored safely`() {
        val harness = Harness(autoQuality = DocumentQualityResult.Accepted)
        harness.startCapture()
        val late = harness.ocrCallbacks.single()

        harness.orchestrator.cancel()
        late(OcrEngineResult.Recognized(OcrTextArtifact(validMrz())))

        assertTrue(harness.orchestrator.state is Cancelled)
        assertTrue(harness.handler.cleanupComplete)
        assertTrue(harness.artifactStore.isCleared)
        assertTrue(harness.imageStore.isCleared)
    }

    @Test
    public fun `real NFC effect starts only after the reducer request`() {
        val harness = Harness(includeNfc = true)
        harness.startToAwaitingNfc()

        assertTrue(harness.orchestrator.state is AwaitingNfc)
        assertTrue(harness.nfcCallbacks.isEmpty())

        harness.orchestrator.dispatch(VerificationEvent.NfcRequested)

        assertEquals(1, harness.nfcCallbacks.size)
        assertEquals(harness.sessionId, harness.nfcRequests.single().sessionId)
    }

    @Test
    public fun `successful NFC read enters chip validation and comparison through existing reducer`() {
        val harness =
            Harness(
                includeNfc = true,
                policy = VerificationPolicy(requireFaceMatch = false),
            )
        harness.startNfcRead()

        harness.nfcCallbacks.single()(NfcReadResult.Read(validChip()))

        val terminal = harness.orchestrator.state as Verified
        assertTrue(VerificationEvidence.NFC_CHIP_READ in terminal.summary.evidence)
        assertTrue(VerificationEvidence.DG1_AVAILABLE in terminal.summary.evidence)
        assertTrue(VerificationEvidence.PRINTED_CHIP_DATA_MATCH in terminal.summary.evidence)
    }

    @Test
    public fun `NFC capability failure becomes reducer owned recovery`() {
        val harness = Harness(includeNfc = true)
        harness.startNfcRead()

        harness.nfcCallbacks.single()(NfcReadResult.Failed(IdvError.Nfc(NfcFailure.DISABLED)))

        val recovery = harness.orchestrator.state as RecoveryRequired
        assertEquals(RetryableStep.NFC, recovery.failedStep)
    }

    @Test
    public fun `NFC timeout uses existing reducer timeout recovery`() {
        val harness = Harness(includeNfc = true)
        harness.startNfcRead()

        harness.nfcCallbacks.single()(NfcReadResult.Timeout)

        val recovery = harness.orchestrator.state as RecoveryRequired
        assertEquals(RetryableStep.NFC, recovery.failedStep)
    }

    @Test
    public fun `late NFC success after cancellation is suppressed and cleared`() {
        val harness = Harness(includeNfc = true)
        harness.startNfcRead()
        val late = harness.nfcCallbacks.single()

        harness.orchestrator.cancel()
        late(NfcReadResult.Read(validChip()))

        assertTrue(harness.orchestrator.state is Cancelled)
        assertTrue(harness.handler.cleanupComplete)
        assertTrue(harness.artifactStore.isCleared)
    }

    @Test
    public fun `chip mismatch remains evidence until strict policy evaluates it`() {
        val harness =
            Harness(
                includeNfc = true,
                policy = VerificationPolicy(requireFaceMatch = false),
            )
        harness.startNfcRead()

        harness.nfcCallbacks.single()(NfcReadResult.Read(validChip("Z98Y76543")))

        val terminal = harness.orchestrator.state as Rejected
        assertTrue(VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH in terminal.summary.evidence)
    }

    @Test
    public fun `same chip mismatch is policy controlled when consistency is optional`() {
        val harness =
            Harness(
                includeNfc = true,
                policy =
                    VerificationPolicy(
                        requirePrintedChipConsistency = false,
                        requireFaceMatch = false,
                    ),
            )
        harness.startNfcRead()

        harness.nfcCallbacks.single()(NfcReadResult.Read(validChip("Z98Y76543")))

        val terminal = harness.orchestrator.state as Verified
        assertTrue(VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH in terminal.summary.evidence)
    }

    private class Harness(
        private val permissionGranted: Boolean = true,
        private val autoQuality: DocumentQualityResult? = null,
        private val includeNfc: Boolean = false,
        private val policy: VerificationPolicy = VerificationPolicy(),
    ) {
        val sessionId: IdvSessionId = (IdvSessionId.parse("atlas_real_handler") as IdvResult.Success).value
        val artifactStore = SessionArtifactStore(sessionId)
        val imageStore = InMemoryCapturedImageStore(sessionId)
        val scheduler = DeterministicVerificationScheduler()
        val captureCallbacks = mutableListOf<(DocumentCaptureResult) -> Unit>()
        val qualityCallbacks = mutableListOf<(DocumentQualityResult) -> Unit>()
        val ocrCallbacks = mutableListOf<(OcrEngineResult) -> Unit>()
        val nfcRequests = mutableListOf<NfcReadRequest>()
        val nfcCallbacks = mutableListOf<(NfcReadResult) -> Unit>()
        var prepareCalls: Int = 0
        private val permissionGateway = CameraPermissionGateway()
        private val executor = DirectExecutorService()
        val handler: RealVerificationEffectHandler
        val orchestrator: SerializedVerificationOrchestrator

        init {
            permissionGateway.attach { permissionGateway.complete(permissionGranted) }
            val preparation =
                AsyncCameraPreparationEngine { callback ->
                    prepareCalls += 1
                    callback(CameraPreparationResult.Ready)
                    CancellableOperation.NONE
                }
            val capture =
                AsyncDocumentCaptureEngine { _, callback ->
                    captureCallbacks += callback
                    CancellableOperation.NONE
                }
            val quality =
                AsyncDocumentQualityEngine { _, callback ->
                    qualityCallbacks += callback
                    autoQuality?.let(callback)
                    CancellableOperation.NONE
                }
            val ocr =
                AsyncOcrEngine { _, callback ->
                    ocrCallbacks += callback
                    CancellableOperation.NONE
                }
            handler =
                RealVerificationEffectHandler(
                    sessionId = sessionId,
                    permissionGateway = permissionGateway,
                    cameraPreparationEngine = preparation,
                    documentCaptureEngine = capture,
                    documentQualityEngine = quality,
                    ocrEngine = ocr,
                    mrzPipeline = RealMrzPipeline(artifactStore, LocalDate.of(2026, 8, 31)),
                    nfcEngine = { request, callback ->
                        nfcRequests += request
                        nfcCallbacks += callback
                        CancellableOperation.NONE
                    },
                    chipValidationEngine = PassportChipValidationEngine,
                    printedChipComparisonEngine = Td3PrintedChipComparisonEngine,
                    artifactStore = artifactStore,
                    imageStore = imageStore,
                    scheduler = scheduler,
                    eventDispatcher = RealEventDispatcher { it() },
                    backgroundExecutor = executor,
                    closeableResources = emptyList(),
                )
            orchestrator =
                SerializedVerificationOrchestrator(
                    VerificationContext(
                        policy = policy,
                        capabilities =
                            VerificationCapabilities(
                                buildSet {
                                    add(VerificationCapability.CAMERA)
                                    if (includeNfc) add(VerificationCapability.NFC)
                                },
                            ),
                    ),
                    handler,
                )
        }

        fun startToCameraReady() {
            orchestrator.dispatch(VerificationEvent.Start(sessionId))
            orchestrator.dispatch(VerificationEvent.PassportSelected)
        }

        fun startCapture() {
            startToCameraReady()
            orchestrator.dispatch(VerificationEvent.CaptureRequested)
            captureCallbacks.single()(DocumentCaptureResult.Captured(DocumentCaptureArtifact(1)))
        }

        fun startToAwaitingNfc() {
            startCapture()
            if (ocrCallbacks.isEmpty()) {
                qualityCallbacks.single()(DocumentQualityResult.Accepted)
            }
            ocrCallbacks.single()(OcrEngineResult.Recognized(OcrTextArtifact(validMrz())))
        }

        fun startNfcRead() {
            startToAwaitingNfc()
            orchestrator.dispatch(VerificationEvent.NfcRequested)
        }

        fun fireActiveTimeout() {
            val operation = (orchestrator.state as ActiveVerificationState).progress.activeOperation
            assertTrue(scheduler.fire(requireNotNull(operation)))
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown: Boolean = false

        override fun execute(command: Runnable) {
            if (!shutdown) command.run()
        }

        override fun shutdown() {
            shutdown = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = shutdown

        override fun awaitTermination(
            timeout: Long,
            unit: TimeUnit,
        ): Boolean = shutdown
    }

    private fun RecoveryRequired.canRetry(): Boolean = retryDecision.name == "RETRY_AVAILABLE"

    private companion object {
        fun validChip(documentNumber: String = "A12B34567"): ChipDataArtifact =
            ChipDataArtifact.fromDg1Fields(documentNumber, "UTO", "900101", "301231")

        fun validMrz(documentNumber: String = "A12B34567"): String {
            val birthDate = "900101"
            val expiryDate = "301231"
            val optionalData = "SYNTHETIC1<<<<"
            val documentDigit = checkDigit(documentNumber)
            val birthDigit = checkDigit(birthDate)
            val expiryDigit = checkDigit(expiryDate)
            val optionalDigit = checkDigit(optionalData)
            val composite =
                documentNumber + documentDigit + birthDate + birthDigit + expiryDate + expiryDigit + optionalData + optionalDigit
            val line1 = "P<UTO" + "TESTER<<SYNTHETIC<ATLAS".padEnd(39, '<')
            val line2 =
                documentNumber + documentDigit + "UTO" + birthDate + birthDigit + "F" + expiryDate + expiryDigit +
                    optionalData + optionalDigit + checkDigit(composite)
            return "$line1\n$line2"
        }

        fun checkDigit(value: CharSequence): Char {
            val weights = intArrayOf(7, 3, 1)
            val sum =
                value
                    .mapIndexed { index, character ->
                        val encoded =
                            when (character) {
                                in '0'..'9' -> character - '0'
                                in 'A'..'Z' -> character - 'A' + 10
                                else -> 0
                            }
                        encoded * weights[index % weights.size]
                    }.sum()
            return ('0'.code + sum % 10).toChar()
        }
    }
}
