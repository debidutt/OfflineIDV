package com.ing.offlineidv.verification.orchestration

import com.ing.offlineidv.verification.fixtures.VerificationFixtures
import com.ing.offlineidv.verification.model.Cancelled
import com.ing.offlineidv.verification.model.DocumentSelection
import com.ing.offlineidv.verification.model.Idle
import com.ing.offlineidv.verification.model.Initializing
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class SerializedVerificationOrchestratorTest {
    @Test
    public fun `effect completions are queued without recursive state mutation`() {
        var completionDispatchState: VerificationState? = null
        val handler =
            VerificationEffectHandler { effect, sink ->
                if (effect is VerificationEffect.InitializeSession) {
                    completionDispatchState =
                        sink.dispatch(VerificationEvent.InitializationSucceeded(effect.operation)).state
                }
            }
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), handler)

        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        assertTrue(completionDispatchState is Initializing)
        assertTrue(orchestrator.state is DocumentSelection)
        assertEquals(0, orchestrator.queuedEventCount)
    }

    @Test
    public fun `reentrant events preserve FIFO order`() {
        val observed = mutableListOf<String>()
        val handler =
            VerificationEffectHandler { effect, sink ->
                if (effect is VerificationEffect.InitializeSession) {
                    sink.dispatch(VerificationEvent.InitializationSucceeded(effect.operation))
                    sink.dispatch(VerificationEvent.Cancel)
                }
            }
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), handler)
        orchestrator.observe { state -> observed += state.javaClass.simpleName }

        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        assertEquals(listOf("Idle", "Initializing", "DocumentSelection", "Cancelled"), observed)
        assertTrue(orchestrator.state is Cancelled)
    }

    @Test
    public fun `duplicate completion does not duplicate state notification`() {
        val observed = mutableListOf<String>()
        val handler =
            VerificationEffectHandler { effect, sink ->
                if (effect is VerificationEffect.InitializeSession) {
                    repeat(2) {
                        sink.dispatch(VerificationEvent.InitializationSucceeded(effect.operation))
                    }
                }
            }
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), handler)
        orchestrator.observe { state -> observed += state.javaClass.simpleName }

        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        assertEquals(1, observed.count { it == "DocumentSelection" })
        assertTrue(orchestrator.state is DocumentSelection)
    }

    @Test
    public fun `late engine completion after cancellation is ignored`() {
        var operation: com.ing.offlineidv.verification.model.VerificationOperationToken? = null
        val handler =
            VerificationEffectHandler { effect, _ ->
                if (effect is VerificationEffect.InitializeSession) operation = effect.operation
            }
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), handler)
        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
        orchestrator.cancel()

        val terminal = orchestrator.state
        val late = orchestrator.dispatch(VerificationEvent.InitializationSucceeded(checkNotNull(operation)))

        assertSame(terminal, late.state)
        assertEquals(TransitionDisposition.IGNORED_DUPLICATE_EVENT, late.disposition)
    }

    @Test
    public fun `closed observer receives no later state`() {
        val observed = mutableListOf<String>()
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), VerificationEffectHandler { _, _ -> })
        val handle = orchestrator.observe { state -> observed += state.javaClass.simpleName }

        handle.close()
        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))

        assertEquals(listOf("Idle"), observed)
    }

    @Test
    public fun `terminal session resets to idle through serialized dispatch`() {
        val orchestrator = SerializedVerificationOrchestrator(VerificationContext(), VerificationEffectHandler { _, _ -> })
        orchestrator.dispatch(VerificationEvent.Start(VerificationFixtures.sessionId))
        orchestrator.cancel()

        val result = orchestrator.reset()

        assertSame(Idle, result.state)
        assertSame(Idle, orchestrator.state)
    }
}
