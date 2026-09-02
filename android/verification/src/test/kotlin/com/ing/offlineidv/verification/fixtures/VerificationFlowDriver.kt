package com.ing.offlineidv.verification.fixtures

import com.ing.offlineidv.verification.model.FaceComparisonStatus
import com.ing.offlineidv.verification.model.PrintedChipComparisonStatus
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.mrz.MrzVerificationSummary

internal fun StateMachineHarness.startAndInitialize() {
    dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
    dispatch(VerificationEvent.InitializationSucceeded(operation()))
}

internal fun StateMachineHarness.advanceToCameraReady() {
    startAndInitialize()
    dispatch(VerificationEvent.PassportSelected)
    dispatch(VerificationEvent.CameraPermissionGranted)
    dispatch(VerificationEvent.CameraReady(operation()))
}

internal fun StateMachineHarness.advanceToDocumentQuality() {
    advanceToCameraReady()
    dispatch(VerificationEvent.CaptureRequested)
    dispatch(
        VerificationEvent.DocumentCaptured(
            operation(),
            VerificationFixtures.documentReference,
        ),
    )
}

internal fun StateMachineHarness.advanceToOcr() {
    advanceToDocumentQuality()
    dispatch(VerificationEvent.CaptureQualityAccepted(operation()))
}

internal fun StateMachineHarness.advanceToMrzValidation() {
    advanceToOcr()
    dispatch(VerificationEvent.OcrSucceeded(operation(), VerificationFixtures.ocrReference))
    dispatch(VerificationEvent.MrzExtractionSucceeded(operation()))
}

internal fun StateMachineHarness.completeMrz(summary: MrzVerificationSummary = VerificationFixtures.validMrzSummary) {
    dispatch(
        VerificationEvent.MrzValidationCompleted(
            operation(),
            summary,
            VerificationFixtures.printedReference,
            VerificationFixtures.accessKeyReference,
        ),
    )
}

internal fun StateMachineHarness.advanceToNfcRead() {
    advanceToMrzValidation()
    completeMrz()
    dispatch(VerificationEvent.NfcRequested)
}

internal fun StateMachineHarness.advanceToChipValidation() {
    advanceToNfcRead()
    dispatch(VerificationEvent.NfcReadSucceeded(operation(), VerificationFixtures.chipReference))
}

internal fun StateMachineHarness.advanceToPrintedChipComparison() {
    advanceToChipValidation()
    dispatch(VerificationEvent.ChipValidationCompleted(operation(), VerificationFixtures.validChipSummary))
}

internal fun StateMachineHarness.advanceToAwaitingSelfie() {
    advanceToPrintedChipComparison()
    dispatch(
        VerificationEvent.PrintedAndChipComparisonCompleted(
            operation(),
            PrintedChipComparisonStatus.MATCH,
        ),
    )
}

internal fun StateMachineHarness.advanceToSelfieQuality() {
    advanceToAwaitingSelfie()
    dispatch(VerificationEvent.SelfieRequested)
    dispatch(VerificationEvent.SelfieCaptured(operation(), VerificationFixtures.selfieReference))
}

internal fun StateMachineHarness.advanceToFaceComparison() {
    advanceToSelfieQuality()
    dispatch(VerificationEvent.SelfieQualityAccepted(operation()))
}

internal fun StateMachineHarness.advanceToDecision(faceStatus: FaceComparisonStatus = FaceComparisonStatus.ACCEPTED) {
    advanceToFaceComparison()
    dispatch(VerificationEvent.FaceComparisonCompleted(operation(), faceStatus))
}

internal fun StateMachineHarness.completeHappyPath() {
    advanceToDecision()
    dispatch(VerificationEvent.DecisionCompleted(operation()))
}
