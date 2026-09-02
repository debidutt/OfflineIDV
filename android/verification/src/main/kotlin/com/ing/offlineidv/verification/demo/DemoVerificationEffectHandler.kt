package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.camera.DocumentCaptureEngine
import com.ing.offlineidv.camera.DocumentCaptureRequest
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.camera.DocumentQualityEngine
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.core.error.CameraFailure
import com.ing.offlineidv.core.error.FaceFailure
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.MrzFailure
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.face.DocumentPortraitArtifact
import com.ing.offlineidv.face.FaceMatchEngine
import com.ing.offlineidv.face.FaceMatchResult
import com.ing.offlineidv.face.SelfieArtifact
import com.ing.offlineidv.face.SelfieCaptureEngine
import com.ing.offlineidv.face.SelfieCaptureRequest
import com.ing.offlineidv.face.SelfieCaptureResult
import com.ing.offlineidv.face.SelfieQualityEngine
import com.ing.offlineidv.face.SelfieQualityResult
import com.ing.offlineidv.nfc.ChipDataArtifact
import com.ing.offlineidv.nfc.ChipValidationEngine
import com.ing.offlineidv.nfc.ChipValidationResult
import com.ing.offlineidv.nfc.NfcReadRequest
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PassportNfcEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonEngine
import com.ing.offlineidv.nfc.PrintedChipComparisonResult
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngine
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.PassiveAuthenticationStatus
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.model.VerificationTerminalSummary
import com.ing.offlineidv.verification.orchestration.VerificationEffectHandler
import com.ing.offlineidv.verification.orchestration.VerificationEventSink
import com.ing.offlineidv.verification.scheduling.VerificationScheduler

/** Per-session fake resource cleared only in response to the reducer's cleanup effect. */
public fun interface DemoSessionResource {
    public fun clearSession()
}

/** Receives safe terminal summaries without logging or assigning an outcome. */
public fun interface DemoTerminalResultSink {
    public fun emit(summary: VerificationTerminalSummary)
}

/** Controls whether presentation prompts are advanced by the runner or by an interactive host. */
public enum class DemoPromptMode {
    /** Preserves the Milestone 4 headless runner behavior. */
    AUTOMATIC,

    /** Leaves the reducer in its awaiting state until the host dispatches the user event. */
    HOST_CONTROLLED,
}

/** Simple in-memory sink used by tests and the safe demo runner. */
public class RecordingDemoTerminalResultSink : DemoTerminalResultSink {
    private val mutableResults = mutableListOf<VerificationTerminalSummary>()

    public val results: List<VerificationTerminalSummary>
        get() = mutableResults.toList()

    override fun emit(summary: VerificationTerminalSummary) {
        mutableResults += summary
    }
}

/**
 * Platform-independent Demo Mode effect translator.
 *
 * It calls feature engines and emits observations with the exact reducer-issued operation token.
 * It never mutates state, chooses a next step, or evaluates verification policy.
 */
