package com.ing.offlineidv.face.demo

import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.face.DocumentPortraitArtifact
import com.ing.offlineidv.face.FaceMatchResult
import com.ing.offlineidv.face.SelfieArtifact
import com.ing.offlineidv.face.SelfieCaptureRequest
import com.ing.offlineidv.face.SelfieCaptureResult
import com.ing.offlineidv.face.SelfieQualityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class FakeFaceEnginesTest {
    @Test
    public fun `selfie capture is deterministic across fresh engines`() {
        val first = FakeSelfieCaptureEngine(DemoSelfieCaptureBehavior.SUCCEED).capture(request)
        val second = FakeSelfieCaptureEngine(DemoSelfieCaptureBehavior.SUCCEED).capture(request)

        assertEquals((first as SelfieCaptureResult.Captured).artifact, (second as SelfieCaptureResult.Captured).artifact)
    }

    @Test
    public fun `selfie capture failure remains a safe observation`() {
        val result = FakeSelfieCaptureEngine(DemoSelfieCaptureBehavior.TECHNICAL_FAILURE).capture(request)

        assertEquals("face.no_face", (result as SelfieCaptureResult.Failed).error.code)
    }

    @Test
    public fun `selfie quality rejection once is followed by acceptance`() {
        val engine = FakeSelfieQualityEngine(DemoSelfieQualityBehavior.REJECT_ONCE_THEN_ACCEPT)

        assertEquals(SelfieQualityResult.Rejected, engine.evaluate(selfie))
        assertEquals(SelfieQualityResult.Accepted, engine.evaluate(selfie))
    }

    @Test
    public fun `selfie quality can remain rejected without choosing retry`() {
        val engine = FakeSelfieQualityEngine(DemoSelfieQualityBehavior.ALWAYS_REJECT)

        repeat(3) { assertEquals(SelfieQualityResult.Rejected, engine.evaluate(selfie)) }
    }

    @Test
    public fun `face engine reports configured observations`() {
        assertEquals(FaceMatchResult.Accepted, compare(DemoFaceMatchBehavior.ACCEPT))
        assertEquals(FaceMatchResult.Rejected, compare(DemoFaceMatchBehavior.REJECT))
        assertEquals(FaceMatchResult.Inconclusive, compare(DemoFaceMatchBehavior.INCONCLUSIVE))
    }

    @Test
    public fun `face technical failure is not a terminal product outcome`() {
        val result = compare(DemoFaceMatchBehavior.TECHNICAL_FAILURE)

        assertEquals("face.comparison_failed", (result as FaceMatchResult.Failed).error.code)
    }

    @Test
    public fun `portrait and selfie strings are redacted`() {
        val rendered = listOf(portrait, selfie).joinToString()

        assertTrue(rendered.contains("[REDACTED]"))
        assertFalse(rendered.contains("synthetic"))
    }

    private fun compare(behavior: DemoFaceMatchBehavior): FaceMatchResult = FakeFaceMatchEngine(behavior).compare(portrait, selfie)

    private companion object {
        val request =
            SelfieCaptureRequest(
                (IdvSessionId.parse("face-demo-session") as IdvResult.Success).value,
            )
        val portrait = DocumentPortraitArtifact("synthetic-portrait")
        val selfie = SelfieArtifact("synthetic-selfie")
    }
}
