package com.ing.offlineidv.ui

import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.verification.model.AwaitingNfc
import com.ing.offlineidv.verification.model.AwaitingSelfie
import com.ing.offlineidv.verification.model.CameraPermissionRequired
import com.ing.offlineidv.verification.model.CameraReady
import com.ing.offlineidv.verification.model.Cancelled
import com.ing.offlineidv.verification.model.CapturingDocument
import com.ing.offlineidv.verification.model.CapturingSelfie
import com.ing.offlineidv.verification.model.ComparingFaces
import com.ing.offlineidv.verification.model.ComparingPrintedAndChipData
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.EvaluatingDocumentQuality
import com.ing.offlineidv.verification.model.EvaluatingSelfie
import com.ing.offlineidv.verification.model.Expired
import com.ing.offlineidv.verification.model.ExtractingMrz
import com.ing.offlineidv.verification.model.Idle
import com.ing.offlineidv.verification.model.Inconclusive
import com.ing.offlineidv.verification.model.Initializing
import com.ing.offlineidv.verification.model.MakingDecision
import com.ing.offlineidv.verification.model.NfcReadPhase
import com.ing.offlineidv.verification.model.PreparingCamera
import com.ing.offlineidv.verification.model.ReadingNfc
import com.ing.offlineidv.verification.model.RecoveryRequired
import com.ing.offlineidv.verification.model.Rejected
import com.ing.offlineidv.verification.model.RetryDecision
import com.ing.offlineidv.verification.model.RetryableStep
import com.ing.offlineidv.verification.model.RunningOcr
import com.ing.offlineidv.verification.model.TechnicalFailure
import com.ing.offlineidv.verification.model.TerminalState
import com.ing.offlineidv.verification.model.ValidatingChipData
import com.ing.offlineidv.verification.model.ValidatingMrz
import com.ing.offlineidv.verification.model.VerificationEvidence
import com.ing.offlineidv.verification.model.VerificationOutcome
import com.ing.offlineidv.verification.model.VerificationState
import com.ing.offlineidv.verification.model.Verified

/** Exhaustive projection from safe reducer state to presentation-only data. */
public object VerificationUiStateMapper {
    public fun map(state: VerificationState): AtlasUiState =
        when (state) {
            Idle -> {
                AtlasUiState.Welcome
            }

            is Initializing -> {
                processing("Starting offline session", "Preparing the offline verification session.", Stage.DOCUMENT)
            }

            is DocumentSelection -> {
                AtlasUiState.DocumentSelection
            }

            is CameraPermissionRequired -> {
                processing("Preparing document capture", "Waiting for the capture boundary.", Stage.DOCUMENT)
            }

            is PreparingCamera -> {
                processing("Preparing document capture", "Position your document inside the frame.", Stage.DOCUMENT)
            }

            is CameraReady -> {
                AtlasUiState.DocumentCapture(progress(Stage.DOCUMENT))
            }

            is CapturingDocument -> {
                processing("Capturing document", "Capturing the document on this device.", Stage.DOCUMENT)
            }

            is EvaluatingDocumentQuality -> {
                processing("Checking image quality", "Checking framing and readability signals.", Stage.DOCUMENT)
            }

            is RunningOcr -> {
                processing("Reading document", "Processing document text on this device.", Stage.MRZ)
            }

            is ExtractingMrz -> {
                processing("Finding the MRZ", "Locating the machine-readable zone.", Stage.MRZ)
            }

            is ValidatingMrz -> {
                processing("Checking the MRZ", "Checking structure, dates, and check digits.", Stage.MRZ)
            }

            is AwaitingNfc -> {
                AtlasUiState.Nfc(
                    progress = progress(Stage.CHIP),
                    evidence = evidenceItems(state.progress.evidence, EvidenceScope.MRZ),
                    scanStatus = AtlasNfcScanStatus.READY_TO_SCAN,
                    canStartScan = true,
                )
            }

            is ReadingNfc -> {
                AtlasUiState.Nfc(
                    progress = progress(Stage.CHIP),
                    evidence = evidenceItems(state.progress.evidence, EvidenceScope.MRZ),
                    scanStatus = state.phase.toPresentationStatus(),
                    canStartScan = false,
                )
            }

            is ValidatingChipData -> {
                AtlasUiState.Nfc(
                    progress = progress(Stage.CHIP),
                    evidence = evidenceItems(state.progress.evidence, EvidenceScope.COMPLETED),
                    scanStatus = AtlasNfcScanStatus.SCAN_COMPLETE,
                    canStartScan = false,
                )
            }

            is ComparingPrintedAndChipData -> {
                AtlasUiState.Nfc(
                    progress = progress(Stage.CHIP),
                    evidence = evidenceItems(state.progress.evidence, EvidenceScope.COMPLETED),
                    scanStatus = AtlasNfcScanStatus.SCAN_COMPLETE,
                    canStartScan = false,
                )
            }

            is AwaitingSelfie -> {
                AtlasUiState.Selfie(
                    progress = progress(Stage.FACE),
                    evidence = evidenceItems(state.progress.evidence, EvidenceScope.COMPLETED),
                )
            }

            is CapturingSelfie -> {
                processing("Capturing selfie", "Capturing the selfie on this device.", Stage.FACE)
            }

            is EvaluatingSelfie -> {
                processing("Checking selfie quality", "Reviewing selfie quality signals.", Stage.FACE)
            }

            is ComparingFaces -> {
                processing("Comparing faces", "Comparing the available face signals.", Stage.FACE)
            }

            is MakingDecision -> {
                processing("Making a decision", "Applying the configured policy to safe evidence.", Stage.DECISION)
            }

            is RecoveryRequired -> {
                recovery(state)
            }

            is TerminalState -> {
                result(state)
            }
        }

