package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PrintedPassportData
import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngineResult
import com.ing.offlineidv.ocr.demo.DemoOcrBehavior
import com.ing.offlineidv.ocr.demo.FakeOcrEngine
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import com.ing.offlineidv.verification.model.VerificationEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class DemoMrzPipelineTest {
    @Test
    public fun `valid fake OCR is validated by real MRZ pipeline`() {
        val result = process(DemoOcrBehavior.VALID_TD3)

        assertTrue(VerificationEvidence.MRZ_STRUCTURE_VALID in result.summary.evidence)
        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_VALID in result.summary.evidence)
    }

    @Test
    public fun `invalid check digit becomes MRZ evidence rather than fake policy`() {
        val result = process(DemoOcrBehavior.INVALID_MRZ)

        assertTrue(VerificationEvidence.MRZ_CHECK_DIGITS_INVALID in result.summary.evidence)
    }

    @Test
    public fun `OCR ambiguity is surfaced by the real parser mapper`() {
        val result = process(DemoOcrBehavior.AMBIGUOUS_MRZ)

        assertTrue(VerificationEvidence.MRZ_CHARACTER_AMBIGUITY in result.summary.evidence)
    }

    @Test
    public fun `expired document is evaluated against fixed reference date`() {
        val result = process(DemoOcrBehavior.EXPIRED_DOCUMENT)

        assertTrue(VerificationEvidence.DOCUMENT_EXPIRED in result.summary.evidence)
    }

    @Test
    public fun `expired OCR produces identical real MRZ evidence for every policy evaluation`() {
        val first = process(DemoOcrBehavior.EXPIRED_DOCUMENT)
        val second = process(DemoOcrBehavior.EXPIRED_DOCUMENT)

        assertEquals(first.summary, second.summary)
    }

    @Test
    public fun `pipeline registers typed printed data and access key artifacts`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        val result = process(DemoOcrBehavior.VALID_TD3, registry)

        assertTrue(
            registry.resolve(
                result.printedDataReference,
                VerificationArtifactKind.MRZ_PRINTED_DATA,
                PrintedPassportData::class.java,
            ) is IdvResult.Success,
        )
        assertTrue(
            registry.resolve(
                result.accessKeyReference,
                VerificationArtifactKind.MRZ_ACCESS_KEY,
                PassportAccessKey::class.java,
            ) is IdvResult.Success,
        )
        assertEquals(3, registry.size)
    }

    private fun process(
        behavior: DemoOcrBehavior,
        registry: DemoArtifactRegistry = DemoArtifactRegistry(VerificationFixtures.sessionId),
    ): DemoMrzPipelineResult {
        val recognized =
            FakeOcrEngine(behavior).recognize(OcrDocumentInput(1)) as OcrEngineResult.Recognized
        val reference =
            success(
                registry.register(
                    VerificationArtifactKind.OCR_RESULT,
                    recognized.artifact,
                ),
            )
        return success(
            DemoMrzPipeline(registry, DemoScenarioDefinition.REFERENCE_DATE).process(reference),
        )
    }

    private fun <T> success(result: IdvResult<T>): T = (result as IdvResult.Success).value
}