public class DemoVerificationEffectHandler(
    private val documentCaptureEngine: DocumentCaptureEngine,
    private val documentQualityEngine: DocumentQualityEngine,
    private val ocrEngine: OcrEngine,
    private val nfcEngine: PassportNfcEngine,
    private val chipValidationEngine: ChipValidationEngine,
    private val printedChipComparisonEngine: PrintedChipComparisonEngine,
    private val selfieCaptureEngine: SelfieCaptureEngine,
    private val selfieQualityEngine: SelfieQualityEngine,
    private val faceMatchEngine: FaceMatchEngine,
    private val mrzPipeline: DemoMrzPipeline,
    private val registry: DemoArtifactRegistry,
    private val scheduler: VerificationScheduler,
    private val terminalSink: DemoTerminalResultSink,
    private val sessionResources: List<DemoSessionResource> = emptyList(),
    private val promptMode: DemoPromptMode = DemoPromptMode.AUTOMATIC,
) : VerificationEffectHandler {
    private val mutableEffectNames = mutableListOf<String>()

    /** Safe names of effects handled so far. */
    public val effectNames: List<String>
        get() = mutableEffectNames.toList()

    /** Whether the reducer-requested cleanup effect has completed. */
    public var cleanupComplete: Boolean = false
        private set

    override fun handle(
        effect: VerificationEffect,
        eventSink: VerificationEventSink,
    ) {
        mutableEffectNames += effectName(effect)
        when (effect) {
            is VerificationEffect.InitializeSession -> {
                eventSink.dispatch(VerificationEvent.InitializationSucceeded(effect.operation))
            }

            is VerificationEffect.RequestCameraPermission -> {
                eventSink.dispatch(VerificationEvent.CameraPermissionGranted)
            }

            is VerificationEffect.PrepareCamera -> {
                eventSink.dispatch(VerificationEvent.CameraReady(effect.operation))
            }

            is VerificationEffect.CaptureDocument -> {
                captureDocument(effect, eventSink)
            }

            is VerificationEffect.EvaluateDocumentQuality -> {
                evaluateDocumentQuality(effect, eventSink)
            }

            is VerificationEffect.RunOcr -> {
                runOcr(effect, eventSink)
            }

            is VerificationEffect.ExtractAndValidateMrz -> {
                extractAndValidateMrz(effect, eventSink)
            }

            is VerificationEffect.PromptForNfc -> {
                if (promptMode == DemoPromptMode.AUTOMATIC) {
                    eventSink.dispatch(VerificationEvent.NfcRequested)
                }
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
                if (promptMode == DemoPromptMode.AUTOMATIC) {
                    eventSink.dispatch(VerificationEvent.SelfieRequested)
                }
            }

            is VerificationEffect.CaptureSelfie -> {
                captureSelfie(effect, eventSink)
            }

            is VerificationEffect.EvaluateSelfieQuality -> {
                evaluateSelfieQuality(effect, eventSink)
            }

            is VerificationEffect.CompareFaces -> {
                compareFaces(effect, eventSink)
            }

            is VerificationEffect.EvaluateVerificationPolicy -> {
                eventSink.dispatch(VerificationEvent.DecisionCompleted(effect.operation))
            }

            is VerificationEffect.ScheduleTimeout -> {
                scheduler.schedule(effect.operation, effect.duration) {
                    if (effect.operation.step == VerificationStep.SESSION) {
                        eventSink.dispatch(VerificationEvent.SessionExpired(effect.operation.sessionId))
                    } else {
                        eventSink.dispatch(VerificationEvent.SessionTimedOut(effect.operation))
                    }
                }
            }

            is VerificationEffect.CancelTimeout -> {
                scheduler.cancel(effect.operation)
            }

            is VerificationEffect.ClearSensitiveSessionData -> {
                if (!cleanupComplete) {
                    registry.clear()
                    scheduler.cancelAll(effect.sessionId)
                    sessionResources.forEach(DemoSessionResource::clearSession)
                    cleanupComplete = true
                }
            }

            is VerificationEffect.EmitTerminalResult -> {
                terminalSink.emit(effect.summary)
            }
        }
    }

    private fun captureDocument(
        effect: VerificationEffect.CaptureDocument,
        eventSink: VerificationEventSink,
    ) {
        val result =
            safeCall(
                fallback = DocumentCaptureResult.Failed(IdvError.Camera(CameraFailure.CAPTURE_FAILED)),
            ) {
                documentCaptureEngine.capture(DocumentCaptureRequest(effect.operation.sessionId))
            }
        when (result) {
            is DocumentCaptureResult.Captured -> {
                val registered = registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, result.artifact)
                if (registered is IdvResult.Success) {
                    eventSink.dispatch(VerificationEvent.DocumentCaptured(effect.operation, registered.value))
                } else {
                    eventSink.dispatch(VerificationEvent.CaptureFailed(effect.operation, (registered as IdvResult.Failure).error))
                }
            }

            is DocumentCaptureResult.Failed -> {
                eventSink.dispatch(VerificationEvent.CaptureFailed(effect.operation, result.error))
            }
        }
    }

    private fun evaluateDocumentQuality(
        effect: VerificationEffect.EvaluateDocumentQuality,
        eventSink: VerificationEventSink,
    ) {
        val artifact =
            resolve(
                effect.documentReference,
                VerificationArtifactKind.DOCUMENT_CAPTURE,
                DocumentCaptureArtifact::class.java,
            )
        if (artifact is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.DocumentQualityFailed(effect.operation, artifact.error))
            return
        }
        val result =
            safeCall(
                fallback = DocumentQualityResult.Failed(IdvError.Camera(CameraFailure.QUALITY_REJECTED)),
            ) {
                documentQualityEngine.evaluate((artifact as IdvResult.Success).value)
            }
        when (result) {
            DocumentQualityResult.Accepted -> {
                eventSink.dispatch(VerificationEvent.CaptureQualityAccepted(effect.operation))
            }

            DocumentQualityResult.Rejected -> {
                eventSink.dispatch(VerificationEvent.CaptureQualityRejected(effect.operation))
            }

            is DocumentQualityResult.Failed -> {
                eventSink.dispatch(VerificationEvent.DocumentQualityFailed(effect.operation, result.error))
            }
        }
    }

    private fun runOcr(
        effect: VerificationEffect.RunOcr,
        eventSink: VerificationEventSink,
    ) {
        val document =
            resolve(
                effect.documentReference,
                VerificationArtifactKind.DOCUMENT_CAPTURE,
                DocumentCaptureArtifact::class.java,
            )
        if (document is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.OcrFailed(effect.operation, document.error))
            return
        }
        val input = OcrDocumentInput((document as IdvResult.Success).value.sourceToken)
        val result =
            safeCall(
                fallback = OcrEngineResult.Failed(IdvError.Ocr(OcrFailure.RECOGNITION_FAILED)),
            ) {
                ocrEngine.recognize(input)
            }
        when (result) {
            is OcrEngineResult.Recognized -> {
                val registered = registry.register(VerificationArtifactKind.OCR_RESULT, result.artifact)
                if (registered is IdvResult.Success) {
                    eventSink.dispatch(VerificationEvent.OcrSucceeded(effect.operation, registered.value))
                } else {
                    eventSink.dispatch(VerificationEvent.OcrFailed(effect.operation, (registered as IdvResult.Failure).error))
                }
            }

            is OcrEngineResult.Failed -> {
                eventSink.dispatch(VerificationEvent.OcrFailed(effect.operation, result.error))
            }
        }
    }

    private fun extractAndValidateMrz(
        effect: VerificationEffect.ExtractAndValidateMrz,
        eventSink: VerificationEventSink,
    ) {
        val result =
            safeCall<IdvResult<DemoMrzPipelineResult>>(
                fallback = IdvResult.Failure(IdvError.Mrz(MrzFailure.MALFORMED)),
            ) {
                mrzPipeline.process(effect.ocrReference)
            }
        if (result is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.MrzExtractionFailed(effect.operation, result.error))
            return
        }
        val pipeline = (result as IdvResult.Success).value
        eventSink.dispatch(VerificationEvent.MrzExtractionSucceeded(effect.operation))
        eventSink.dispatch(
            VerificationEvent.MrzValidationCompleted(
                operation = effect.operation,
                summary = pipeline.summary,
                printedDataReference = pipeline.printedDataReference,
                accessKeyReference = pipeline.accessKeyReference,
            ),
        )
    }

    private fun readNfc(
        effect: VerificationEffect.StartNfcRead,
        eventSink: VerificationEventSink,
    ) {
        val key =
            resolve(
                effect.accessKeyReference,
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                PassportAccessKey::class.java,
            )
        if (key is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.NfcReadFailed(effect.operation, key.error))
            return
        }
        val result =
            safeCall(
                fallback = NfcReadResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED)),
            ) {
                nfcEngine.read(NfcReadRequest(effect.operation.sessionId, (key as IdvResult.Success).value))
            }
        when (result) {
            is NfcReadResult.Read -> {
                val registered = registry.register(VerificationArtifactKind.NFC_CHIP_DATA, result.artifact)
                if (registered is IdvResult.Success) {
                    eventSink.dispatch(VerificationEvent.NfcReadSucceeded(effect.operation, registered.value))
                } else {
                    eventSink.dispatch(VerificationEvent.NfcReadFailed(effect.operation, (registered as IdvResult.Failure).error))
                }
            }

            NfcReadResult.Timeout -> {
                eventSink.dispatch(VerificationEvent.SessionTimedOut(effect.operation))
            }

            NfcReadResult.Unavailable -> {
                eventSink.dispatch(VerificationEvent.NfcReadFailed(effect.operation, IdvError.Nfc(NfcFailure.UNAVAILABLE)))
            }

            is NfcReadResult.Failed -> {
                eventSink.dispatch(VerificationEvent.NfcReadFailed(effect.operation, result.error))
            }
        }
    }

    private fun validateChip(
        effect: VerificationEffect.ValidateChipData,
        eventSink: VerificationEventSink,
    ) {
        val chip =
            resolve(
                effect.chipReference,
                VerificationArtifactKind.NFC_CHIP_DATA,
                ChipDataArtifact::class.java,
            )
        if (chip is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.ChipValidationFailed(effect.operation, chip.error))
            return
        }
        val result =
            safeCall(
                fallback = ChipValidationResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED)),
            ) {
                chipValidationEngine.validate((chip as IdvResult.Success).value)
            }
        when (result) {
            is ChipValidationResult.Failed -> {
                eventSink.dispatch(VerificationEvent.ChipValidationFailed(effect.operation, result.error))
            }

            is ChipValidationResult.Validated -> {
                val observation = result.observation
                val portraitReference =
                    observation.portrait?.let { portrait ->
                        val facePortrait = portrait.useValue(::DocumentPortraitArtifact)
                        registry.register(VerificationArtifactKind.CHIP_PORTRAIT, facePortrait)
                    }
                if (portraitReference is IdvResult.Failure) {
                    eventSink.dispatch(VerificationEvent.ChipValidationFailed(effect.operation, portraitReference.error))
                    return
                }
                eventSink.dispatch(
                    VerificationEvent.ChipValidationCompleted(
                        effect.operation,
                        ChipValidationSummary(
                            dg1Available = observation.dg1Available,
                            dg2Available = observation.dg2Available,
                            passiveAuthentication = observation.passiveAuthentication.toVerificationStatus(),
                            portraitReference = (portraitReference as? IdvResult.Success)?.value,
                        ),
                    ),
                )
            }
        }
    }

    private fun comparePrintedChip(
        effect: VerificationEffect.ComparePrintedAndChipData,
        eventSink: VerificationEventSink,
    ) {
        val printed =
            resolve(
                effect.printedDataReference,
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                PrintedPassportData::class.java,
            )
        val chip =
            resolve(
                effect.chipReference,
                VerificationArtifactKind.NFC_CHIP_DATA,
                ChipDataArtifact::class.java,
            )
        val failure = listOf(printed, chip).filterIsInstance<IdvResult.Failure>().firstOrNull()
        if (failure != null) {
            eventSink.dispatch(VerificationEvent.PrintedAndChipComparisonFailed(effect.operation, failure.error))
            return
        }
        val result =
            safeCall(
                fallback = PrintedChipComparisonResult.Failed(IdvError.Nfc(NfcFailure.READ_FAILED)),
            ) {
                printedChipComparisonEngine.compare(
                    (printed as IdvResult.Success).value,
                    (chip as IdvResult.Success).value,
                )
            }
        when (result) {
            PrintedChipComparisonResult.Match -> {
                completePrintedChip(effect, PrintedChipComparisonStatus.MATCH, eventSink)
            }

            PrintedChipComparisonResult.Mismatch -> {
                completePrintedChip(effect, PrintedChipComparisonStatus.MISMATCH, eventSink)
            }

            PrintedChipComparisonResult.Inconclusive -> {
                completePrintedChip(effect, PrintedChipComparisonStatus.INCONCLUSIVE, eventSink)
            }

            is PrintedChipComparisonResult.Failed -> {
                eventSink.dispatch(VerificationEvent.PrintedAndChipComparisonFailed(effect.operation, result.error))
            }
        }
    }

    private fun completePrintedChip(
        effect: VerificationEffect.ComparePrintedAndChipData,
        status: PrintedChipComparisonStatus,
        eventSink: VerificationEventSink,
    ) {
        eventSink.dispatch(VerificationEvent.PrintedAndChipComparisonCompleted(effect.operation, status))
    }

    private fun captureSelfie(
        effect: VerificationEffect.CaptureSelfie,
        eventSink: VerificationEventSink,
    ) {
        val result =
            safeCall(
                fallback = SelfieCaptureResult.Failed(IdvError.Face(FaceFailure.NO_FACE)),
            ) {
                selfieCaptureEngine.capture(SelfieCaptureRequest(effect.operation.sessionId))
            }
        when (result) {
            is SelfieCaptureResult.Captured -> {
                val registered = registry.register(VerificationArtifactKind.SELFIE_CAPTURE, result.artifact)
                if (registered is IdvResult.Success) {
                    eventSink.dispatch(VerificationEvent.SelfieCaptured(effect.operation, registered.value))
                } else {
                    eventSink.dispatch(VerificationEvent.SelfieCaptureFailed(effect.operation, (registered as IdvResult.Failure).error))
                }
            }

            is SelfieCaptureResult.Failed -> {
                eventSink.dispatch(VerificationEvent.SelfieCaptureFailed(effect.operation, result.error))
            }
        }
    }

    private fun evaluateSelfieQuality(
        effect: VerificationEffect.EvaluateSelfieQuality,
        eventSink: VerificationEventSink,
    ) {
        val selfie =
            resolve(
                effect.selfieReference,
                VerificationArtifactKind.SELFIE_CAPTURE,
                SelfieArtifact::class.java,
            )
        if (selfie is IdvResult.Failure) {
            eventSink.dispatch(VerificationEvent.SelfieQualityFailed(effect.operation, selfie.error))
            return
        }
        val result =
            safeCall(
                fallback = SelfieQualityResult.Failed(IdvError.Face(FaceFailure.QUALITY_REJECTED)),
            ) {
                selfieQualityEngine.evaluate((selfie as IdvResult.Success).value)
            }
        when (result) {
            SelfieQualityResult.Accepted -> {
                eventSink.dispatch(VerificationEvent.SelfieQualityAccepted(effect.operation))
            }

            SelfieQualityResult.Rejected -> {
                eventSink.dispatch(VerificationEvent.SelfieQualityRejected(effect.operation))
            }

            is SelfieQualityResult.Failed -> {
                eventSink.dispatch(VerificationEvent.SelfieQualityFailed(effect.operation, result.error))
            }
        }
    }

    private fun compareFaces(
        effect: VerificationEffect.CompareFaces,
        eventSink: VerificationEventSink,
    ) {
        val portrait =
            resolve(
                effect.portraitReference,
                VerificationArtifactKind.CHIP_PORTRAIT,
                DocumentPortraitArtifact::class.java,
            )
        val selfie =
            resolve(
                effect.selfieReference,
                VerificationArtifactKind.SELFIE_CAPTURE,
                SelfieArtifact::class.java,
            )
        val failure = listOf(portrait, selfie).filterIsInstance<IdvResult.Failure>().firstOrNull()
        if (failure != null) {
            eventSink.dispatch(VerificationEvent.FaceComparisonFailed(effect.operation, failure.error))
            return
        }
        val result =
            safeCall(
                fallback = FaceMatchResult.Failed(IdvError.Face(FaceFailure.COMPARISON_FAILED)),
            ) {
                faceMatchEngine.compare(
                    (portrait as IdvResult.Success).value,
                    (selfie as IdvResult.Success).value,
                )
            }
        when (result) {
            FaceMatchResult.Accepted -> {
                completeFace(effect, FaceComparisonStatus.ACCEPTED, eventSink)
            }

            FaceMatchResult.Rejected -> {
                completeFace(effect, FaceComparisonStatus.REJECTED, eventSink)
            }

            FaceMatchResult.Inconclusive -> {
                completeFace(effect, FaceComparisonStatus.INCONCLUSIVE, eventSink)
            }

            is FaceMatchResult.Failed -> {
                eventSink.dispatch(VerificationEvent.FaceComparisonFailed(effect.operation, result.error))
            }
        }
    }

    private fun completeFace(
        effect: VerificationEffect.CompareFaces,
        status: FaceComparisonStatus,
        eventSink: VerificationEventSink,
    ) {
        eventSink.dispatch(VerificationEvent.FaceComparisonCompleted(effect.operation, status))
    }

    private fun <T : Any> resolve(
        reference: com.ing.offlineidv.verification.model.VerificationArtifactReference,
        kind: VerificationArtifactKind,
        type: Class<T>,
    ): IdvResult<T> = registry.resolve(reference, kind, type)

    private inline fun <T> safeCall(
        fallback: T,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (_: RuntimeException) {
            fallback
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

    private fun effectName(effect: VerificationEffect): String =
        when (effect) {
            is VerificationEffect.InitializeSession -> "InitializeSession"
            is VerificationEffect.RequestCameraPermission -> "RequestCameraPermission"
            is VerificationEffect.PrepareCamera -> "PrepareCamera"
            is VerificationEffect.CaptureDocument -> "CaptureDocument"
            is VerificationEffect.EvaluateDocumentQuality -> "EvaluateDocumentQuality"
            is VerificationEffect.RunOcr -> "RunOcr"
            is VerificationEffect.ExtractAndValidateMrz -> "ExtractAndValidateMrz"
            is VerificationEffect.PromptForNfc -> "PromptForNfc"
            is VerificationEffect.StartNfcRead -> "StartNfcRead"
            is VerificationEffect.ValidateChipData -> "ValidateChipData"
            is VerificationEffect.ComparePrintedAndChipData -> "ComparePrintedAndChipData"
            is VerificationEffect.PromptForSelfie -> "PromptForSelfie"
            is VerificationEffect.CaptureSelfie -> "CaptureSelfie"
            is VerificationEffect.EvaluateSelfieQuality -> "EvaluateSelfieQuality"
            is VerificationEffect.CompareFaces -> "CompareFaces"
            is VerificationEffect.EvaluateVerificationPolicy -> "EvaluateVerificationPolicy"
            is VerificationEffect.ScheduleTimeout -> "ScheduleTimeout"
            is VerificationEffect.CancelTimeout -> "CancelTimeout"
            is VerificationEffect.ClearSensitiveSessionData -> "ClearSensitiveSessionData"
            is VerificationEffect.EmitTerminalResult -> "EmitTerminalResult"
        }
}
