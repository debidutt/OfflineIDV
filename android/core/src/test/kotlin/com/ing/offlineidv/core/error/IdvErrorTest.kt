package com.ing.offlineidv.core.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class IdvErrorTest {
    @Test
    public fun `error exposes stable safe metadata`() {
        val error = IdvError.Nfc(NfcFailure.TIMEOUT)

        assertEquals(IdvErrorCategory.NFC, error.category)
        assertEquals("nfc.timeout", error.code)
        assertEquals(IdvRecovery.RETRY, error.recovery)
        assertEquals("The passport read timed out.", error.safeDescription)
    }

    @Test
    public fun `error string contains codes but no arbitrary diagnostics`() {
        val error = IdvError.Storage(StorageFailure.CLEANUP_FAILED)

        val rendered = error.toString()

        assertTrue(rendered.contains("storage.cleanup_failed"))
        assertFalse(rendered.contains(error.safeDescription))
    }

    @Test
    public fun `internal error remains predefined and non-retryable in place`() {
        val error = IdvError.Internal

        assertEquals("internal.unexpected", error.code)
        assertEquals(IdvRecovery.RESTART_SESSION, error.recovery)
        assertFalse(error.toString().contains(error.safeDescription))
    }

    @Test
    public fun `demo mode requirement is a safe configuration error`() {
        val error = IdvError.Configuration(ConfigurationFailure.DEMO_MODE_REQUIRED)

        assertEquals("configuration.demo_mode_required", error.code)
        assertEquals(IdvErrorCategory.CONFIGURATION, error.category)
        assertFalse(error.toString().contains(error.safeDescription))
    }

    @Test
    public fun `invalid artifact reference is a safe verification error`() {
        val error = IdvError.Verification(VerificationFailure.ARTIFACT_REFERENCE_INVALID)

        assertEquals("verification.artifact_reference_invalid", error.code)
        assertEquals(IdvRecovery.RESTART_SESSION, error.recovery)
        assertFalse(error.toString().contains(error.safeDescription))
    }
}
