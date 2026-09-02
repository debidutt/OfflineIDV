package com.ing.offlineidv.verification

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.verification.model.ActiveVerificationState
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CameraPermissionRequired
import com.ing.offlineidv.verification.model.CameraReady
import com.ing.offlineidv.verification.model.Cancelled
import com.ing.offlineidv.verification.model.CapturingDocument
import com.ing.offlineidv.verification.model.CapturingSelfie
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.ComparingFaces
import com.ing.offlineidv.verification.model.ComparingPrintedAndChipData
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.EvaluatingDocumentQuality
import com.ing.offlineidv.verification.model.EvaluatingSelfie
import com.ing.offlineidv.verification.model.Expired
import com.ing.offlineidv.verification.model.ExtractingMrz
import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.Idle
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.Initializing
import com.ing.offlineidv.verification.model.MakingDecision
import com.ing.offlineidv.verification.model.PassiveAuthenticationStatus
import com.ing.offlineidv.verification.model.PreparingCamera
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.ReadingNfc
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.RetryDecision
import com.ing.offlineidv.verification.model.RetryReason
import com.ing.offlineidv.verification.model.RetryableStep
import com.ing.offlineidv.verification.model.RunningOcr
import com.ing.offlineidv.verification.model.TechnicalFailure
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.TransitionResult
import com.ing.offlineidv.verification.model.ValidatingChipData
import com.ing.offlineidv.verification.model.ValidatingMrz
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOperationToken
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationProgress
import com.ing.offlineidv.verification.model.VerificationState
import com.ing.offlineidv.verification.model.VerificationStep
import com.ing.offlineidv.verification.model.VerificationTerminalReason
import com.ing.offlineidv.verification.model.VerificationTerminalSummary
import com.ing.offlineidv.verification.model.Verified
import com.ing.offlineidv.verification.policy.VerificationPolicyEvaluator

