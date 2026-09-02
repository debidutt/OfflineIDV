package com.ing.offlineidv.verification.real

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
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
    }

    @Test
    public fun `candidate extractor does not hide parser checksum rejection`() {
        val fixture = fixture(validMrz().replaceRange(54, 55, "0"))
        val result = fixture.pipeline.process(fixture.ocrReference).success()

        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in result.summary.evidence)
    }

    @Test
    public fun `unrelated OCR fails as no candidate without raw text`() {
        val fixture = fixture("boarding pass only")
        val result = fixture.pipeline.process(fixture.ocrReference) as IdvResult.Failure

        assertEquals("ocr.no_mrz_candidate", result.error.code)
        assertTrue(result.error.toString().contains("ocr.no_mrz_candidate"))
    }

    private fun fixture(text: String = validMrz()): Fixture {
        val session = (IdvSessionId.parse("atlas_real_mrz_test") as IdvResult.Success).value
        val store = SessionArtifactStore(session)
        val reference = store.register(VerificationArtifactKind.OCR_RESULT, OcrTextArtifact(text)).success()
        return Fixture(store, reference, RealMrzPipeline(store, LocalDate.of(2026, 8, 31)))
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
    )
}
