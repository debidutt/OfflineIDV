package com.ing.offlineidv.verification.real

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrTextArtifact
import com.ing.offlineidv.verification.artifact.SessionArtifactStore
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

public class RealMrzPipelineTest {
    @Test
    public fun `clean OCR candidate is validated by existing parser`() {
        val fixture = fixture()
        val result = fixture.pipeline.process(fixture.ocrReference).success()

        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in result.summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in result.summary.evidence)
        assertEquals(3, fixture.store.size)
        val printed =
            fixture.store
                .resolve(
                    result.printedDataReference,
                    VerificationArtifactKind.MRZ_PRINTED_DATA,
                    PrintedPassportData::class.java,
                ).success()
        val accessKey =
            fixture.store
                .resolve(
                    result.accessKeyReference,
                    VerificationArtifactKind.MRZ_ACCESS_KEY,
                    PassportAccessKey::class.java,
                ).success()
        assertEquals(24, printed.useValue(String::length))
        assertEquals(21, accessKey.useValue(String::length))
        assertTrue(printed.toString().contains("[REDACTED]"))
        assertTrue(accessKey.toString().contains("[REDACTED]"))
        with(fixture.diagnostics.single()) {
            assertTrue(ocrSuccessful)
            assertEquals(2, textBlockCount)
            assertEquals(2, recognizedLineCount)
            assertEquals(2, candidateLineCount)
            assertEquals(listOf(44, 44), candidateLengths)
            assertEquals(MrzDiagnosticFormat.TD3, format)
            assertEquals(MrzDiagnosticStatus.SUCCESS, normalization)
            assertEquals(MrzDiagnosticStatus.SUCCESS, parse)
            assertEquals(MrzDiagnosticStatus.SUCCESS, validation)
            assertEquals(MrzDiagnosticFailureReason.NONE, failureReason)
        }
    }

    @Test
    public fun `candidate extractor does not hide parser checksum rejection`() {
        val fixture = fixture(validMrz().replaceRange(54, 55, "0"))
        val result = fixture.pipeline.process(fixture.ocrReference).success()

        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in result.summary.evidence)
        with(fixture.diagnostics.single()) {
            assertEquals(MrzDiagnosticStatus.SUCCESS, parse)
            assertEquals(MrzDiagnosticStatus.FAILURE, validation)
            assertEquals(MrzDiagnosticFailureReason.CHECK_DIGIT_MISMATCH, failureReason)
        }
    }

    @Test
    public fun `unrelated OCR fails as no candidate without raw text`() {
        val fixture = fixture("boarding pass only")
        val result = fixture.pipeline.process(fixture.ocrReference) as IdvResult.Failure

        assertEquals("ocr.no_mrz_candidate", result.error.code)
        assertTrue(result.error.toString().contains("ocr.no_mrz_candidate"))
        with(fixture.diagnostics.single()) {
            assertTrue(ocrSuccessful)
            assertEquals(1, recognizedLineCount)
            assertEquals(0, candidateLineCount)
            assertEquals(emptyList<Int>(), candidateLengths)
            assertEquals(MrzDiagnosticFormat.UNKNOWN, format)
            assertEquals(MrzDiagnosticStatus.NOT_RUN, normalization)
            assertEquals(MrzDiagnosticStatus.NOT_RUN, parse)
            assertEquals(MrzDiagnosticStatus.NOT_RUN, validation)
            assertEquals(MrzDiagnosticFailureReason.NO_MRZ_CANDIDATE, failureReason)
        }
    }

    @Test
    public fun `candidate lengths identify normalization failure without exposing text`() {
        val lines = validMrz().lines()
        val fixture = fixture("${lines.first().dropLast(1)}\n${lines.last()}")

        fixture.pipeline.process(fixture.ocrReference).success()

        with(fixture.diagnostics.single()) {
            assertEquals(listOf(43, 44), candidateLengths)
            assertEquals(MrzDiagnosticFormat.UNKNOWN, format)
            assertEquals(MrzDiagnosticStatus.FAILURE, normalization)
            assertEquals(MrzDiagnosticStatus.FAILURE, parse)
            assertEquals(MrzDiagnosticStatus.FAILURE, validation)
            assertEquals(MrzDiagnosticFailureReason.INCORRECT_LINE_LENGTH, failureReason)
            assertTrue(toString().contains("candidateLengths=[43, 44]"))
            assertTrue(!toString().contains("SYNTHETIC"))
            assertTrue(!toString().contains("A12B34567"))
        }
    }

    @Test
    public fun `diagnostic sink failure cannot change MRZ behavior`() {
        val fixture = fixture(diagnosticSink = MrzDiagnosticSink { error("diagnostic failure") })

        val result = fixture.pipeline.process(fixture.ocrReference).success()

        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in result.summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in result.summary.evidence)
    }

    private fun fixture(
        text: String = validMrz(),
        diagnosticSink: MrzDiagnosticSink? = null,
    ): Fixture {
        val session = (IdvSessionId.parse("atlas_real_mrz_test") as IdvResult.Success).value
        val store = SessionArtifactStore(session)
        val diagnostics = mutableListOf<MrzDiagnosticSnapshot>()
        val reference =
            store
                .register(
                    VerificationArtifactKind.OCR_RESULT,
                    OcrTextArtifact(text, recognizedTextBlockCount = 2),
                ).success()
        return Fixture(
            store,
            reference,
            RealMrzPipeline(
                artifactStore = store,
                referenceDate = LocalDate.of(2026, 8, 31),
                diagnosticSink = diagnosticSink ?: MrzDiagnosticSink(diagnostics::add),
            ),
            diagnostics,
        )
    }

    private fun validMrz(): String {
        val documentNumber = "A12B34567"
        val birthDate = "900101"
        val expiryDate = "301231"
        val optionalData = "SYNTHETIC1<<<<"
        val documentDigit = checkDigit(documentNumber)
        val birthDigit = checkDigit(birthDate)
        val expiryDigit = checkDigit(expiryDate)
        val optionalDigit = checkDigit(optionalData)
        val composite = documentNumber + documentDigit + birthDate + birthDigit + expiryDate + expiryDigit + optionalData + optionalDigit
        val line1 = "P<UTO" + "TESTER<<SYNTHETIC<ATLAS".padEnd(39, '<')
        val line2 =
            documentNumber + documentDigit + "UTO" + birthDate + birthDigit + "F" + expiryDate + expiryDigit +
                optionalData + optionalDigit + checkDigit(composite)
        return "$line1\n$line2"
    }

    private fun checkDigit(value: CharSequence): Char {
        val weights = intArrayOf(7, 3, 1)
        val sum =
            value
                .mapIndexed { index, character ->
                    val encoded =
                        when (character) {
                            in '0'..'9' -> character - '0'
                            in 'A'..'Z' -> character - 'A' + 10
                            else -> 0
                        }
                    encoded * weights[index % weights.size]
                }.sum()
        return ('0'.code + sum % 10).toChar()
    }

    private fun <T> IdvResult<T>.success(): T = (this as IdvResult.Success).value

    private data class Fixture(
        val store: SessionArtifactStore,
        val ocrReference: com.ing.offlineidv.verification.model.VerificationArtifactReference,
        val pipeline: RealMrzPipeline,
        val diagnostics: List<MrzDiagnosticSnapshot>,
    )
}
