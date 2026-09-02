package com.ing.offlineidv.verification.real

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.mrz.model.Td3PassportMrz
import com.ing.offlineidv.mrz.parser.Td3MrzParser
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.ocr.mrz.MrzCandidateExtractor
import com.ing.offlineidv.verification.artifact.SessionArtifactStore
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.mrz.MrzEvidenceMapper
import com.ing.offlineidv.verification.mrz.MrzVerificationSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Safe result from candidate extraction through the existing parser and evidence mapper. */
public data class RealMrzPipelineResult(
    public val summary: MrzVerificationSummary,
    public val printedDataReference: VerificationArtifactReference,
    public val accessKeyReference: VerificationArtifactReference,
)

/** Real OCR-to-MRZ bridge that delegates all parsing and validation to Milestone 2. */
public class RealMrzPipeline(
    private val artifactStore: SessionArtifactStore,
    private val referenceDate: LocalDate,
    private val extractor: MrzCandidateExtractor = MrzCandidateExtractor(),
    private val parser: Td3MrzParser = Td3MrzParser(),
) {
    public fun process(ocrReference: VerificationArtifactReference): IdvResult<RealMrzPipelineResult> {
        val resolved =
            artifactStore.resolve(
                ocrReference,
                VerificationArtifactKind.OCR_RESULT,
                OcrTextArtifact::class.java,
            )
        if (resolved is IdvResult.Failure) return resolved
        val candidate =
            (resolved as IdvResult.Success).value.useText(extractor::extract)
                ?: return IdvResult.Failure(IdvError.Ocr(OcrFailure.NO_MRZ_CANDIDATE))
        val parsed = candidate.useText { parser.parse(it, referenceDate) }
        val printedValue = candidate.useText { it.toString() }
        val printed =
            artifactStore.register(
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                PrintedPassportData(printedValue),
            )
        if (printed is IdvResult.Failure) return printed
        val accessKey =
            artifactStore.register(
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                PassportAccessKey(accessKeyFrom(parsed)),
            )
        if (accessKey is IdvResult.Failure) return accessKey
        return IdvResult.Success(
            RealMrzPipelineResult(
                summary = MrzEvidenceMapper.map(validationFrom(parsed)),
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

    private fun accessKeyFrom(result: MrzParseResult<Td3PassportMrz>): String =
        when (result) {
            is MrzParseResult.Parsed -> {
                val document = result.document
                document.documentNumber +
                    document.dateOfBirth
                        ?.preferredValue
                        ?.format(MRZ_DATE)
                        .orEmpty() +
                    document.expiryDate
                        ?.preferredValue
                        ?.format(MRZ_DATE)
                        .orEmpty()
            }

            is MrzParseResult.Rejected -> {
                "unavailable"
            }
        }

    private companion object {
        val MRZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