    private fun processing(
        title: String,
        detail: String,
        stage: Stage,
    ): AtlasUiState.Processing = AtlasUiState.Processing(title, detail, progress(stage))

    private fun recovery(state: RecoveryRequired): AtlasUiState.Recovery {
        val stage =
            when (state.failedStep) {
                RetryableStep.DOCUMENT_CAPTURE -> Stage.DOCUMENT
                RetryableStep.OCR -> Stage.MRZ
                RetryableStep.NFC -> Stage.CHIP
                RetryableStep.SELFIE -> Stage.FACE
            }
        val defaultTitle =
            when (state.failedStep) {
                RetryableStep.DOCUMENT_CAPTURE -> "Document capture needs attention"
                RetryableStep.OCR -> "Document text needs attention"
                RetryableStep.NFC -> "Chip read needs attention"
                RetryableStep.SELFIE -> "Selfie needs attention"
            }
        val nfcRecovery = if (state.failedStep == RetryableStep.NFC) nfcRecovery(state) else null
        return AtlasUiState.Recovery(
            title = nfcRecovery?.title ?: defaultTitle,
            detail = nfcRecovery?.detail ?: "The step did not complete. You can use the safe actions below.",
            canRetry = state.retryDecision == RetryDecision.RETRY_AVAILABLE,
            progress = progress(stage, needsAttention = true),
            nfcScanStatus = nfcRecovery?.status,
        )
    }

    private fun nfcRecovery(state: RecoveryRequired): NfcRecoveryPresentation {
        val reason = state.error?.reason
        return when (reason) {
            NfcFailure.TAG_LOST -> {
                NfcRecoveryPresentation(
                    AtlasNfcScanStatus.CONNECTION_LOST,
                    "Connection lost",
                    "Hold the document against the phone again and keep it still, then retry.",
                )
            }

            NfcFailure.ACCESS_DENIED -> {
                NfcRecoveryPresentation(
                    AtlasNfcScanStatus.AUTHENTICATION_FAILED,
                    "Chip authentication failed",
                    "The document chip did not accept the MRZ-derived access key. Check the document and retry.",
                )
            }

            NfcFailure.UNSUPPORTED_TAG,
            NfcFailure.ISO_DEP_UNAVAILABLE,
            NfcFailure.PROTOCOL_UNSUPPORTED,
            NfcFailure.ACCESS_CONTROL_UNSUPPORTED,
            -> {
                NfcRecoveryPresentation(
                    AtlasNfcScanStatus.UNSUPPORTED_CHIP,
                    "Unsupported document chip",
                    "This chip or its protected access method is not supported by this build.",
                )
            }

            NfcFailure.CONNECTION_TIMEOUT,
            NfcFailure.COMMUNICATION_TIMEOUT,
            NfcFailure.TIMEOUT,
            VerificationFailure.STEP_TIMEOUT,
            -> {
                NfcRecoveryPresentation(
                    AtlasNfcScanStatus.TIMED_OUT,
                    "Chip scan timed out",
                    "Hold the document firmly against the phone and try again.",
                )
            }

            else -> {
                NfcRecoveryPresentation(
                    AtlasNfcScanStatus.SCAN_FAILED,
                    "Chip scan failed",
                    "The chip scan could not complete. Reposition the document and try again.",
                )
            }
        }
    }

