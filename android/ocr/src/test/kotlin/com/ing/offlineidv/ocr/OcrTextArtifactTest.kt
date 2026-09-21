package com.ing.offlineidv.ocr

import com.ing.offlineidv.core.security.Redaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

public class OcrTextArtifactTest {
    @Test
    public fun `structural block count is available without appearing in redacted text output`() {
        val artifact = OcrTextArtifact(text = SENSITIVE_OCR_TEXT, recognizedTextBlockCount = 3)

        assertEquals(3, artifact.recognizedTextBlockCount)
        assertEquals("OcrTextArtifact(${Redaction.MARKER})", artifact.toString())
        assertFalse(artifact.toString().contains(SENSITIVE_OCR_TEXT))
    }

    @Test
    public fun `negative structural block count is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OcrTextArtifact(text = SENSITIVE_OCR_TEXT, recognizedTextBlockCount = -1)
        }
    }

    private companion object {
        const val SENSITIVE_OCR_TEXT: String = "SENSITIVE_DOCUMENT_CONTENT"
    }
}
