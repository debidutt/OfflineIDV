package com.ing.offlineidv.verification.scheduling

import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.VerificationOperationToken
import com.ing.offlineidv.verification.model.VerificationStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

public class DeterministicVerificationSchedulerTest {
    @Test
    public fun `scheduled callback runs only when explicitly fired`() {
        val scheduler = DeterministicVerificationScheduler()
        val token = token(VerificationStep.OCR, 1)
        var calls = 0

        scheduler.schedule(token, Duration.ofSeconds(3)) { calls += 1 }

        assertEquals(0, calls)
        assertTrue(scheduler.isScheduled(token))
        assertTrue(scheduler.fire(token))
        assertEquals(1, calls)
    }

    @Test
    public fun `firing is single shot`() {
        val scheduler = DeterministicVerificationScheduler()
        val token = token(VerificationStep.NFC_READ, 2)
        var calls = 0
        scheduler.schedule(token, Duration.ofSeconds(1)) { calls += 1 }

        assertTrue(scheduler.fire(token))
        assertFalse(scheduler.fire(token))
        assertEquals(1, calls)
    }

    @Test
    public fun `cancel prevents late callback`() {
        val scheduler = DeterministicVerificationScheduler()
        val token = token(VerificationStep.FACE_COMPARISON, 3)
        scheduler.schedule(token, Duration.ofSeconds(1)) { error("cancelled callback ran") }

        scheduler.cancel(token)

        assertFalse(scheduler.fire(token))
    }

    @Test
    public fun `cancel all affects only matching session`() {
        val scheduler = DeterministicVerificationScheduler()
        val local = token(VerificationStep.OCR, 1)
        val foreign =
            VerificationOperationToken(
                VerificationFixtures.otherSessionId,
                VerificationStep.OCR,
                1,
            )
        scheduler.schedule(local, Duration.ofSeconds(1)) {}
        scheduler.schedule(foreign, Duration.ofSeconds(1)) {}

        scheduler.cancelAll(VerificationFixtures.sessionId)

        assertFalse(scheduler.isScheduled(local))
        assertTrue(scheduler.isScheduled(foreign))
    }

    @Test(expected = IllegalArgumentException::class)
    public fun `non-positive duration is rejected`() {
        DeterministicVerificationScheduler().schedule(
            token(VerificationStep.OCR, 1),
            Duration.ZERO,
        ) {}
    }

    private fun token(
        step: VerificationStep,
        generation: Int,
    ): VerificationOperationToken = VerificationOperationToken(VerificationFixtures.sessionId, step, generation)
}
