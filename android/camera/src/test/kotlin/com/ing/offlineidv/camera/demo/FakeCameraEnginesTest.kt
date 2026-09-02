package com.ing.offlineidv.camera.demo

import com.ing.offlineidv.camera.DocumentCaptureRequest
import com.ing.offlineidv.camera.DocumentCaptureResult
import com.ing.offlineidv.camera.DocumentQualityResult
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeCameraEnginesTest {
    @Test
    public fun `capture success is deterministic across fresh engines`() {
        val first = FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.SUCCEED).capture(request)
        val second = FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.SUCCEED).capture(request)

        assertEquals((first as DocumentCaptureResult.Captured).artifact, (second as DocumentCaptureResult.Captured).artifact)
    }

    @Test
    public fun `capture technical failure is a safe camera observation`() {
        val result = FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.TECHNICAL_FAILURE).capture(request)

        assertEquals("camera.capture_failed", (result as DocumentCaptureResult.Failed).error.code)
    }

    @Test
    public fun `quality can be accepted`() {
        val result = FakeDocumentQualityEngine(DemoDocumentQualityBehavior.ACCEPT).evaluate(document())

        assertEquals(DocumentQualityResult.Accepted, result)
    }

    @Test
    public fun `quality rejection once is followed by acceptance`() {
        val engine = FakeDocumentQualityEngine(DemoDocumentQualityBehavior.REJECT_ONCE_THEN_ACCEPT)

        assertEquals(DocumentQualityResult.Rejected, engine.evaluate(document()))
        assertEquals(DocumentQualityResult.Accepted, engine.evaluate(document()))
    }

    @Test
    public fun `quality can remain rejected without choosing retry`() {
        val engine = FakeDocumentQualityEngine(DemoDocumentQualityBehavior.ALWAYS_REJECT)

        assertEquals(DocumentQualityResult.Rejected, engine.evaluate(document()))
        assertEquals(DocumentQualityResult.Rejected, engine.evaluate(document()))
    }

    @Test
    public fun `reset restores deterministic call sequence`() {
        val engine = FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.SUCCEED)
        val first = (engine.capture(request) as DocumentCaptureResult.Captured).artifact
        engine.capture(request)

        engine.reset()

        assertEquals(first, (engine.capture(request) as DocumentCaptureResult.Captured).artifact)
    }

    @Test
    public fun `document artifact string is redacted`() {
        val rendered = document().toString()

        assertTrue(rendered.contains("[REDACTED]"))
        assertFalse(rendered.contains("sourceToken=1"))
    }

    private fun document() =
        (FakeDocumentCaptureEngine(DemoDocumentCaptureBehavior.SUCCEED).capture(request) as DocumentCaptureResult.Captured).artifact

    private companion object {
        val request =
            DocumentCaptureRequest(
                (IdvSessionId.parse("camera-demo-session") as IdvResult.Success).value,
            )
    }
}