/** Deterministic platform-independent verification transition contract. */
public fun interface VerificationStateMachine {
    /** Returns the next immutable state and external intents without performing work or reading time. */
    public fun transition(
        currentState: VerificationState,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult
}

/** Complete pure reducer for the Milestone 3 passport verification journey. */
public object DefaultVerificationStateMachine : VerificationStateMachine {
    override fun transition(
        currentState: VerificationState,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult {
        if (currentState == Idle) return transitionFromIdle(event, context)
        if (currentState is TerminalState) return transitionFromTerminal(currentState, event)

        val activeState = currentState as ActiveVerificationState
        when (event) {
            VerificationEvent.Cancel -> {
                return terminal(
                    activeState.progress,
                    VerificationOutcome.CANCELLED,
                    VerificationTerminalReason.HOST_CANCELLED,
                )
            }

            is VerificationEvent.SessionExpired -> {
                return if (event.sessionId == activeState.progress.sessionId) {
                    terminal(
                        activeState.progress,
                        VerificationOutcome.EXPIRED,
                        VerificationTerminalReason.SESSION_EXPIRED,
                    )
                } else {
                    ignored(currentState, TransitionDisposition.IGNORED_STALE_EVENT)
                }
            }

            is VerificationEvent.Start -> {
                return ignored(currentState, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            VerificationEvent.Reset -> {
                return ignored(currentState, TransitionDisposition.IGNORED_ILLEGAL_EVENT)
            }

            else -> {
                Unit
            }
        }

        operationFrom(event)?.let { operation ->
            operationDisposition(activeState.progress, operation)?.let { disposition ->
                return ignored(currentState, disposition)
            }
        }

        if (event is VerificationEvent.SessionTimedOut) {
            return handleStepTimeout(activeState, event.operation, context)
        }

        return when (currentState) {
            is Initializing -> transitionFromInitializing(currentState, event, context)
            is DocumentSelection -> transitionFromDocumentSelection(currentState, event, context)
            is CameraPermissionRequired -> transitionFromCameraPermission(currentState, event, context)
            is PreparingCamera -> transitionFromPreparingCamera(currentState, event)
            is CameraReady -> transitionFromCameraReady(currentState, event, context)
            is CapturingDocument -> transitionFromCapturingDocument(currentState, event, context)
            is EvaluatingDocumentQuality -> transitionFromDocumentQuality(currentState, event, context)
            is RunningOcr -> transitionFromOcr(currentState, event, context)
            is ExtractingMrz -> transitionFromMrzExtraction(currentState, event, context)
            is ValidatingMrz -> transitionFromMrzValidation(currentState, event, context)
            is AwaitingNfc -> transitionFromAwaitingNfc(currentState, event, context)
            is ReadingNfc -> transitionFromReadingNfc(currentState, event, context)
            is ValidatingChipData -> transitionFromChipValidation(currentState, event, context)
            is ComparingPrintedAndChipData -> transitionFromPrintedChipComparison(currentState, event, context)
            is AwaitingSelfie -> transitionFromAwaitingSelfie(currentState, event, context)
            is CapturingSelfie -> transitionFromCapturingSelfie(currentState, event, context)
            is EvaluatingSelfie -> transitionFromSelfieQuality(currentState, event, context)
            is ComparingFaces -> transitionFromFaceComparison(currentState, event, context)
            is MakingDecision -> transitionFromDecision(currentState, event, context)
            is RecoveryRequired -> transitionFromRecovery(currentState, event, context)
        }
    }

    private fun transitionFromIdle(
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.Start -> {
                val operation = VerificationProgress(event.sessionId).beginOperation(VerificationStep.INITIALIZATION)
                TransitionResult(
                    Initializing(operation.progress),
                    listOf(
                        VerificationEffect.InitializeSession(event.sessionId, operation.token),
                        schedule(operation.token, context),
                    ),
                )
            }

            VerificationEvent.Reset -> {
                ignored(Idle, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            else -> {
                ignored(Idle, TransitionDisposition.IGNORED_ILLEGAL_EVENT)
            }
        }

    private fun transitionFromTerminal(
        state: TerminalState,
        event: VerificationEvent,
    ): TransitionResult =
        if (event == VerificationEvent.Reset) {
            TransitionResult(Idle)
        } else {
            ignored(state, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
        }

    private fun transitionFromInitializing(
        state: Initializing,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.InitializationSucceeded -> {
                val progress = state.progress.completeOperation().markSessionExpiryScheduled()
                TransitionResult(
                    DocumentSelection(progress),
                    listOf(
                        VerificationEffect.CancelTimeout(event.operation),
                        VerificationEffect.ScheduleTimeout(
                            progress.sessionExpiryToken,
                            context.timeoutPolicy.session,
                        ),
                    ),
                )
            }

            is VerificationEvent.InitializationFailed -> {
                terminal(
                    state.progress,
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.INITIALIZATION_FAILED,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromDocumentSelection(
        state: DocumentSelection,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.PassportSelected,
            VerificationEvent.CameraPermissionRequired,
            -> {
                if (context.capabilities.isAvailable(com.ing.offlineidv.verification.model.VerificationCapability.CAMERA)) {
                    TransitionResult(
                        CameraPermissionRequired(state.progress),
                        listOf(VerificationEffect.RequestCameraPermission(state.progress.sessionId)),
                    )
                } else {
                    terminal(
                        state.progress.withEvidence(
                            setOf(
                                VerificationEvidence.CAPABILITY_UNAVAILABLE,
                                VerificationEvidence.REQUIRED_STEP_SKIPPED,
                            ),
                        ),
                        VerificationOutcome.INCONCLUSIVE,
                        VerificationTerminalReason.REQUIRED_CAPABILITY_UNAVAILABLE,
                    )
                }
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromCameraPermission(
        state: CameraPermissionRequired,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.CameraPermissionGranted -> {
                beginOperation(
                    state.progress,
                    VerificationStep.CAMERA_PREPARATION,
                    ::PreparingCamera,
                    { VerificationEffect.PrepareCamera(it) },
                    context,
                )
            }

            VerificationEvent.CameraPermissionDenied -> {
                terminal(
                    state.progress.withEvidence(
                        setOf(
                            VerificationEvidence.CAPABILITY_UNAVAILABLE,
                            VerificationEvidence.REQUIRED_STEP_SKIPPED,
                        ),
                    ),
                    VerificationOutcome.INCONCLUSIVE,
                    VerificationTerminalReason.CAMERA_PERMISSION_DENIED,
                )
            }

            VerificationEvent.CameraPermissionRequired -> {
                ignored(state, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            VerificationEvent.Back -> {
                TransitionResult(DocumentSelection(state.progress))
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromPreparingCamera(
        state: PreparingCamera,
        event: VerificationEvent,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.CameraReady -> {
                TransitionResult(
                    CameraReady(state.progress.completeOperation()),
                    listOf(VerificationEffect.CancelTimeout(event.operation)),
                )
            }

            is VerificationEvent.CameraPreparationFailed -> {
                terminal(
                    state.progress.completeOperation(),
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.TECHNICAL_FAILURE,
                    listOf(VerificationEffect.CancelTimeout(event.operation)),
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromCameraReady(
        state: CameraReady,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.CaptureRequested -> startDocumentCapture(state.progress, context)
            VerificationEvent.Back -> TransitionResult(DocumentSelection(state.progress))
            else -> illegal(state)
        }

    private fun transitionFromCapturingDocument(
        state: CapturingDocument,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.DocumentCaptured -> {
                if (event.documentReference.kind == VerificationArtifactKind.DOCUMENT_CAPTURE) {
                    completeAndBegin(
                        state.progress.withArtifact(event.documentReference),
                        event.operation,
                        VerificationStep.DOCUMENT_QUALITY,
                        ::EvaluatingDocumentQuality,
                        { operation ->
                            VerificationEffect.EvaluateDocumentQuality(operation, event.documentReference)
                        },
                        context,
                    )
                } else {
                    illegal(state)
                }
            }

            is VerificationEvent.CaptureFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.DOCUMENT_CAPTURE,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromDocumentQuality(
        state: EvaluatingDocumentQuality,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.CaptureQualityAccepted -> {
                startOcrAfter(state.progress, event.operation, context)
            }

            is VerificationEvent.CaptureQualityRejected -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.DOCUMENT_CAPTURE,
                    RetryReason.QUALITY_REJECTED,
                    null,
                    context,
                )
            }

            is VerificationEvent.DocumentQualityFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.DOCUMENT_CAPTURE,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromOcr(
        state: RunningOcr,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.OcrStarted -> {
                ignored(state, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            is VerificationEvent.OcrSucceeded -> {
                if (event.ocrReference.kind == VerificationArtifactKind.OCR_RESULT) {
                    completeAndBegin(
                        state.progress.withArtifact(event.ocrReference),
                        event.operation,
                        VerificationStep.MRZ,
                        ::ExtractingMrz,
                        { operation ->
                            VerificationEffect.ExtractAndValidateMrz(operation, event.ocrReference)
                        },
                        context,
                    )
                } else {
                    illegal(state)
                }
            }

            is VerificationEvent.OcrFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.OCR,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromMrzExtraction(
        state: ExtractingMrz,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.MrzExtractionSucceeded -> {
                TransitionResult(ValidatingMrz(state.progress))
            }

            is VerificationEvent.MrzExtractionFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.OCR,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromMrzValidation(
        state: ValidatingMrz,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult {
        return when (event) {
            is VerificationEvent.MrzValidationCompleted -> {
                if (
                    event.printedDataReference.kind != VerificationArtifactKind.MRZ_PRINTED_DATA ||
                    event.accessKeyReference.kind != VerificationArtifactKind.MRZ_ACCESS_KEY
                ) {
                    return illegal(state)
                }
                var progress =
                    state.progress
                        .completeOperation()
                        .withArtifact(event.printedDataReference)
                        .withArtifact(event.accessKeyReference)
                        .withEvidence(event.summary.evidence)
                val effects = mutableListOf<VerificationEffect>(VerificationEffect.CancelTimeout(event.operation))
                if (VerificationPolicyEvaluator.mrzRequiresEarlyDecision(context.policy, progress.evidence)) {
                    return beginDecision(progress, context, effects)
                }
                if (
                    !context.capabilities.isAvailable(
                        com.ing.offlineidv.verification.model.VerificationCapability.NFC,
                    )
                ) {
                    val unavailable = mutableSetOf(VerificationEvidence.CAPABILITY_UNAVAILABLE)
                    if (context.policy.requireNfcRead) unavailable += VerificationEvidence.REQUIRED_STEP_SKIPPED
                    progress = progress.withEvidence(unavailable)
                    return if (context.policy.requireNfcRead) {
                        beginDecision(progress, context, effects)
                    } else {
                        routeAfterNfc(progress, context, effects)
                    }
                }
                effects += VerificationEffect.PromptForNfc(progress.sessionId)
                TransitionResult(AwaitingNfc(progress), effects)
            }

            is VerificationEvent.MrzValidationFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.OCR,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }
    }

    private fun transitionFromAwaitingNfc(
        state: AwaitingNfc,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.NfcRequested -> {
                startNfc(state.progress, context)
            }

            VerificationEvent.Back -> {
                if (context.policy.requireNfcRead) {
                    illegal(state)
                } else {
                    routeAfterNfc(state.progress, context)
                }
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromReadingNfc(
        state: ReadingNfc,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.NfcStarted -> {
                ignored(state, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            is VerificationEvent.NfcReadSucceeded -> {
                if (event.chipReference.kind == VerificationArtifactKind.NFC_CHIP_DATA) {
                    completeAndBegin(
                        state.progress
                            .withArtifact(event.chipReference)
                            .withEvidence(setOf(VerificationEvidence.NFC_CHIP_READ)),
                        event.operation,
                        VerificationStep.CHIP_VALIDATION,
                        ::ValidatingChipData,
                        { operation -> VerificationEffect.ValidateChipData(operation, event.chipReference) },
                        context,
                    )
                } else {
                    illegal(state)
                }
            }

            is VerificationEvent.NfcReadFailed -> {
                recover(
                    state.progress.withEvidence(setOf(VerificationEvidence.NFC_READ_FAILED)),
                    event.operation,
                    RetryableStep.NFC,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromChipValidation(
        state: ValidatingChipData,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.ChipValidationCompleted -> {
                completeChipValidation(state, event, context)
            }

            is VerificationEvent.ChipValidationFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.NFC,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun completeChipValidation(
        state: ValidatingChipData,
        event: VerificationEvent.ChipValidationCompleted,
        context: VerificationContext,
    ): TransitionResult {
        var progress = state.progress.completeOperation()
        val evidence = mutableSetOf<VerificationEvidence>()
        if (event.summary.dg1Available) evidence += VerificationEvidence.DG1_AVAILABLE
        if (event.summary.dg2Available) evidence += VerificationEvidence.DG2_AVAILABLE
        evidence += passiveAuthenticationEvidence(event.summary)
        progress = progress.withEvidence(evidence)
        event.summary.portraitReference?.let { progress = progress.withArtifact(it) }
        val effects = mutableListOf<VerificationEffect>(VerificationEffect.CancelTimeout(event.operation))

        if (!event.summary.dg1Available) {
            if (context.policy.requirePrintedChipConsistency) {
                progress = progress.withEvidence(setOf(VerificationEvidence.REQUIRED_STEP_SKIPPED))
                return beginDecision(progress, context, effects)
            }
            return routeAfterNfc(progress, context, effects)
        }

        val printed = progress.artifacts.reference(VerificationArtifactKind.MRZ_PRINTED_DATA)
        val chip = progress.artifacts.reference(VerificationArtifactKind.NFC_CHIP_DATA)
        if (printed == null || chip == null) {
            return terminal(
                progress,
                VerificationOutcome.TECHNICAL_FAILURE,
                VerificationTerminalReason.TECHNICAL_FAILURE,
                effects,
            )
        }
        return beginOperation(
            progress,
            VerificationStep.PRINTED_CHIP_COMPARISON,
            ::ComparingPrintedAndChipData,
            { operation -> VerificationEffect.ComparePrintedAndChipData(operation, printed, chip) },
            context,
            effects,
        )
    }

    private fun transitionFromPrintedChipComparison(
        state: ComparingPrintedAndChipData,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.PrintedAndChipComparisonCompleted -> {
                var progress = state.progress.completeOperation()
                progress =
                    when (event.status) {
                        PrintedChipComparisonStatus.MATCH -> {
                            progress.withEvidence(setOf(VerificationEvidence.PRINTED_CHIP_DATA_MATCH))
                        }

                        PrintedChipComparisonStatus.MISMATCH -> {
                            progress.withEvidence(setOf(VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH))
                        }

                        PrintedChipComparisonStatus.INCONCLUSIVE -> {
                            if (context.policy.requirePrintedChipConsistency) {
                                progress.withEvidence(setOf(VerificationEvidence.REQUIRED_STEP_SKIPPED))
                            } else {
                                progress
                            }
                        }
                    }
                val effects = listOf<VerificationEffect>(VerificationEffect.CancelTimeout(event.operation))
                if (
                    context.policy.requirePrintedChipConsistency &&
                    event.status != PrintedChipComparisonStatus.MATCH
                ) {
                    beginDecision(progress, context, effects)
                } else {
                    routeAfterNfc(progress, context, effects)
                }
            }

            is VerificationEvent.PrintedAndChipComparisonFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.NFC,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromAwaitingSelfie(
        state: AwaitingSelfie,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.SelfieRequested -> {
                startSelfie(state.progress, context)
            }

            VerificationEvent.Back -> {
                if (context.policy.requireFaceMatch) illegal(state) else beginDecision(state.progress, context)
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromCapturingSelfie(
        state: CapturingSelfie,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.SelfieCaptured -> {
                if (event.selfieReference.kind == VerificationArtifactKind.SELFIE_CAPTURE) {
                    completeAndBegin(
                        state.progress.withArtifact(event.selfieReference),
                        event.operation,
                        VerificationStep.SELFIE_QUALITY,
                        ::EvaluatingSelfie,
                        { operation -> VerificationEffect.EvaluateSelfieQuality(operation, event.selfieReference) },
                        context,
                    )
                } else {
                    illegal(state)
                }
            }

            is VerificationEvent.SelfieCaptureFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.SELFIE,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromSelfieQuality(
        state: EvaluatingSelfie,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.SelfieQualityAccepted -> {
                val progress =
                    state.progress
                        .completeOperation()
                        .withEvidence(setOf(VerificationEvidence.SELFIE_QUALITY_ACCEPTED))
                val portrait = progress.artifacts.reference(VerificationArtifactKind.CHIP_PORTRAIT)
                val selfie = progress.artifacts.reference(VerificationArtifactKind.SELFIE_CAPTURE)
                val effects = listOf<VerificationEffect>(VerificationEffect.CancelTimeout(event.operation))
                if (portrait == null || selfie == null) {
                    if (context.policy.requireFaceMatch) {
                        beginDecision(
                            progress.withEvidence(setOf(VerificationEvidence.REQUIRED_STEP_SKIPPED)),
                            context,
                            effects,
                        )
                    } else {
                        beginDecision(progress, context, effects)
                    }
                } else {
                    beginOperation(
                        progress,
                        VerificationStep.FACE_COMPARISON,
                        ::ComparingFaces,
                        { operation -> VerificationEffect.CompareFaces(operation, portrait, selfie) },
                        context,
                        effects,
                    )
                }
            }

            is VerificationEvent.SelfieQualityRejected -> {
                recover(
                    state.progress.withEvidence(setOf(VerificationEvidence.SELFIE_QUALITY_REJECTED)),
                    event.operation,
                    RetryableStep.SELFIE,
                    RetryReason.QUALITY_REJECTED,
                    null,
                    context,
                )
            }

            is VerificationEvent.SelfieQualityFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.SELFIE,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromFaceComparison(
        state: ComparingFaces,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.FaceComparisonCompleted -> {
                val evidence =
                    when (event.status) {
                        FaceComparisonStatus.ACCEPTED -> VerificationEvidence.FACE_MATCH_ACCEPTED
                        FaceComparisonStatus.REJECTED -> VerificationEvidence.FACE_MATCH_REJECTED
                        FaceComparisonStatus.INCONCLUSIVE -> VerificationEvidence.FACE_MATCH_INCONCLUSIVE
                    }
                val progress = state.progress.completeOperation().withEvidence(setOf(evidence))
                beginDecision(
                    progress,
                    context,
                    listOf(VerificationEffect.CancelTimeout(event.operation)),
                )
            }

            is VerificationEvent.FaceComparisonFailed -> {
                recover(
                    state.progress,
                    event.operation,
                    RetryableStep.SELFIE,
                    RetryReason.TECHNICAL_FAILURE,
                    event.error,
                    context,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromDecision(
        state: MakingDecision,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            is VerificationEvent.DecisionRequested -> {
                ignored(state, TransitionDisposition.IGNORED_DUPLICATE_EVENT)
            }

            is VerificationEvent.DecisionCompleted -> {
                val decision = VerificationPolicyEvaluator.evaluate(context.policy, state.progress.evidence)
                terminal(state.progress, decision.outcome, decision.reason)
            }

            is VerificationEvent.DecisionFailed -> {
                terminal(
                    state.progress,
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.DECISION_FAILED,
                )
            }

            else -> {
                illegal(state)
            }
        }

    private fun transitionFromRecovery(
        state: RecoveryRequired,
        event: VerificationEvent,
        context: VerificationContext,
    ): TransitionResult =
        when (event) {
            VerificationEvent.Retry -> retry(state, context)
            VerificationEvent.AcknowledgeError -> terminateDeclinedRecovery(state)
            else -> illegal(state)
        }

    private fun retry(
        state: RecoveryRequired,
        context: VerificationContext,
    ): TransitionResult {
        if (state.retryDecision != RetryDecision.RETRY_AVAILABLE) return illegal(state)
        val progress = state.progress.withEvidence(setOf(VerificationEvidence.STEP_RETRIED))
        return when (state.failedStep) {
            RetryableStep.DOCUMENT_CAPTURE -> startDocumentCapture(progress, context)
            RetryableStep.OCR -> startOcr(progress, context)
            RetryableStep.NFC -> startNfc(progress, context)
            RetryableStep.SELFIE -> startSelfie(progress, context)
        }
    }

    private fun terminateDeclinedRecovery(state: RecoveryRequired): TransitionResult {
        val technical =
            state.retryReason == RetryReason.TECHNICAL_FAILURE ||
                state.retryReason == RetryReason.STEP_TIMEOUT
        return terminal(
            state.progress,
            if (technical) VerificationOutcome.TECHNICAL_FAILURE else VerificationOutcome.INCONCLUSIVE,
            if (technical) {
                VerificationTerminalReason.TECHNICAL_FAILURE
            } else {
                VerificationTerminalReason.INSUFFICIENT_EVIDENCE
            },
        )
    }

    private fun handleStepTimeout(
        state: ActiveVerificationState,
        operation: VerificationOperationToken,
        context: VerificationContext,
    ): TransitionResult {
        val retryable = retryableStep(operation.step)
        return if (retryable == null) {
            terminal(
                state.progress,
                VerificationOutcome.TECHNICAL_FAILURE,
                VerificationTerminalReason.STEP_TIMEOUT,
            )
        } else {
            recover(
                state.progress,
                operation,
                retryable,
                RetryReason.STEP_TIMEOUT,
                IdvError.Verification(VerificationFailure.STEP_TIMEOUT),
                context,
            )
        }
    }

    private fun recover(
        progress: VerificationProgress,
        operation: VerificationOperationToken,
        failedStep: RetryableStep,
        reason: RetryReason,
        error: IdvError?,
        context: VerificationContext,
    ): TransitionResult {
        val completed = progress.completeOperation()
        val decision = retryDecision(completed, failedStep, context)
        if (decision == RetryDecision.RETRY_AVAILABLE) {
            return TransitionResult(
                RecoveryRequired(completed, failedStep, reason, decision, error),
                listOf(VerificationEffect.CancelTimeout(operation)),
            )
        }
        val leadingEffects = listOf<VerificationEffect>(VerificationEffect.CancelTimeout(operation))
        if (failedStep == RetryableStep.NFC && !context.policy.requireNfcRead) {
            return routeAfterNfc(completed, context, leadingEffects)
        }
        if (failedStep == RetryableStep.SELFIE && !context.policy.requireFaceMatch) {
            return beginDecision(completed, context, leadingEffects)
        }
        val qualityFailure =
            reason == RetryReason.QUALITY_REJECTED || reason == RetryReason.INCONCLUSIVE_COMPARISON
        return terminal(
            completed,
            if (qualityFailure) VerificationOutcome.INCONCLUSIVE else VerificationOutcome.TECHNICAL_FAILURE,
            if (decision == RetryDecision.EXHAUSTED) {
                VerificationTerminalReason.RETRY_EXHAUSTED
            } else if (reason == RetryReason.STEP_TIMEOUT) {
                VerificationTerminalReason.STEP_TIMEOUT
            } else if (qualityFailure) {
                VerificationTerminalReason.INSUFFICIENT_EVIDENCE
            } else {
                VerificationTerminalReason.TECHNICAL_FAILURE
            },
            leadingEffects,
        )
    }

    private fun retryDecision(
        progress: VerificationProgress,
        step: RetryableStep,
        context: VerificationContext,
    ): RetryDecision =
        when {
            !context.policy.allowRetry -> {
                RetryDecision.DISABLED
            }

            progress.retries.attemptsFor(step) >= context.retryPolicy.maximumAttempts(step) -> {
                RetryDecision.EXHAUSTED
            }

            else -> {
                RetryDecision.RETRY_AVAILABLE
            }
        }

    private fun startDocumentCapture(
        progress: VerificationProgress,
        context: VerificationContext,
    ): TransitionResult {
        val attempted = progress.recordAttempt(RetryableStep.DOCUMENT_CAPTURE)
        return beginOperation(
            attempted,
            VerificationStep.DOCUMENT_CAPTURE,
            ::CapturingDocument,
            { VerificationEffect.CaptureDocument(it) },
            context,
        )
    }

    private fun startOcrAfter(
        progress: VerificationProgress,
        completedOperation: VerificationOperationToken,
        context: VerificationContext,
    ): TransitionResult {
        val document =
            progress.artifacts.reference(VerificationArtifactKind.DOCUMENT_CAPTURE)
                ?: return terminal(
                    progress,
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.TECHNICAL_FAILURE,
                )
        val completed = progress.completeOperation().recordAttempt(RetryableStep.OCR)
        return beginOperation(
            completed,
            VerificationStep.OCR,
            ::RunningOcr,
            { VerificationEffect.RunOcr(it, document) },
            context,
            listOf(VerificationEffect.CancelTimeout(completedOperation)),
        )
    }

    private fun startOcr(
        progress: VerificationProgress,
        context: VerificationContext,
    ): TransitionResult {
        val document =
            progress.artifacts.reference(VerificationArtifactKind.DOCUMENT_CAPTURE)
                ?: return terminal(
                    progress,
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.TECHNICAL_FAILURE,
                )
        val attempted = progress.recordAttempt(RetryableStep.OCR)
        return beginOperation(
            attempted,
            VerificationStep.OCR,
            ::RunningOcr,
            { VerificationEffect.RunOcr(it, document) },
            context,
        )
    }

    private fun startNfc(
        progress: VerificationProgress,
        context: VerificationContext,
    ): TransitionResult {
        val accessKey =
            progress.artifacts.reference(VerificationArtifactKind.MRZ_ACCESS_KEY)
                ?: return terminal(
                    progress,
                    VerificationOutcome.TECHNICAL_FAILURE,
                    VerificationTerminalReason.TECHNICAL_FAILURE,
                )
        val attempted = progress.recordAttempt(RetryableStep.NFC)
        return beginOperation(
            attempted,
            VerificationStep.NFC_READ,
            ::ReadingNfc,
            { VerificationEffect.StartNfcRead(it, accessKey) },
            context,
        )
    }

    private fun startSelfie(
        progress: VerificationProgress,
        context: VerificationContext,
    ): TransitionResult {
        val attempted = progress.recordAttempt(RetryableStep.SELFIE)
        return beginOperation(
            attempted,
            VerificationStep.SELFIE_CAPTURE,
            ::CapturingSelfie,
            { VerificationEffect.CaptureSelfie(it) },
            context,
        )
    }

    private fun routeAfterNfc(
        progress: VerificationProgress,
        context: VerificationContext,
        leadingEffects: List<VerificationEffect> = emptyList(),
    ): TransitionResult {
        if (
            context.capabilities.isAvailable(
                com.ing.offlineidv.verification.model.VerificationCapability.FACE_COMPARISON,
            )
        ) {
            return TransitionResult(
                AwaitingSelfie(progress),
                leadingEffects + VerificationEffect.PromptForSelfie(progress.sessionId),
            )
        }
        val unavailable = mutableSetOf(VerificationEvidence.CAPABILITY_UNAVAILABLE)
        if (context.policy.requireFaceMatch) unavailable += VerificationEvidence.REQUIRED_STEP_SKIPPED
        return beginDecision(progress.withEvidence(unavailable), context, leadingEffects)
    }

    private fun beginDecision(
        progress: VerificationProgress,
        context: VerificationContext,
        leadingEffects: List<VerificationEffect> = emptyList(),
    ): TransitionResult =
        beginOperation(
            progress,
            VerificationStep.DECISION,
            ::MakingDecision,
            { VerificationEffect.EvaluateVerificationPolicy(it) },
            context,
            leadingEffects,
        )

    private fun beginOperation(
        progress: VerificationProgress,
        step: VerificationStep,
        stateFactory: (VerificationProgress) -> ActiveVerificationState,
        effectFactory: (VerificationOperationToken) -> VerificationEffect,
        context: VerificationContext,
        leadingEffects: List<VerificationEffect> = emptyList(),
    ): TransitionResult {
        val operation = progress.beginOperation(step)
        return TransitionResult(
            stateFactory(operation.progress),
            leadingEffects + effectFactory(operation.token) + schedule(operation.token, context),
        )
    }

    private fun completeAndBegin(
        progress: VerificationProgress,
        completedOperation: VerificationOperationToken,
        nextStep: VerificationStep,
        stateFactory: (VerificationProgress) -> ActiveVerificationState,
        effectFactory: (VerificationOperationToken) -> VerificationEffect,
        context: VerificationContext,
    ): TransitionResult =
        beginOperation(
            progress.completeOperation(),
            nextStep,
            stateFactory,
            effectFactory,
            context,
            listOf(VerificationEffect.CancelTimeout(completedOperation)),
        )

    private fun terminal(
        progress: VerificationProgress,
        outcome: VerificationOutcome,
        reason: VerificationTerminalReason?,
        leadingEffects: List<VerificationEffect> = emptyList(),
    ): TransitionResult {
        val summary =
            VerificationTerminalSummary(
                progress.sessionId,
                outcome,
                progress.evidence,
                progress.retries,
                reason,
            )
        val state: TerminalState =
            when (outcome) {
                VerificationOutcome.VERIFIED -> Verified(summary)
                VerificationOutcome.REJECTED -> Rejected(summary)
                VerificationOutcome.INCONCLUSIVE -> Inconclusive(summary)
                VerificationOutcome.TECHNICAL_FAILURE -> TechnicalFailure(summary)
                VerificationOutcome.CANCELLED -> Cancelled(summary)
                VerificationOutcome.EXPIRED -> Expired(summary)
            }
        val effects = leadingEffects.toMutableList()
        progress.activeOperation?.let { operation ->
            if (effects.none { it == VerificationEffect.CancelTimeout(operation) }) {
                effects += VerificationEffect.CancelTimeout(operation)
            }
        }
        if (progress.sessionExpiryScheduled) {
            effects += VerificationEffect.CancelTimeout(progress.sessionExpiryToken)
        }
        effects += VerificationEffect.ClearSensitiveSessionData(progress.sessionId)
        effects += VerificationEffect.EmitTerminalResult(summary)
        return TransitionResult(state, effects)
    }

    private fun passiveAuthenticationEvidence(summary: ChipValidationSummary): VerificationEvidence =
        when (summary.passiveAuthentication) {
            PassiveAuthenticationStatus.VALID -> {
                VerificationEvidence.PASSIVE_AUTHENTICATION_VALID
            }

            PassiveAuthenticationStatus.FAILED -> {
                VerificationEvidence.PASSIVE_AUTHENTICATION_FAILED
            }

            PassiveAuthenticationStatus.NOT_PERFORMED -> {
                VerificationEvidence.PASSIVE_AUTHENTICATION_NOT_PERFORMED
            }

            PassiveAuthenticationStatus.UNAVAILABLE,
            PassiveAuthenticationStatus.UNSUPPORTED,
            PassiveAuthenticationStatus.TECHNICAL_ERROR,
            -> {
                VerificationEvidence.PASSIVE_AUTHENTICATION_NOT_PERFORMED
            }
        }

    private fun retryableStep(step: VerificationStep): RetryableStep? =
        when (step) {
            VerificationStep.DOCUMENT_CAPTURE,
            VerificationStep.DOCUMENT_QUALITY,
            -> RetryableStep.DOCUMENT_CAPTURE

            VerificationStep.OCR,
            VerificationStep.MRZ,
            -> RetryableStep.OCR

            VerificationStep.NFC_READ,
            VerificationStep.CHIP_VALIDATION,
            VerificationStep.PRINTED_CHIP_COMPARISON,
            -> RetryableStep.NFC

            VerificationStep.SELFIE_CAPTURE,
            VerificationStep.SELFIE_QUALITY,
            VerificationStep.FACE_COMPARISON,
            -> RetryableStep.SELFIE

            VerificationStep.SESSION,
            VerificationStep.INITIALIZATION,
            VerificationStep.CAMERA_PERMISSION,
            VerificationStep.CAMERA_PREPARATION,
            VerificationStep.DECISION,
            -> null
        }

    private fun schedule(
        operation: VerificationOperationToken,
        context: VerificationContext,
    ): VerificationEffect.ScheduleTimeout = VerificationEffect.ScheduleTimeout(operation, context.timeoutPolicy.durationFor(operation.step))

    private fun operationDisposition(
        progress: VerificationProgress,
        operation: VerificationOperationToken,
    ): TransitionDisposition? {
        val active = progress.activeOperation
        if (active == operation) return null
        if (operation.sessionId != progress.sessionId) return TransitionDisposition.IGNORED_STALE_EVENT
        if (active != null) {
            return if (operation.generation < active.generation) {
                TransitionDisposition.IGNORED_DUPLICATE_EVENT
            } else {
                TransitionDisposition.IGNORED_STALE_EVENT
            }
        }
        return if (operation.generation < progress.nextGeneration) {
            TransitionDisposition.IGNORED_DUPLICATE_EVENT
        } else {
            TransitionDisposition.IGNORED_STALE_EVENT
        }
    }

    private fun operationFrom(event: VerificationEvent): VerificationOperationToken? =
        when (event) {
            is VerificationEvent.InitializationSucceeded -> event.operation

            is VerificationEvent.InitializationFailed -> event.operation

            is VerificationEvent.SessionTimedOut -> event.operation

            is VerificationEvent.CameraReady -> event.operation

            is VerificationEvent.CameraPreparationFailed -> event.operation

            is VerificationEvent.DocumentCaptured -> event.operation

            is VerificationEvent.CaptureQualityAccepted -> event.operation

            is VerificationEvent.CaptureQualityRejected -> event.operation

            is VerificationEvent.DocumentQualityFailed -> event.operation

            is VerificationEvent.CaptureFailed -> event.operation

            is VerificationEvent.OcrStarted -> event.operation

            is VerificationEvent.OcrSucceeded -> event.operation

            is VerificationEvent.OcrFailed -> event.operation

            is VerificationEvent.MrzExtractionSucceeded -> event.operation

            is VerificationEvent.MrzExtractionFailed -> event.operation

            is VerificationEvent.MrzValidationCompleted -> event.operation

            is VerificationEvent.MrzValidationFailed -> event.operation

            is VerificationEvent.NfcStarted -> event.operation

            is VerificationEvent.NfcReadSucceeded -> event.operation

            is VerificationEvent.NfcReadFailed -> event.operation

            is VerificationEvent.ChipValidationCompleted -> event.operation

            is VerificationEvent.ChipValidationFailed -> event.operation

            is VerificationEvent.PrintedAndChipComparisonCompleted -> event.operation

            is VerificationEvent.PrintedAndChipComparisonFailed -> event.operation

            is VerificationEvent.SelfieCaptured -> event.operation

            is VerificationEvent.SelfieCaptureFailed -> event.operation

            is VerificationEvent.SelfieQualityAccepted -> event.operation

            is VerificationEvent.SelfieQualityRejected -> event.operation

            is VerificationEvent.SelfieQualityFailed -> event.operation

            is VerificationEvent.FaceComparisonCompleted -> event.operation

            is VerificationEvent.FaceComparisonFailed -> event.operation

            is VerificationEvent.DecisionRequested -> event.operation

            is VerificationEvent.DecisionCompleted -> event.operation

            is VerificationEvent.DecisionFailed -> event.operation

            is VerificationEvent.Start,
            is VerificationEvent.SessionExpired,
            VerificationEvent.Reset,
            VerificationEvent.Cancel,
            VerificationEvent.PassportSelected,
            VerificationEvent.CameraPermissionRequired,
            VerificationEvent.CameraPermissionGranted,
            VerificationEvent.CameraPermissionDenied,
            VerificationEvent.CaptureRequested,
            VerificationEvent.NfcRequested,
            VerificationEvent.SelfieRequested,
            VerificationEvent.Retry,
            VerificationEvent.Back,
            VerificationEvent.AcknowledgeError,
            -> null
        }

    private fun illegal(state: VerificationState): TransitionResult = ignored(state, TransitionDisposition.IGNORED_ILLEGAL_EVENT)

    private fun ignored(
        state: VerificationState,
        disposition: TransitionDisposition,
    ): TransitionResult = TransitionResult(state, disposition = disposition)
}
