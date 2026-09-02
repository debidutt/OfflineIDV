package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.camera.DocumentCaptureArtifact
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationArtifactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class DemoArtifactRegistryTest {
    @Test
    public fun `registered artifact resolves only through its issued reference`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        val artifact = DocumentCaptureArtifact(7)
        val reference = success(registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, artifact))

        val resolved =
            success(
                registry.resolve(
                    reference,
                    VerificationArtifactKind.DOCUMENT_CAPTURE,
                    DocumentCaptureArtifact::class.java,
                ),
            )

        assertSame(artifact, resolved)
        assertEquals(1, registry.size)
    }

    @Test
    public fun `equal-looking reference from another session cannot resolve`() {
        val first = DemoArtifactRegistry(VerificationFixtures.sessionId)
        val second = DemoArtifactRegistry(VerificationFixtures.otherSessionId)
        first.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(1))
        val foreign = success(second.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(2)))

        val result =
            first.resolve(
                foreign,
                VerificationArtifactKind.DOCUMENT_CAPTURE,
                DocumentCaptureArtifact::class.java,
            )

        assertFailureCode("verification.artifact_reference_invalid", result)
    }

    @Test
    public fun `wrong artifact kind is rejected without resolving content`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        val reference = success(registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(1)))

        val result =
            registry.resolve(
                reference,
                VerificationArtifactKind.OCR_RESULT,
                DocumentCaptureArtifact::class.java,
            )

        assertFailureCode("verification.artifact_reference_invalid", result)
    }

    @Test
    public fun `wrong artifact type is rejected`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        val reference = success(registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(1)))

        val result = registry.resolve(reference, VerificationArtifactKind.DOCUMENT_CAPTURE, String::class.java)

        assertFailureCode("verification.artifact_reference_invalid", result)
    }

    @Test
    public fun `cleanup is idempotent and permanently closes registry`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(1))

        registry.clear()
        registry.clear()

        assertTrue(registry.isCleared)
        assertEquals(0, registry.size)
        assertTrue(registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(2)) is IdvResult.Failure)
    }

    @Test
    public fun `registry rendering contains no artifact identifier or content`() {
        val registry = DemoArtifactRegistry(VerificationFixtures.sessionId)
        registry.register(VerificationArtifactKind.DOCUMENT_CAPTURE, DocumentCaptureArtifact(99))

        val rendered = registry.toString()

        assertFalse(rendered.contains("demo-artifact"))
        assertFalse(rendered.contains("99"))
    }

    private fun <T> success(result: IdvResult<T>): T = (result as IdvResult.Success).value

    private fun assertFailureCode(
        code: String,
        result: IdvResult<*>,
    ) {
        assertEquals(code, (result as IdvResult.Failure).error.code)
    }
}
