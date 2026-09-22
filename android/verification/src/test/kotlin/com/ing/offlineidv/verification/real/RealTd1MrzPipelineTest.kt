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

public class RealTd1MrzPipelineTest {
    @Test
    public fun `three-line residence permit candidate produces valid document evidence`() {
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
            assertEquals(3, recognizedLineCount)
            assertEquals(3, candidateLineCount)
            assertEquals(listOf(30, 30, 30), candidateLengths)
            assertEquals(MrzDiagnosticFormat.TD1, format)
            assertEquals(MrzDiagnosticStatus.SUCCESS, normalization)
            assertEquals(MrzDiagnosticStatus.SUCCESS, parse)
            assertEquals(MrzDiagnosticStatus.SUCCESS, validation)
            assertEquals(MrzDiagnosticFailureReason.NONE, failureReason)
        }
    }

    @Test
    public fun `TD1 checksum failure remains visible to verification evidence`() {
        val lines = validTd1().lines().toMutableList()
        lines[1] = lines[1].replaceRange(29, 30, if (lines[1][29] == '9') "0" else "9")
        val fixture = fixture(lines.joinToString("\n"))

        val result = fixture.pipeline.process(fixture.ocrReference).success()

        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in result.summary.evidence)
        assertEquals(MrzDiagnosticFailureReason.CHECK_DIGIT_MISMATCH, fixture.diagnostics.single().failureReason)
    }

    @Test
    public fun `non-MRZ OCR fails without exposing recognized text`() {
        val fixture = fixture("Netherlands residence card")

        val result = fixture.pipeline.process(fixture.ocrReference) as IdvResult.Failure

        assertEquals("ocr.no_mrz_candidate", result.error.code)
        assertEquals(MrzDiagnosticFailureReason.NO_MRZ_CANDIDATE, fixture.diagnostics.single().failureReason)
        assertTrue(
            !fixture.diagnostics
                .single()
                .toString()
                .contains("residence card", ignoreCase = true),
        )
    }

    private fun fixture(text: String = validTd1()): Fixture {
        val session = (IdvSessionId.parse("atlas_real_td1_test") as IdvResult.Success).value
        val store = SessionArtifactStore(session)
        val diagnostics = mutableListOf<MrzDiagnosticSnapshot>()
        val reference =
            store
                .register(
                    VerificationArtifactKind.OCR_RESULT,
                    OcrTextArtifact(text, recognizedTextBlockCount = 3),
                ).success()
        return Fixture(
            store = store,
            ocrReference = reference,
            pipeline =
                RealTd1MrzPipeline(
                    artifactStore = store,
                    referenceDate = LocalDate.of(2026, 9, 21),
                    diagnosticSink = MrzDiagnosticSink(diagnostics::add),
                ),
            diagnostics = diagnostics,
        )
    }

    private fun validTd1(): String {
        val documentNumber = "X12T34567"
        val birthDate = "900101"
        val expiryDate = "300101"
        val line1 = "I<NLD" + documentNumber + checkDigit(documentNumber) + "".padEnd(15, '<')
        val line2WithoutComposite =
            birthDate + checkDigit(birthDate) + "F" + expiryDate + checkDigit(expiryDate) +
                "UTO" + "".padEnd(11, '<')
        val compositeSource =
            line1.substring(5, 30) +
                line2WithoutComposite.substring(0, 7) +
                line2WithoutComposite.substring(8, 15) +
                line2WithoutComposite.substring(18, 29)
        val line2 = line2WithoutComposite + checkDigit(compositeSource)
        val line3 = "SYNTHETIC<<RESIDENT<TEST".padEnd(30, '<')
        return "$line1\n$line2\n$line3"
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
        val pipeline: RealTd1MrzPipeline,
        val diagnostics: List<MrzDiagnosticSnapshot>,
    )
}
