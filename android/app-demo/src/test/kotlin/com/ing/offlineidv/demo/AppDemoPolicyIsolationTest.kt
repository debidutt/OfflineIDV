package com.ing.offlineidv.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class AppDemoPolicyIsolationTest {
    @Test
    public fun `composition and controller reference no feature fake engines`() {
        val source = productionSource()
        val forbidden =
            listOf(
                "FakeDocumentCaptureEngine",
                "FakeOcrEngine",
                "FakePassportNfcEngine",
                "FakeFaceMatchEngine",
            )

        forbidden.forEach { token -> assertFalse(source.contains(token)) }
        assertTrue(source.contains("DemoVerificationFactory"))
    }

    @Test
    public fun `controller dispatches events without policy decision logic`() {
        val source = locateProductionSource().resolve("kotlin/com/ing/offlineidv/demo/AtlasDemoController.kt").readText()
        val forbidden =
            listOf(
                "VerificationPolicyEvaluator",
                "VerificationPolicy(",
                "attemptsFor(",
                "VerificationOutcome.REJECTED",
                "VerificationOutcome.VERIFIED",
                "DOCUMENT_EXPIRED",
                "FACE_MATCH_REJECTED",
            )

        forbidden.forEach { token -> assertFalse("app-demo references forbidden token $token", source.contains(token)) }
        assertTrue(source.contains("VerificationEvent.CaptureRequested"))
        assertTrue(source.contains("VerificationEvent.NfcRequested"))
        assertTrue(source.contains("VerificationEvent.SelfieRequested"))
    }

    @Test
    public fun `demo and real runtimes feed one shared state mapper`() {
        val source = locateProductionSource().resolve("kotlin/com/ing/offlineidv/demo/AtlasDemoController.kt").readText()

        assertEquals(1, Regex("VerificationUiStateMapper\\.map").findAll(source).count())
        assertTrue(source.contains("update(VerificationUiStateMapper.map(domainState))"))
    }

    private fun productionSource(): String =
        locateProductionSource()
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    private fun locateProductionSource(): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (directory != null) {
            val candidate = File(directory, "android/app-demo/src/main")
            if (candidate.isDirectory) return candidate
            directory = directory.parentFile
        }
        error("Could not locate app-demo production source directory.")
    }
}
