package com.ing.offlineidv.verification.model

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.verification.mrz.MrzVerificationSummary

/** Closed events accepted by the verification reducer. */
public sealed interface VerificationEvent {
    public data class Start(
        public val sessionId: IdvSessionId,
    ) : VerificationEvent

    public data class InitializationSucceeded(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class InitializationFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data object Reset : VerificationEvent

    public data object Cancel : VerificationEvent

    /** Step timeout. Session expiry is represented separately by [SessionExpired]. */
    public data class SessionTimedOut(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class SessionExpired(
        public val sessionId: IdvSessionId,
    ) : VerificationEvent

    public data object PassportSelected : VerificationEvent

    public data object CameraPermissionRequired : VerificationEvent

    public data object CameraPermissionGranted : VerificationEvent

    public data object CameraPermissionDenied : VerificationEvent

    public data class CameraReady(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    /** Camera binding failed before capture could start. */
    public data class CameraPreparationFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data object CaptureRequested : VerificationEvent

    public data class DocumentCaptured(
        public val operation: VerificationOperationToken,
        public val documentReference: VerificationArtifactReference,
    ) : VerificationEvent

    public data class CaptureQualityAccepted(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class CaptureQualityRejected(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class DocumentQualityFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class CaptureFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class OcrStarted(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class OcrSucceeded(
        public val operation: VerificationOperationToken,
        public val ocrReference: VerificationArtifactReference,
    ) : VerificationEvent

    public data class OcrFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class MrzExtractionSucceeded(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class MrzExtractionFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class MrzValidationCompleted(
        public val operation: VerificationOperationToken,
        public val summary: MrzVerificationSummary,
        public val printedDataReference: VerificationArtifactReference,
        public val accessKeyReference: VerificationArtifactReference,
    ) : VerificationEvent

    public data class MrzValidationFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data object NfcRequested : VerificationEvent

    public data class NfcStarted(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class NfcReadSucceeded(
        public val operation: VerificationOperationToken,
        public val chipReference: VerificationArtifactReference,
    ) : VerificationEvent

    public data class NfcReadFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class ChipValidationCompleted(
        public val operation: VerificationOperationToken,
        public val summary: ChipValidationSummary,
    ) : VerificationEvent

    public data class ChipValidationFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class PrintedAndChipComparisonCompleted(
        public val operation: VerificationOperationToken,
        public val status: PrintedChipComparisonStatus,
    ) : VerificationEvent

    public data class PrintedAndChipComparisonFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data object SelfieRequested : VerificationEvent

    public data class SelfieCaptured(
        public val operation: VerificationOperationToken,
        public val selfieReference: VerificationArtifactReference,
    ) : VerificationEvent

    public data class SelfieCaptureFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class SelfieQualityAccepted(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class SelfieQualityRejected(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class SelfieQualityFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class FaceComparisonCompleted(
        public val operation: VerificationOperationToken,
        public val status: FaceComparisonStatus,
    ) : VerificationEvent

    public data class FaceComparisonFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data class DecisionRequested(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class DecisionCompleted(
        public val operation: VerificationOperationToken,
    ) : VerificationEvent

    public data class DecisionFailed(
        public val operation: VerificationOperationToken,
        public val error: IdvError,
    ) : VerificationEvent

    public data object Retry : VerificationEvent

    public data object Back : VerificationEvent

    public data object AcknowledgeError : VerificationEvent
}
