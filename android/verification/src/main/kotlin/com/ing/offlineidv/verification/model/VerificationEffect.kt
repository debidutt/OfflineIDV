package com.ing.offlineidv.verification.model

import com.ing.offlineidv.core.session.IdvSessionId
import java.time.Duration

/** Closed external intents emitted by the pure reducer. */
public sealed interface VerificationEffect {
    public data class InitializeSession(
        public val sessionId: IdvSessionId,
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class RequestCameraPermission(
        public val sessionId: IdvSessionId,
    ) : VerificationEffect

    public data class PrepareCamera(
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class CaptureDocument(
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class EvaluateDocumentQuality(
        public val operation: VerificationOperationToken,
        public val documentReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class RunOcr(
        public val operation: VerificationOperationToken,
        public val documentReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class ExtractAndValidateMrz(
        public val operation: VerificationOperationToken,
        public val ocrReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class PromptForNfc(
        public val sessionId: IdvSessionId,
    ) : VerificationEffect

    public data class StartNfcRead(
        public val operation: VerificationOperationToken,
        public val accessKeyReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class ValidateChipData(
        public val operation: VerificationOperationToken,
        public val chipReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class ComparePrintedAndChipData(
        public val operation: VerificationOperationToken,
        public val printedDataReference: VerificationArtifactReference,
        public val chipReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class PromptForSelfie(
        public val sessionId: IdvSessionId,
    ) : VerificationEffect

    public data class CaptureSelfie(
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class EvaluateSelfieQuality(
        public val operation: VerificationOperationToken,
        public val selfieReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class CompareFaces(
        public val operation: VerificationOperationToken,
        public val portraitReference: VerificationArtifactReference,
        public val selfieReference: VerificationArtifactReference,
    ) : VerificationEffect

    public data class EvaluateVerificationPolicy(
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class ScheduleTimeout(
        public val operation: VerificationOperationToken,
        public val duration: Duration,
    ) : VerificationEffect

    public data class CancelTimeout(
        public val operation: VerificationOperationToken,
    ) : VerificationEffect

    public data class ClearSensitiveSessionData(
        public val sessionId: IdvSessionId,
    ) : VerificationEffect

    public data class EmitTerminalResult(
        public val summary: VerificationTerminalSummary,
    ) : VerificationEffect
}
