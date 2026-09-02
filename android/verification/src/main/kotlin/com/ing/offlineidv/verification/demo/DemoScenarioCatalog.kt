package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.demo.DemoDocumentCaptureBehavior
import com.ing.offlineidv.camera.demo.DemoDocumentQualityBehavior
import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.face.demo.DemoFaceMatchBehavior
import com.ing.offlineidv.face.demo.DemoSelfieCaptureBehavior
import com.ing.offlineidv.face.demo.DemoSelfieQualityBehavior
import com.ing.offlineidv.nfc.PassiveAuthenticationObservation
import com.ing.offlineidv.nfc.demo.DemoChipValidationBehavior
import com.ing.offlineidv.nfc.demo.DemoNfcReadBehavior
import com.ing.offlineidv.nfc.demo.DemoPrintedChipComparisonBehavior
import com.ing.offlineidv.ocr.demo.DemoOcrBehavior
import com.ing.offlineidv.verification.model.VerificationCapability
import java.time.LocalDate

/** Host or scheduler stimulus that is deliberately separate from engine observations. */
public enum class DemoLifecycleTrigger {
    NONE,
    CANCEL_AFTER_INITIALIZATION,
    EXPIRE_AFTER_INITIALIZATION,
}

/**
 * Immutable external behavior for one synthetic scenario.
 *
 * This model deliberately contains no expected outcome, retry instruction, next step, or state.
 */
public class DemoScenarioDefinition(
    public val documentCapture: DemoDocumentCaptureBehavior = DemoDocumentCaptureBehavior.SUCCEED,
    public val documentQuality: DemoDocumentQualityBehavior = DemoDocumentQualityBehavior.ACCEPT,
    public val ocr: DemoOcrBehavior = DemoOcrBehavior.VALID_TD3,
    public val nfcRead: DemoNfcReadBehavior = DemoNfcReadBehavior.SUCCESS,
    public val chipValidation: DemoChipValidationBehavior = DemoChipValidationBehavior(),
    public val printedChipComparison: DemoPrintedChipComparisonBehavior = DemoPrintedChipComparisonBehavior.MATCH,
    public val selfieCapture: DemoSelfieCaptureBehavior = DemoSelfieCaptureBehavior.SUCCEED,
    public val selfieQuality: DemoSelfieQualityBehavior = DemoSelfieQualityBehavior.ACCEPT,
    public val faceMatch: DemoFaceMatchBehavior = DemoFaceMatchBehavior.ACCEPT,
    capabilities: Set<VerificationCapability> = VerificationCapability.entries.toSet(),
    public val lifecycleTrigger: DemoLifecycleTrigger = DemoLifecycleTrigger.NONE,
    public val mrzReferenceDate: LocalDate = REFERENCE_DATE,
) {
    public val capabilities: Set<VerificationCapability> = capabilities.toSet()

    public companion object {
        /** Fixed date used by all synthetic MRZ scenarios. */
        public val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 8, 30)
    }
}

/** Central catalog of deterministic external behavior. */
public object DemoScenarioCatalog {
    /** Returns a fresh immutable definition for [scenario]. */
    public fun definitionFor(scenario: DemoScenario): DemoScenarioDefinition =
        when (scenario) {
            DemoScenario.SUCCESS -> {
                DemoScenarioDefinition()
            }

            DemoScenario.INVALID_MRZ -> {
                DemoScenarioDefinition(ocr = DemoOcrBehavior.INVALID_MRZ)
            }

            DemoScenario.MRZ_AMBIGUITY -> {
                DemoScenarioDefinition(ocr = DemoOcrBehavior.AMBIGUOUS_MRZ)
            }

            DemoScenario.EXPIRED_DOCUMENT -> {
                DemoScenarioDefinition(ocr = DemoOcrBehavior.EXPIRED_DOCUMENT)
            }

            DemoScenario.NFC_TIMEOUT_THEN_SUCCESS -> {
                DemoScenarioDefinition(nfcRead = DemoNfcReadBehavior.TIMEOUT_THEN_SUCCESS)
            }

            DemoScenario.NFC_TIMEOUT_EXHAUSTED,
            DemoScenario.NFC_TIMEOUT,
            -> {
                DemoScenarioDefinition(nfcRead = DemoNfcReadBehavior.ALWAYS_TIMEOUT)
            }

            DemoScenario.NFC_UNAVAILABLE -> {
                DemoScenarioDefinition(
                    capabilities = VerificationCapability.entries.toSet() - VerificationCapability.NFC,
                )
            }

            DemoScenario.CHIP_MISMATCH,
            DemoScenario.CHIP_DATA_MISMATCH,
            -> {
                DemoScenarioDefinition(
                    printedChipComparison = DemoPrintedChipComparisonBehavior.MISMATCH,
                )
            }

            DemoScenario.PASSIVE_AUTH_FAILURE -> {
                DemoScenarioDefinition(
                    chipValidation =
                        DemoChipValidationBehavior(
                            passiveAuthentication = PassiveAuthenticationObservation.FAILED,
                        ),
                )
            }

            DemoScenario.SELFIE_QUALITY_FAILURE -> {
                DemoScenarioDefinition(selfieQuality = DemoSelfieQualityBehavior.ALWAYS_REJECT)
            }

            DemoScenario.FACE_MISMATCH -> {
                DemoScenarioDefinition(faceMatch = DemoFaceMatchBehavior.REJECT)
            }

            DemoScenario.FACE_INCONCLUSIVE -> {
                DemoScenarioDefinition(faceMatch = DemoFaceMatchBehavior.INCONCLUSIVE)
            }

            DemoScenario.TECHNICAL_FAILURE -> {
                DemoScenarioDefinition(ocr = DemoOcrBehavior.TECHNICAL_FAILURE)
            }

            DemoScenario.USER_CANCELLED,
            DemoScenario.CANCELLATION,
            -> {
                DemoScenarioDefinition(lifecycleTrigger = DemoLifecycleTrigger.CANCEL_AFTER_INITIALIZATION)
            }

            DemoScenario.SESSION_EXPIRED -> {
                DemoScenarioDefinition(lifecycleTrigger = DemoLifecycleTrigger.EXPIRE_AFTER_INITIALIZATION)
            }
        }
}
