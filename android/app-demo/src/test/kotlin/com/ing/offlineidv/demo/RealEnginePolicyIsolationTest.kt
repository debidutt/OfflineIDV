package com.ing.offlineidv.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class RealEnginePolicyIsolationTest {
    @Test
    public fun `CameraX engine contains no verification policy or outcome types`() {
        assertForbidden(cameraSource(), POLICY_TYPES)
    }

    @Test
    public fun `ML Kit engine contains no verification policy or outcome types`() {
        assertForbidden(ocrSource(), POLICY_TYPES)
    }

    @Test
    public fun `real NFC adapter contains no verification policy outcome or retry types`() {
        assertForbidden(nfcRealSource(), POLICY_TYPES + listOf("RetryPolicy", "TechnicalFailure", "Rejected"))
    }

    @Test
    public fun `feature engines do not dispatch verification events or access reducer state`() {
        assertForbidden(
            cameraSource() + ocrSource() + nfcProductionSource(),
            listOf("VerificationEvent", "VerificationState", "VerificationStateMachine", "eventSink", ".dispatch("),
        )
    }

    @Test
    public fun `feature engines contain no retry or next step selection`() {
        assertForbidden(
            cameraSource() + ocrSource() + nfcProductionSource(),
            listOf("RetryDecision", "RecoveryRequired", "nextStep", "allowRetry"),
        )
    }

    @Test
    public fun `production sources contain no sensitive logging or public storage APIs`() {
        val sources = cameraSource() + ocrSource() + nfcProductionSource()
        listOf("Log.", "println(", "MediaStore", "Environment.getExternal", "getExternalFilesDir").forEach { token ->
            assertFalse("real engine references forbidden token $token", sources.contains(token))
        }
        val fileConstructorRegex = Regex("""\bFile\(""")
        assertFalse("real engine references forbidden token File(", fileConstructorRegex.containsMatchIn(sources))
    }

    @Test
    public fun `NFC source contains no APDU logging or custom passport cryptography`() {
        assertForbidden(
            nfcProductionSource(),
            listOf(
                "android.util.Log",
                "Log.",
                "println(",
                "Cipher.getInstance",
                "Mac.getInstance",
                "MessageDigest.getInstance",
                "SecretKey",
                "addAPDUListener",
                "notifyExchangedAPDU",
                "doAA",
                "doCA",
                "doTA",
            ),
        )
        assertTrue(nfcRealSource().contains("JmrtdPassportProtocolReader"))
        assertContained("IsoDepCardServiceBridge.kt", listOf("CommandAPDU", "ResponseAPDU", "isoDep.transceive"))
        assertContained(
            "JmrtdPassportProtocolReader.kt",
            listOf("service.doBAC", "service.doPACE"),
        )
        assertContained("PassportChipAuthenticity.kt", listOf("service.doEACCA"))
    }

    @Test
    public fun `manifest has CAMERA and NFC and remains network free`() {
        val manifest = locate("android/app-demo/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.CAMERA"))
        assertTrue(manifest.contains("android.permission.NFC"))
        assertPermissionRemoved(manifest, "android.permission.INTERNET")
        assertPermissionRemoved(manifest, "android.permission.ACCESS_NETWORK_STATE")
    }

    private fun assertPermissionRemoved(
        manifest: String,
        permission: String,
    ) {
        val removal =
            Regex(
                """<uses-permission\s+android:name="$permission"\s+tools:node="remove"\s*/>""",
                RegexOption.MULTILINE,
            )
        assertTrue("$permission must be removed from the merged manifest", removal.containsMatchIn(manifest))
    }

    private fun cameraSource(): String = sourceUnder("android/camera/src/main/kotlin/com/ing/offlineidv/camera/real")

    private fun ocrSource(): String = sourceUnder("android/ocr/src/main/kotlin/com/ing/offlineidv/ocr/real")

    private fun nfcRealSource(): String = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real")

    private fun nfcProductionSource(): String = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc")

    private fun assertContained(
        allowedFile: String,
        tokens: List<String>,
    ) {
        val directory = locate("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real")
        val allowed = File(directory, allowedFile).readText()
        val remaining =
            directory
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != allowedFile }
                .joinToString("\n") { it.readText() }
        tokens.forEach { token ->
            assertTrue("allowed adapter must contain $token", allowed.contains(token))
            assertFalse("$token escaped $allowedFile", remaining.contains(token))
        }
    }

    private fun sourceUnder(path: String): String =
        locate(path)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    private fun assertForbidden(
        source: String,
        forbidden: List<String>,
    ) {
        forbidden.forEach { token -> assertFalse("real engine references forbidden token $token", source.contains(token)) }
    }

    private fun locate(path: String): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
        }
        error("Could not locate $path")
    }

    private companion object {
        val POLICY_TYPES: List<String> =
            listOf(
                "VerificationPolicyEvaluator",
                "VerificationPolicy",
                "VerificationOutcome",
                "RetryDecision",
                "RecoveryRequired",
                "Verified",
                "Inconclusive",
                "MakingDecision",
            )
    }
}
