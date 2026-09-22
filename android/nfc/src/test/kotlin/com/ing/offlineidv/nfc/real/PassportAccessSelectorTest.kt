package com.ing.offlineidv.nfc.real

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class PassportAccessSelectorTest {
    @Test
    public fun `absent CardAccess permits BAC only when explicitly enabled`() {
        assertEquals(PassportAccessDecision.UseBac, select(CardAccessObservation.Absent, allowBac = true))
        assertEquals(PassportAccessDecision.Unsupported, select(CardAccessObservation.Absent, allowBac = false))
    }

    @Test
    public fun `malformed CardAccess always fails closed`() {
        assertEquals(PassportAccessDecision.Malformed, select(CardAccessObservation.Malformed, allowBac = true))
        assertEquals(PassportAccessDecision.Malformed, select(CardAccessObservation.Malformed, allowBac = false))
    }

    @Test
    public fun `compatible PACE always wins over BAC`() {
        val suite = PaceSuiteDescriptor(PACE_AES_128, 13)

        assertEquals(PassportAccessDecision.UsePace(suite), select(advertised(suite), allowBac = true))
    }

    @Test
    public fun `unknown PACE suite cannot be attempted`() {
        val observation = advertised(PaceSuiteDescriptor("1.2.3.4", 13))

        assertEquals(PassportAccessDecision.UseBac, select(observation, allowBac = true))
        assertEquals(PassportAccessDecision.Unsupported, select(observation, allowBac = false))
    }

    @Test
    public fun `unsupported parameter cannot be attempted`() {
        val observation = advertised(PaceSuiteDescriptor(PACE_AES_128, 99))

        assertEquals(PassportAccessDecision.UseBac, select(observation, allowBac = true))
    }

    @Test
    public fun `selection is deterministic and prefers reviewed stronger AES suite`() {
        val weak = PaceSuiteDescriptor(PACE_AES_128, 13)
        val strong = PaceSuiteDescriptor(PACE_AES_256, 13)

        val selected = select(advertised(weak, strong), allowBac = true)

        assertEquals(PassportAccessDecision.UsePace(strong), selected)
        assertTrue(selected !is PassportAccessDecision.UseBac)
    }

    private fun select(
        observation: CardAccessObservation,
        allowBac: Boolean,
    ): PassportAccessDecision = PassportAccessSelector.select(observation, allowBac)

    private fun advertised(vararg suites: PaceSuiteDescriptor): CardAccessObservation = CardAccessObservation.Advertised(suites.toList())

    private companion object {
        const val PACE_AES_128: String = "0.4.0.127.0.7.2.2.4.2.2"
        const val PACE_AES_256: String = "0.4.0.127.0.7.2.2.4.2.4"
    }
}
