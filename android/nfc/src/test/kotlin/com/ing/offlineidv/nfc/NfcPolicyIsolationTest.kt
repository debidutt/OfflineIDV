package com.ing.offlineidv.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class NfcPolicyIsolationTest {
    @Test
    public fun `NFC production has no verification policy outcome or retry dependency`() {
        assertForbidden(
            productionSource(),
            listOf(
                "VerificationPolicy",
                "VerificationPolicyEvaluator",
                "VerificationOutcome",
                "RetryPolicy",
                "RetryDecision",
                "RecoveryRequired",
                "TechnicalFailure",
                "MakingDecision",
            ),
        )
    }

    @Test
    public fun `NFC production cannot dispatch verification events or access reducer state`() {
        assertForbidden(
            productionSource(),
            listOf(
                "VerificationEvent",
                "VerificationStateMachine",
                "VerificationState",
                "VerificationEventSink",
                "eventSink.dispatch",
            ),
        )
    }

    @Test
    public fun `NFC production contains no APDU logging networking or custom passport crypto`() {
        assertForbidden(
            productionSource(),
            listOf(
                "android.util.Log",
                "println(",
                "CommandAPDU",
                "ResponseAPDU",
                "transceive(",
                "Cipher.getInstance",
                "Mac.getInstance",
                "MessageDigest.getInstance",
                "SecretKey",
                "doBAC",
                "doPACE",
                "java.net",
                "http://",
                "https://",
            ),
        )
    }

    @Test
    public fun `real adapter stops before opening access material or using a fake`() {
        val source = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real")

        assertTrue(source.contains("PROTOCOL_UNSUPPORTED"))
        assertFalse(source.contains("accessKey.useValue"))
        assertFalse(source.contains("FakePassportNfcEngine"))
    }

    private fun productionSource(): String = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc")

    private fun sourceUnder(path: String): String =
        locate(path)
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    private fun assertForbidden(
        source: String,
        tokens: List<String>,
    ) {
        tokens.forEach { token -> assertFalse("NFC production references forbidden token $token", source.contains(token)) }
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
}