    private fun NfcReadPhase.toPresentationStatus(): AtlasNfcScanStatus =
        when (this) {
            NfcReadPhase.STARTING_READER -> AtlasNfcScanStatus.STARTING_READER
            NfcReadPhase.READY_TO_SCAN -> AtlasNfcScanStatus.READY_TO_SCAN
            NfcReadPhase.CHIP_DETECTED -> AtlasNfcScanStatus.CHIP_DETECTED
            NfcReadPhase.CONNECTING -> AtlasNfcScanStatus.CONNECTING
            NfcReadPhase.SCAN_IN_PROGRESS -> AtlasNfcScanStatus.SCAN_IN_PROGRESS
        }

    private fun result(state: TerminalState): AtlasUiState.Result {
        val (title, detail) = outcomeCopy(state.summary.outcome)
        return AtlasUiState.Result(
            outcome = state.summary.outcome,
            title = title,
            detail = detail,
            evidence = evidenceItems(state.summary.evidence, EvidenceScope.ALL),
        )
    }

    private fun outcomeCopy(outcome: VerificationOutcome): Pair<String, String> =
        when (outcome) {
            VerificationOutcome.VERIFIED -> {
                "Verification complete" to "The required offline verification checks completed."
            }

            VerificationOutcome.REJECTED -> {
                "Verification not approved" to "The verification policy did not accept the available evidence."
            }

            VerificationOutcome.INCONCLUSIVE -> {
                "More evidence needed" to "The available evidence was insufficient for a conclusive result."
            }

            VerificationOutcome.TECHNICAL_FAILURE -> {
                "Something went wrong" to "The verification flow could not complete. No information was sent anywhere."
            }

            VerificationOutcome.CANCELLED -> {
                "Verification cancelled" to "The session was cancelled and its in-memory artifacts were cleared."
            }

            VerificationOutcome.EXPIRED -> {
                "Session expired" to "Start a new session to continue."
            }
        }

    private fun progress(
        stage: Stage,
        needsAttention: Boolean = false,
    ): AtlasProgress =
        AtlasProgress(
            Stage.entries.map { item ->
                val status =
                    when {
                        item.ordinal < stage.ordinal -> AtlasStepStatus.COMPLETE
                        item == stage && needsAttention -> AtlasStepStatus.NEEDS_ATTENTION
                        item == stage -> AtlasStepStatus.CURRENT
                        else -> AtlasStepStatus.UPCOMING
                    }
                AtlasProgressStep(item.label, status)
            },
        )

    private fun evidenceItems(
        evidence: Set<VerificationEvidence>,
        scope: EvidenceScope,
    ): List<AtlasEvidenceItem> =
        evidence
            .asSequence()
            .filter { item -> scope.includes(item) }
            .sortedBy(VerificationEvidence::ordinal)
            .map(::evidenceItem)
            .toList()

