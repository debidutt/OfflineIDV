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
                "Cipher.getInstance",
                "Mac.getInstance",
                "MessageDigest.getInstance",
                "SecretKey",
                "addAPDUListener",
                "notifyExchangedAPDU",
                "doAA",
                "doCA",
                "doTA",
                "java.net",
                "http://",
                "https://",
            ),
        )
    }

    @Test
    public fun `APDU types and transceive stay inside the single transport bridge`() {
        val bridge = sourceFile("IsoDepCardServiceBridge.kt")
        val remaining = nfcRealSourceExcept("IsoDepCardServiceBridge.kt")

        assertTrue(bridge.contains("CommandAPDU"))
        assertTrue(bridge.contains("ResponseAPDU"))
        assertTrue(bridge.contains("isoDep.transceive"))
        assertForbidden(remaining, listOf("CommandAPDU", "ResponseAPDU", ".transceive("))
    }

    @Test
    public fun `BAC and PACE invocation stay inside the reviewed protocol reader`() {
        val reader = sourceFile("JmrtdPassportProtocolReader.kt")
        val remaining = nfcRealSourceExcept("JmrtdPassportProtocolReader.kt")

        assertTrue(reader.contains("service.doPACE"))
        assertTrue(reader.contains("service.doBAC"))
        val pacePath =
            reader
                .substringAfter("private fun authenticateWithPaceAndRead")
                .substringBefore("private fun authenticateWithBacOrReject")
        assertFalse(pacePath.contains("service.doBAC"))
        assertForbidden(remaining, listOf("doBAC", "doPACE"))
    }

    @Test
    public fun `real adapter delegates to contained protocol reader and never uses a fake`() {
        val source = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real")

        assertTrue(source.contains("JmrtdPassportProtocolReader"))
        assertTrue(source.contains("accessKey.fields()"))
        assertFalse(source.contains("FakePassportNfcEngine"))
    }

    private fun productionSource(): String = sourceUnder("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc")

    private fun sourceFile(name: String): String = locate("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real/$name").readText()

    private fun nfcRealSourceExcept(name: String): String =
        locate("android/nfc/src/main/kotlin/com/ing/offlineidv/nfc/real")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != name }
            .joinToString("\n") { it.readText() }

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
