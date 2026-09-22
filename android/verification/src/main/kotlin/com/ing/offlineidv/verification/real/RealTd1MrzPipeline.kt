package com.ing.offlineidv.verification.real

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.OcrFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.mrz.model.MrzNormalizationResult
import com.ing.offlineidv.mrz.model.MrzParseError
import com.ing.offlineidv.mrz.model.MrzParseResult
import com.ing.offlineidv.mrz.model.MrzValidationIssueType
import com.ing.offlineidv.mrz.model.MrzValidationResult
import com.ing.offlineidv.mrz.model.Td1ResidencePermitMrz
import com.ing.offlineidv.mrz.normalization.Td1MrzNormalizer
import com.ing.offlineidv.mrz.parser.Td1MrzParser
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.ocr.mrz.Td1MrzCandidate
import com.ing.offlineidv.ocr.mrz.Td1MrzCandidateExtractor
import com.ing.offlineidv.verification.artifact.SessionArtifactStore
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationArtifactReference
import com.ing.offlineidv.verification.mrz.MrzEvidenceMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Real OCR-to-TD1 bridge retaining only access and printed/chip comparison fields. */
public class RealTd1MrzPipeline(
    private val artifactStore: SessionArtifactStore,
    private val referenceDate: LocalDate,
    private val extractor: Td1MrzCandidateExtractor = Td1MrzCandidateExtractor(),
    private val parser: Td1MrzParser = Td1MrzParser(),
    private val normalizer: Td1MrzNormalizer = Td1MrzNormalizer(),
    private val diagnosticSink: MrzDiagnosticSink = MrzDiagnosticSink.NONE,
) : RealMrzProcessor {
    override fun process(ocrReference: VerificationArtifactReference): IdvResult<RealMrzPipelineResult> {
        val resolved =
            artifactStore.resolve(
                ocrReference,
                VerificationArtifactKind.OCR_RESULT,
                OcrTextArtifact::class.java,
            )
        if (resolved is IdvResult.Failure) {
            recordDiagnostic(emptyDiagnostic(MrzDiagnosticFailureReason.OCR_ARTIFACT_UNAVAILABLE))
            return resolved
        }
        val artifact = (resolved as IdvResult.Success).value
        val observation =
            artifact.useText { recognizedText ->
                OcrObservation(
                    lineCount = countNonBlankLines(recognizedText),
                    candidate = extractor.extract(recognizedText),
                )
            }
        val candidate = observation.candidate
        if (candidate == null) {
            recordDiagnostic(
                MrzDiagnosticSnapshot(
                    ocrSuccessful = true,
                    textBlockCount = artifact.recognizedTextBlockCount,
                    recognizedLineCount = observation.lineCount,
                    candidateLineCount = 0,
                    candidateLengths = emptyList(),
                    format = MrzDiagnosticFormat.UNKNOWN,
                    normalization = MrzDiagnosticStatus.NOT_RUN,
                    parse = MrzDiagnosticStatus.NOT_RUN,
                    validation = MrzDiagnosticStatus.NOT_RUN,
                    failureReason = MrzDiagnosticFailureReason.NO_MRZ_CANDIDATE,
                ),
            )
            return IdvResult.Failure(IdvError.Ocr(OcrFailure.NO_MRZ_CANDIDATE))
        }

        val normalization = candidate.useText(normalizer::normalize)
        val parsed = candidate.useText { parser.parse(it, referenceDate) }
        val validation = validationFrom(parsed)
        recordDiagnostic(
            MrzDiagnosticSnapshot(
                ocrSuccessful = true,
                textBlockCount = artifact.recognizedTextBlockCount,
                recognizedLineCount = observation.lineCount,
                candidateLineCount = candidate.lineLengths.size,
                candidateLengths = candidate.lineLengths,
                format =
                    if (candidate.lineLengths == TD1_LINE_LENGTHS) {
                        MrzDiagnosticFormat.TD1
                    } else {
                        MrzDiagnosticFormat.UNKNOWN
                    },
                normalization = normalization.status(),
                parse = parsed.status(),
                validation =
                    if (validation.isFormatAndCheckDigitValid) {
                        MrzDiagnosticStatus.SUCCESS
                    } else {
                        MrzDiagnosticStatus.FAILURE
                    },
                failureReason = failureReason(normalization, parsed, validation),
            ),
        )

        val printed =
            artifactStore.register(
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                printedDataFrom(parsed),
            )
        if (printed is IdvResult.Failure) return printed
        val accessKey =
            artifactStore.register(
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                accessKeyFrom(parsed),
            )
        if (accessKey is IdvResult.Failure) return accessKey
        return IdvResult.Success(
            RealMrzPipelineResult(
                summary = MrzEvidenceMapper.map(validation),
                printedDataReference = (printed as IdvResult.Success).value,
                accessKeyReference = (accessKey as IdvResult.Success).value,
            ),
        )
    }

    private fun emptyDiagnostic(failureReason: MrzDiagnosticFailureReason): MrzDiagnosticSnapshot =
        MrzDiagnosticSnapshot(
            ocrSuccessful = false,
            textBlockCount = null,
            recognizedLineCount = 0,
            candidateLineCount = 0,
            candidateLengths = emptyList(),
            format = MrzDiagnosticFormat.UNKNOWN,
            normalization = MrzDiagnosticStatus.NOT_RUN,
            parse = MrzDiagnosticStatus.NOT_RUN,
            validation = MrzDiagnosticStatus.NOT_RUN,
            failureReason = failureReason,
        )

    private fun recordDiagnostic(snapshot: MrzDiagnosticSnapshot) {
        try {
            diagnosticSink.record(snapshot)
        } catch (_: RuntimeException) {
            // Device diagnostics must never alter verification behavior.
        }
    }

    private fun countNonBlankLines(text: CharSequence): Int {
        var count = 0
        var currentLineHasContent = false
        text.forEach { character ->
            if (character == '\n' || character == '\r') {
                if (currentLineHasContent) count += 1
                currentLineHasContent = false
            } else if (!character.isWhitespace()) {
                currentLineHasContent = true
            }
        }
        if (currentLineHasContent) count += 1
        return count
    }

    private fun MrzNormalizationResult.status(): MrzDiagnosticStatus =
        when (this) {
            is MrzNormalizationResult.Success -> MrzDiagnosticStatus.SUCCESS
            is MrzNormalizationResult.Failure -> MrzDiagnosticStatus.FAILURE
        }

    private fun MrzParseResult<*>.status(): MrzDiagnosticStatus =
        when (this) {
            is MrzParseResult.Parsed -> MrzDiagnosticStatus.SUCCESS
            is MrzParseResult.Rejected -> MrzDiagnosticStatus.FAILURE
        }

    private fun failureReason(
        normalization: MrzNormalizationResult,
        parsed: MrzParseResult<*>,
        validation: MrzValidationResult,
    ): MrzDiagnosticFailureReason {
        if (normalization is MrzNormalizationResult.Failure) {
            return normalization.issues
                .firstOrNull()
                ?.type
                ?.toDiagnosticFailure()
                ?: MrzDiagnosticFailureReason.MALFORMED_INPUT
        }
        if (parsed is MrzParseResult.Rejected) return parsed.error.toDiagnosticFailure()
        if (validation.isFormatAndCheckDigitValid) return MrzDiagnosticFailureReason.NONE
        return validation.issues
            .firstOrNull()
            ?.type
            ?.toDiagnosticFailure()
            ?: MrzDiagnosticFailureReason.INVALID_DOCUMENT_FIELDS
    }

    private fun MrzParseError.toDiagnosticFailure(): MrzDiagnosticFailureReason =
        when (this) {
            MrzParseError.MALFORMED_INPUT -> MrzDiagnosticFailureReason.MALFORMED_INPUT
            MrzParseError.UNSUPPORTED_CHARACTER -> MrzDiagnosticFailureReason.UNSUPPORTED_CHARACTER
            MrzParseError.UNSUPPORTED_FORMAT -> MrzDiagnosticFailureReason.UNSUPPORTED_FORMAT
            MrzParseError.INVALID_DATE -> MrzDiagnosticFailureReason.INVALID_DATE
            MrzParseError.CHECKSUM_FAILURE -> MrzDiagnosticFailureReason.CHECK_DIGIT_MISMATCH
        }

    private fun MrzValidationIssueType.toDiagnosticFailure(): MrzDiagnosticFailureReason =
        when (this) {
            MrzValidationIssueType.INCORRECT_LINE_COUNT -> MrzDiagnosticFailureReason.INCORRECT_LINE_COUNT

            MrzValidationIssueType.INCORRECT_LINE_LENGTH -> MrzDiagnosticFailureReason.INCORRECT_LINE_LENGTH

            MrzValidationIssueType.UNSUPPORTED_CHARACTER -> MrzDiagnosticFailureReason.UNSUPPORTED_CHARACTER

            MrzValidationIssueType.UNSUPPORTED_TD1_VARIANT,
            MrzValidationIssueType.UNSUPPORTED_TD3_VARIANT,
            -> MrzDiagnosticFailureReason.UNSUPPORTED_FORMAT

            MrzValidationIssueType.INVALID_DATE_FORMAT,
            MrzValidationIssueType.IMPOSSIBLE_DATE,
            MrzValidationIssueType.FUTURE_BIRTH_DATE,
            -> MrzDiagnosticFailureReason.INVALID_DATE

            MrzValidationIssueType.CHECK_DIGIT_MISMATCH,
            MrzValidationIssueType.COMPOSITE_CHECK_DIGIT_MISMATCH,
            MrzValidationIssueType.INVALID_CHECK_DIGIT_CHARACTER,
            -> MrzDiagnosticFailureReason.CHECK_DIGIT_MISMATCH

            MrzValidationIssueType.INVALID_DOCUMENT_CODE,
            MrzValidationIssueType.INVALID_ISSUING_STATE,
            MrzValidationIssueType.INVALID_NATIONALITY,
            MrzValidationIssueType.INVALID_SEX_MARKER,
            MrzValidationIssueType.INVALID_DOCUMENT_NUMBER,
            MrzValidationIssueType.AMBIGUOUS_CHARACTER,
            MrzValidationIssueType.AMBIGUOUS_CENTURY,
            MrzValidationIssueType.MISSING_NAME_SEPARATOR,
            -> MrzDiagnosticFailureReason.INVALID_DOCUMENT_FIELDS
        }

    private fun validationFrom(result: MrzParseResult<*>): MrzValidationResult =
        when (result) {
            is MrzParseResult.Parsed -> result.validation
            is MrzParseResult.Rejected -> result.validation
        }

    private fun printedDataFrom(result: MrzParseResult<Td1ResidencePermitMrz>): PrintedPassportData =
        when (result) {
            is MrzParseResult.Parsed -> {
                val document = result.document
                val dateOfBirth =
                    document.dateOfBirth?.preferredValue?.format(MRZ_DATE)
                        ?: return PrintedPassportData(UNAVAILABLE_ARTIFACT_VALUE)
                val expiryDate =
                    document.expiryDate?.preferredValue?.format(MRZ_DATE)
                        ?: return PrintedPassportData(UNAVAILABLE_ARTIFACT_VALUE)
                PrintedPassportData.fromMrzFields(
                    documentNumber = document.documentNumber,
                    nationality = document.nationality,
                    dateOfBirth = dateOfBirth,
                    expiryDate = expiryDate,
                )
            }

            is MrzParseResult.Rejected -> {
                PrintedPassportData(UNAVAILABLE_ARTIFACT_VALUE)
            }
        }

    private fun accessKeyFrom(result: MrzParseResult<Td1ResidencePermitMrz>): PassportAccessKey =
        when (result) {
            is MrzParseResult.Parsed -> {
                val document = result.document
                val dateOfBirth =
                    document.dateOfBirth?.preferredValue?.format(MRZ_DATE)
                        ?: return PassportAccessKey(UNAVAILABLE_ARTIFACT_VALUE)
                val expiryDate =
                    document.expiryDate?.preferredValue?.format(MRZ_DATE)
                        ?: return PassportAccessKey(UNAVAILABLE_ARTIFACT_VALUE)
                PassportAccessKey.fromMrzFields(
                    documentNumber = document.documentNumber,
                    dateOfBirth = dateOfBirth,
                    expiryDate = expiryDate,
                )
            }

            is MrzParseResult.Rejected -> {
                PassportAccessKey(UNAVAILABLE_ARTIFACT_VALUE)
            }
        }

    private companion object {
        const val UNAVAILABLE_ARTIFACT_VALUE: String = ""
        val MRZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
        val TD1_LINE_LENGTHS: List<Int> = listOf(30, 30, 30)
    }

    private data class OcrObservation(
        val lineCount: Int,
        val candidate: Td1MrzCandidate?,
    )
}
