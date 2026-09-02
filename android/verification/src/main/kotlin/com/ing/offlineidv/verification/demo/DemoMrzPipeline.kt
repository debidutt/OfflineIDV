package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.mrz.parser.Td3MrzParser
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.mrz.MrzEvidenceMapper
import com.ing.offlineidv.verification.mrz.MrzVerificationSummary
import java.time.LocalDate

/** Safe result of exercising the real MRZ parser against a registry-owned OCR artifact. */
public data class DemoMrzPipelineResult(
    public val summary: MrzVerificationSummary,
    public val printedDataReference: VerificationArtifactReference,
    public val accessKeyReference: VerificationArtifactReference,
)

/** Demo adapter that invokes the real Milestone 2 parser and Milestone 3 evidence mapper. */
public class DemoMrzPipeline(
    private val registry: DemoArtifactRegistry,
    private val referenceDate: LocalDate,
    private val parser: Td3MrzParser = Td3MrzParser(),
) {
    /** Parses the OCR artifact without exposing text or parsed identity data to verification state. */
    public fun process(ocrReference: VerificationArtifactReference): IdvResult<DemoMrzPipelineResult> {
        val resolved =
            registry.resolve(
                ocrReference,
                VerificationArtifactKind.OCR_RESULT,
                OcrTextArtifact::class.java,
            )
        if (resolved is IdvResult.Failure) return resolved
        val ocr = (resolved as IdvResult.Success).value
        val parsed = ocr.useText { text -> parser.parse(text, referenceDate) }
        val validation = validationFrom(parsed)
        val printed =
            registry.register(
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                PrintedPassportData("synthetic-printed-passport-data"),
            )
        if (printed is IdvResult.Failure) return printed
        val accessKey =
            registry.register(
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                PassportAccessKey("synthetic-passport-access-key"),
            )
        if (accessKey is IdvResult.Failure) return accessKey
        return IdvResult.Success(
            DemoMrzPipelineResult(
                summary = MrzEvidenceMapper.map(validation),
                printedDataReference = (printed as IdvResult.Success).value,
                accessKeyReference = (accessKey as IdvResult.Success).value,
            ),
        )
    }

    private fun validationFrom(result: MrzParseResult<*>): MrzValidationResult =
        when (result) {
            is MrzParseResult.Parsed -> result.validation
            is MrzParseResult.Rejected -> result.validation
        }
}
