package com.ing.offlineidv.ocr.demo

import com.ing.offlineidv.ocr.OcrDocumentInput
import com.ing.offlineidv.ocr.OcrEngineResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeOcrEngineTest {
    @Test
    public fun `valid TD3 output is deterministic`() {
        val first = recognized(DemoOcrBehavior.VALID_TD3)
        val second = recognized(DemoOcrBehavior.VALID_TD3)

        assertEquals(first, second)
    }

    @Test
    public fun `invalid MRZ fixture differs from valid fixture`() {
        assertNotEquals(text(DemoOcrBehavior.VALID_TD3), text(DemoOcrBehavior.INVALID_MRZ))
    }

    @Test
    public fun `ambiguity fixture contains a synthetic OCR ambiguity`() {
        assertTrue(text(DemoOcrBehavior.AMBIGUOUS_MRZ).contains('O'))
    }

    @Test
    public fun `expired fixture remains a two-line TD3 candidate`() {
        val lines = text(DemoOcrBehavior.EXPIRED_DOCUMENT).split('\n')

        assertEquals(listOf(44, 44), lines.map(String::length))
    }

    @Test
    public fun `technical failure is separate from recognized text`() {
        val result = FakeOcrEngine(DemoOcrBehavior.TECHNICAL_FAILURE).recognize(OcrDocumentInput(1))

        assertEquals("ocr.recognition_failed", (result as OcrEngineResult.Failed).error.code)
    }

    @Test
    public fun `OCR artifact string never renders text`() {
        val artifact = recognized(DemoOcrBehavior.VALID_TD3)
        val rendered = artifact.toString()

        assertTrue(rendered.contains("[REDACTED]"))
        assertFalse(rendered.contains("TESTER"))
    }

    private fun recognized(behavior: DemoOcrBehavior) =
        (FakeOcrEngine(behavior).recognize(OcrDocumentInput(1)) as OcrEngineResult.Recognized).artifact

    private fun text(behavior: DemoOcrBehavior): String = recognized(behavior).useText { it.toString() }
}
