package com.ing.offlineidv.nfc.real

import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PassportChipSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class NewTagHapticGateTest {
    @Test
    public fun `new physical tag produces one short feedback request only`() {
        var pulses = 0
        val gate = NewTagHapticGate()

        repeat(4) {
            gate.perform { pulses += 1 }
        }

        assertEquals(1, pulses)
    }

    @Test
    public fun `separate physical tag discoveries receive separate feedback`() {
        var pulses = 0
        val feedback = NfcTagHapticFeedback { pulses += 1 }

        NewTagHapticGate().perform(feedback)
        NewTagHapticGate().perform(feedback)

        assertEquals(2, pulses)
    }

    @Test
    public fun `background interruption closes an active physical tag session`() {
        val session = RecordingSession()
        val lease = PhysicalTagSessionLease()
        assertTrue(lease.attach(session))

        lease.interrupt()
        lease.interrupt()

        assertEquals(1, session.closeCalls)
    }

    @Test
    public fun `tag session discovered after background race is rejected and closed`() {
        val session = RecordingSession()
        val lease = PhysicalTagSessionLease()
        lease.interrupt()

        assertFalse(lease.attach(session))
        assertEquals(1, session.closeCalls)
    }

    private class RecordingSession : PassportChipSession {
        var closeCalls: Int = 0

        override fun read(accessKey: PassportAccessKey): NfcReadResult = NfcReadResult.Timeout

        override fun close() {
            closeCalls += 1
        }
    }
}
