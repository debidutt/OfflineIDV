package com.ing.offlineidv.core.config

import com.ing.offlineidv.core.error.ConfigurationFailure
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.core.verification.VerificationRequirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

public class IdvConfigTest {
    @Test
    public fun `production is the explicit safe default`() {
        val result = IdvConfig.production()

        val config = (result as IdvResult.Success).value
        assertEquals(IdvMode.PRODUCTION, config.mode)
        assertNull(config.demoScenario)
        assertTrue(config.requirements.contains(VerificationRequirement.MRZ_CHECKSUM))
    }

    @Test
    public fun `demo configuration always requires an explicit scenario`() {
        val result = IdvConfig.demo(DemoScenario.NFC_TIMEOUT)

        val config = (result as IdvResult.Success).value
        assertEquals(IdvMode.DEMO, config.mode)
        assertEquals(DemoScenario.NFC_TIMEOUT, config.demoScenario)
    }

    @Test
    public fun `non-positive timeout returns a structured configuration error`() {
        val result = IdvConfig.production(sessionTimeout = Duration.ZERO)

        val failure = result as IdvResult.Failure
        assertEquals(ConfigurationFailure.INVALID_SESSION_TIMEOUT.stableCode, failure.error.code)
    }

    @Test
    public fun `empty requirements return a structured configuration error`() {
        val result = IdvConfig.production(requirements = emptySet())

        val failure = result as IdvResult.Failure
        assertEquals(ConfigurationFailure.EMPTY_REQUIREMENTS.stableCode, failure.error.code)
    }

    @Test
    public fun `requirements are defensively copied`() {
        val requirements = mutableSetOf(VerificationRequirement.MRZ_CHECKSUM)
        val config = (IdvConfig.production(requirements = requirements) as IdvResult.Success).value

        requirements.clear()

        assertEquals(setOf(VerificationRequirement.MRZ_CHECKSUM), config.requirements)
    }
}
