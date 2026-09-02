package com.ing.offlineidv.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class UiPolicyIsolationTest {
    @Test
    public fun `production ui contains no policy evaluator validator engine or fake references`() {
        val source = productionSource("android/ui/src/main")
        val forbidden =
            listOf(
                "VerificationPolicyEvaluator",
                "VerificationPolicy(",
                "MrzValidator",
                "FaceMatchEngine",
                "PassportNfcEngine",
                "FakeDocument",
                "FakeOcr",
                "FakePassport",
                "FakeFace",
            )

        forbidden.forEach { token -> assertFalse("UI references forbidden token $token", source.contains(token)) }
    }

    @Test
    public fun `camera preview and MRZ presentation cannot carry raw payload types`() {
        val source = productionSource("android/ui/src/main")
        listOf("Bitmap", "ImageProxy", "OcrTextArtifact", "DocumentCaptureArtifact", "ByteArray", "sourceToken").forEach { token ->
            assertFalse("UI model/source references sensitive payload type $token", source.contains(token))
        }
        assertTrue(source.contains("cameraPreview: (@Composable () -> Unit)?"))
    }

    @Test
    public fun `shared mapper copy contains no implementation-specific terminology`() {
        val source = productionFile("android/ui/src/main/kotlin/com/ing/offlineidv/ui/VerificationUiStateMapper.kt")

        listOf("synthetic", "fake", "simulated").forEach { term ->
            assertFalse("Shared mapper contains implementation-specific term $term", source.contains(term, ignoreCase = true))
        }
    }

    @Test
    public fun `shared mapper remains runtime-mode agnostic`() {
        val source = productionFile("android/ui/src/main/kotlin/com/ing/offlineidv/ui/VerificationUiStateMapper.kt")

        listOf("RuntimeMode", "IdvMode", "REAL_ANDROID", "CameraX", "MlKit", "ML Kit").forEach { term ->
            assertFalse("Shared mapper references mode or engine term $term", source.contains(term, ignoreCase = true))
        }
    }

    @Test
    public fun `production ui does not derive decisions from evidence or retry counters`() {
        val source = productionSource("android/ui/src/main")
        val forbiddenDecisionPatterns =
            listOf(
                "attemptsFor(",
                "maximumAttempts(",
                "recordAttempt(",
                "retryCount",
                "FACE_MATCH_REJECTED -> VerificationOutcome",
                "DOCUMENT_EXPIRED -> VerificationOutcome",
                "PRINTED_CHIP_DATA_MISMATCH -> VerificationOutcome",
            )

        forbiddenDecisionPatterns.forEach { token -> assertFalse(source.contains(token)) }
        assertTrue(source.contains("state.retryDecision == RetryDecision.RETRY_AVAILABLE"))

        val evidencePresentation =
            source
                .substringAfter("private fun evidenceItem")
                .substringBefore("private fun confirmed")
        listOf("VERIFIED", "REJECTED", "INCONCLUSIVE", "TECHNICAL_FAILURE", "CANCELLED", "EXPIRED").forEach { outcome ->
            assertFalse(
                "Evidence presentation derives $outcome",
                evidencePresentation.contains("VerificationOutcome.$outcome"),
            )
        }
    }

    private fun productionSource(path: String): String =
        locate(path)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    private fun productionFile(path: String): String = locate(path).readText()

    private fun locate(path: String): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
        }
        error("Could not locate production source path.")
    }
}
