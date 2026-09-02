package com.ing.offlineidv.verification.demo

import com.ing.offlineidv.core.config.DemoScenario
import com.ing.offlineidv.core.config.IdvConfig
import com.ing.offlineidv.core.result.IdvResult
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class DemoScenarioCatalogTest {
    @Test
    public fun `production configuration cannot construct demo runtime`() {
        val config = success(IdvConfig.production())

        val result = DemoVerificationFactory.create(config, VerificationFixtures.sessionId)

        assertEquals(
            "configuration.demo_mode_required",
            (result as IdvResult.Failure).error.code,
        )
    }

    @Test
    public fun `explicit demo configuration constructs runtime`() {
        val config = success(IdvConfig.demo(DemoScenario.SUCCESS))

        assertTrue(DemoVerificationFactory.create(config, VerificationFixtures.sessionId) is IdvResult.Success)
    }

    @Test
    public fun `catalog provides every declared scenario`() {
        DemoScenario.entries.forEach { scenario ->
            DemoScenarioCatalog.definitionFor(scenario)
        }
    }

    @Test
    public fun `catalog definitions expose no outcome state retry or next-step fields`() {
        val forbidden = setOf("outcome", "state", "retry", "nextStep", "event")
        val fields =
            DemoScenarioDefinition::class.java.declaredFields
                .map { it.name }
                .toSet()

        assertTrue(fields.intersect(forbidden).isEmpty())
    }

    @Test
    public fun `capability input is defensively copied`() {
        val mutable = VerificationCapability.entries.toMutableSet()
        val definition = DemoScenarioDefinition(capabilities = mutable)

        mutable.clear()

        assertFalse(definition.capabilities.isEmpty())
    }

    @Test
    public fun `catalog returns fresh definitions`() {
        assertNotSame(
            DemoScenarioCatalog.definitionFor(DemoScenario.SUCCESS),
            DemoScenarioCatalog.definitionFor(DemoScenario.SUCCESS),
        )
    }

    private fun <T> success(result: IdvResult<T>): T = (result as IdvResult.Success).value
}
