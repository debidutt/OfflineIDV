package com.ing.offlineidv.verification

import com.ing.offlineidv.core.error.IdvErrorCategory
import com.ing.offlineidv.verification.fixtures.StateMachineHarness
import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.fixtures.advanceToCameraReady
import com.ing.offlineidv.verification.fixtures.completeHappyPath
import com.ing.offlineidv.verification.model.CapturingDocument
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.Verified
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class VerificationSecurityTest {
    private val opaqueValue = "document_ref_0001"

    @Test
    public fun `opaque artifact reference always redacts its identifier`() {
        val rendered = VerificationFixtures.documentReference.toString()

        assertFalse(rendered.contains(opaqueValue))
        assertTrue(rendered.contains("[REDACTED]"))
    }

    @Test
    public fun `active state does not render opaque artifact value`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        harness.dispatch(VerificationEvent.CaptureRequested)
        val rendered = (harness.state as CapturingDocument).toString()

        assertFalse(rendered.contains(opaqueValue))
        assertFalse(rendered.contains("passport", ignoreCase = true))
    }

    @Test
    public fun `events and effects render references safely`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        harness.dispatch(VerificationEvent.CaptureRequested)
        val operation = harness.operation()
        val event = VerificationEvent.DocumentCaptured(operation, VerificationFixtures.documentReference)
        val effect = VerificationEffect.RunOcr(operation, VerificationFixtures.documentReference)

        listOf(event.toString(), effect.toString()).forEach { rendered ->
            assertFalse(rendered.contains(opaqueValue))
            assertTrue(rendered.contains("[REDACTED]"))
        }
    }

    @Test
    public fun `terminal state drops artifact references while retaining evidence`() {
        val harness = StateMachineHarness()
        harness.completeHappyPath()
        val terminal = harness.state as Verified
        val rendered = terminal.toString()

        assertFalse(rendered.contains("Artifact"))
        assertFalse(rendered.contains("_ref_"))
        assertTrue(terminal.summary.evidence.isNotEmpty())
    }

    @Test
    public fun `terminal summary maps to safe existing error hierarchy`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        harness.dispatch(VerificationEvent.Cancel)
        val summary = (harness.state as com.ing.offlineidv.verification.model.Cancelled).summary
        val error = summary.toIdvErrorOrNull()

        assertEquals(IdvErrorCategory.VERIFICATION, error?.category)
        assertEquals("verification.cancelled", error?.code)
        assertFalse(error.toString().contains(opaqueValue))
    }

    @Test
    public fun `transition result rendering does not expose artifact identifiers`() {
        val harness = StateMachineHarness()
        harness.advanceToCameraReady()
        val result = harness.dispatch(VerificationEvent.CaptureRequested)

        assertFalse(result.toString().contains(opaqueValue))
    }
}
