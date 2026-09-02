package com.ing.offlineidv.verification.fixtures

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.core.result.getOrNull
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.mrz.model.CheckDigitResult
import com.ing.offlineidv.mrz.model.CheckDigitStatus
import com.ing.offlineidv.mrz.model.MrzExpiryStatus
import com.ing.offlineidv.mrz.model.MrzField
import com.ing.offlineidv.mrz.model.MrzValidationIssue
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.verification.DefaultVerificationStateMachine
import com.ing.offlineidv.verification.model.ChipValidationSummary
import com.ing.offlineidv.verification.model.PassiveAuthenticationStatus
import com.ing.offlineidv.verification.model.TransitionResult
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState
import com.ing.offlineidv.verification.mrz.MrzEvidenceMapper
import com.ing.offlineidv.verification.mrz.MrzVerificationSummary

internal object VerificationFixtures {
    val sessionId: IdvSessionId = checkNotNull(IdvSessionId.parse("session_fixture_0001").getOrNull())
    val otherSessionId: IdvSessionId = checkNotNull(IdvSessionId.parse("session_fixture_0002").getOrNull())

    val documentReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.DOCUMENT_CAPTURE, "document_ref_0001")
    val ocrReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.OCR_RESULT, "ocr_result_ref_0001")
    val printedReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.MRZ_PRINTED_DATA, "printed_ref_0001")
    val accessKeyReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.MRZ_ACCESS_KEY, "access_key_ref_0001")
    val chipReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.NFC_CHIP_DATA, "chip_data_ref_0001")
    val portraitReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.CHIP_PORTRAIT, "portrait_ref_0001")
    val selfieReference: VerificationArtifactReference =
        reference(VerificationArtifactKind.SELFIE_CAPTURE, "selfie_ref_0001")

    val safeTechnicalError: IdvError = IdvError.Verification(VerificationFailure.TECHNICAL_FAILURE)

    val validMrzSummary: MrzVerificationSummary = MrzEvidenceMapper.map(validation())

    val validChipSummary: ChipValidationSummary =
        ChipValidationSummary(
            dg1Available = true,
            dg2Available = true,
            passiveAuthentication = PassiveAuthenticationStatus.VALID,
            portraitReference = portraitReference,
        )

    fun validation(
        structurallyValid: Boolean = true,
        checkDigitStatus: CheckDigitStatus = CheckDigitStatus.VALID,
        issues: List<MrzValidationIssue> = emptyList(),
        expiryStatus: MrzExpiryStatus = MrzExpiryStatus.VALID,
    ): MrzValidationResult =
        MrzValidationResult(
            structurallyValid = structurallyValid,
            checkDigits =
                listOf(
                    MrzField.DOCUMENT_NUMBER,
                    MrzField.DATE_OF_BIRTH,
                    MrzField.EXPIRY_DATE,
                    MrzField.OPTIONAL_DATA,
                    MrzField.COMPOSITE_CHECK_DIGIT,
                ).associateWith { field -> CheckDigitResult(field, checkDigitStatus) },
            issues = issues,
            corrections = emptyList(),
            normalizationChanges = emptyList(),
            expiryStatus = expiryStatus,
            confidence = com.ing.offlineidv.mrz.model.MrzConfidence.HIGH,
        )

    fun issue(type: MrzValidationIssueType): MrzValidationIssue = MrzValidationIssue(type)

    private fun reference(
        kind: VerificationArtifactKind,
        value: String,
    ): VerificationArtifactReference = checkNotNull(VerificationArtifactReference.parse(kind, value).getOrNull())
}

internal class StateMachineHarness(
    var state: VerificationState = com.ing.offlineidv.verification.model.Idle,
    val context: VerificationContext = VerificationContext(),
) {
    val effects: MutableList<VerificationEffect> = mutableListOf()
    val results: MutableList<TransitionResult> = mutableListOf()

    fun dispatch(event: VerificationEvent): TransitionResult {
        val result = DefaultVerificationStateMachine.transition(state, event, context)
        state = result.state
        effects += result.effects
        results += result
        return result
    }

    fun operation(): com.ing.offlineidv.verification.model.VerificationOperationToken =
        checkNotNull((state as com.ing.offlineidv.verification.model.ActiveVerificationState).progress.activeOperation)
}