    private fun evidenceItem(evidence: VerificationEvidence): AtlasEvidenceItem =
        when (evidence) {
            VerificationEvidence.MRZ_STRUCTURE_VALID -> {
                confirmed("MRZ structure", "Expected document structure found.")
            }

            VerificationEvidence.MRZ_STRUCTURE_INVALID -> {
                attention("MRZ structure", "Structure needs attention.")
            }

            VerificationEvidence.MRZ_CHECK_DIGITS_VALID -> {
                confirmed("MRZ check digits", "Check digits are consistent.")
            }

            VerificationEvidence.MRZ_CHECK_DIGITS_INVALID -> {
                attention("MRZ check digits", "Check digits are inconsistent.")
            }

            VerificationEvidence.MRZ_CHARACTER_AMBIGUITY -> {
                attention("MRZ characters", "Character ambiguity was detected.")
            }

            VerificationEvidence.MRZ_CENTURY_AMBIGUITY -> {
                attention("MRZ date", "Date-century ambiguity was detected.")
            }

            VerificationEvidence.DOCUMENT_EXPIRED -> {
                attention("Document expiry", "The document is expired.")
            }

            VerificationEvidence.DOCUMENT_EXPIRY_UNKNOWN -> {
                attention("Document expiry", "The expiry could not be resolved.")
            }

            VerificationEvidence.NFC_CHIP_READ -> {
                confirmed("Document chip", "Document chip data was read.")
            }

            VerificationEvidence.NFC_READ_FAILED -> {
                attention("Document chip", "The document chip read failed.")
            }

            VerificationEvidence.DG1_AVAILABLE -> {
                confirmed("Chip identity group", "A safe availability signal was recorded.")
            }

            VerificationEvidence.DG2_AVAILABLE -> {
                confirmed("Chip portrait group", "A safe availability signal was recorded.")
            }

            VerificationEvidence.PRINTED_CHIP_DATA_MATCH -> {
                confirmed("Document consistency", "Printed and chip signals match.")
            }

            VerificationEvidence.PRINTED_CHIP_DATA_MISMATCH -> {
                attention("Document consistency", "Printed and chip signals differ.")
            }

            VerificationEvidence.PASSIVE_AUTHENTICATION_VALID -> {
                confirmed("Signed chip data", "The chip data signature and trusted signer were verified.")
            }

            VerificationEvidence.PASSIVE_AUTHENTICATION_FAILED -> {
                attention("Signed chip data", "The signed chip data could not be verified.")
            }

            VerificationEvidence.PASSIVE_AUTHENTICATION_NOT_PERFORMED -> {
                notPerformed("Signed chip data", "This check was not completed.")
            }

            VerificationEvidence.CHIP_AUTHENTICATION_SUCCEEDED -> {
                confirmed("Live chip proof", "Fresh chip-key possession was verified.")
            }

            VerificationEvidence.CHIP_AUTHENTICATION_FAILED -> {
                attention("Live chip proof", "The chip-key possession check failed.")
            }

            VerificationEvidence.CHIP_AUTHENTICATION_NOT_PERFORMED -> {
                notPerformed("Live chip proof", "This check was not completed.")
            }

            VerificationEvidence.SELFIE_QUALITY_ACCEPTED -> {
                confirmed("Selfie quality", "Quality signal accepted.")
            }

            VerificationEvidence.SELFIE_QUALITY_REJECTED -> {
                attention("Selfie quality", "Quality signal needs attention.")
            }

            VerificationEvidence.FACE_MATCH_ACCEPTED -> {
                confirmed("Face comparison", "Comparison accepted.")
            }

            VerificationEvidence.FACE_MATCH_REJECTED -> {
                attention("Face comparison", "Comparison not accepted.")
            }

            VerificationEvidence.FACE_MATCH_INCONCLUSIVE -> {
                attention("Face comparison", "Comparison inconclusive.")
            }

            VerificationEvidence.STEP_RETRIED -> {
                confirmed("Recovery", "A reducer-approved retry was used.")
            }

            VerificationEvidence.REQUIRED_STEP_SKIPPED -> {
                attention("Required step", "A required step was not completed.")
            }

            VerificationEvidence.CAPABILITY_UNAVAILABLE -> {
                attention("Device capability", "An unavailable device capability was recorded.")
            }
        }

    private fun confirmed(
        title: String,
        detail: String,
    ): AtlasEvidenceItem = AtlasEvidenceItem(title, detail, AtlasEvidenceStatus.CONFIRMED)

    private fun attention(
        title: String,
        detail: String,
    ): AtlasEvidenceItem = AtlasEvidenceItem(title, detail, AtlasEvidenceStatus.ATTENTION)

    private fun notPerformed(
        title: String,
        detail: String,
    ): AtlasEvidenceItem = AtlasEvidenceItem(title, detail, AtlasEvidenceStatus.NOT_PERFORMED)

    private enum class Stage(
        val label: String,
    ) {
        DOCUMENT("Document"),
        MRZ("MRZ"),
        CHIP("Chip"),
        FACE("Face"),
        DECISION("Decision"),
    }

    private enum class EvidenceScope {
        MRZ,
        COMPLETED,
        ALL,
        ;

        fun includes(evidence: VerificationEvidence): Boolean =
            when (this) {
                MRZ -> evidence.name.startsWith("MRZ_") || evidence.name.startsWith("DOCUMENT_")

                COMPLETED,
                ALL,
                -> true
            }
    }

    private data class NfcRecoveryPresentation(
        val status: AtlasNfcScanStatus,
        val title: String,
        val detail: String,
    )
}
