package com.ing.offlineidv.verification.orchestration

import com.ing.offlineidv.verification.DefaultVerificationStateMachine
import com.ing.offlineidv.verification.VerificationStateMachine
import com.ing.offlineidv.verification.model.Idle
import com.ing.offlineidv.verification.model.TransitionDisposition
import com.ing.offlineidv.verification.model.TransitionResult
import com.ing.offlineidv.verification.model.VerificationContext
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState
import java.util.ArrayDeque

/**
 * FIFO, platform-independent orchestrator for one immutable verification context.
 *
 * Events emitted while effects are being handled are queued and never recursively reduce state.
 */
public class SerializedVerificationOrchestrator(
    private val context: VerificationContext,
    private val effectHandler: VerificationEffectHandler,
    private val stateMachine: VerificationStateMachine = DefaultVerificationStateMachine,
) : VerificationOrchestrator,
    VerificationSessionController {
    private val eventQueue = ArrayDeque<PendingEvent>()
    private val observers = mutableListOf<VerificationStateObserver>()
    private var draining: Boolean = false

    override var state: VerificationState = Idle
        private set

    /** Number of events currently waiting for serialized processing. */
    public val queuedEventCount: Int
        get() = eventQueue.size

    override fun dispatch(event: VerificationEvent): TransitionResult {
        val pending = PendingEvent(event)
        eventQueue.addLast(pending)
        if (!draining) drainQueue()
        return pending.result ?: TransitionResult(state)
    }

    override fun observe(observer: VerificationStateObserver): AutoCloseable {
        observers += observer
        observer.onStateChanged(state)
        return AutoCloseable { observers.remove(observer) }
    }

    override fun cancel(): TransitionResult = dispatch(VerificationEvent.Cancel)

    override fun reset(): TransitionResult = dispatch(VerificationEvent.Reset)

    private fun drainQueue() {
        check(!draining) { "The serialized event queue is already draining." }
        draining = true
        try {
            while (eventQueue.isNotEmpty()) {
                val pending = eventQueue.removeFirst()
                val result = stateMachine.transition(state, pending.event, context)
                state = result.state
                pending.result = result
                if (result.disposition == TransitionDisposition.APPLIED) {
                    observers.toList().forEach { observer -> observer.onStateChanged(state) }
                }
                result.effects.forEach { effect -> effectHandler.handle(effect, this) }
            }
        } finally {
            draining = false
        }
    }

    private class PendingEvent(
        val event: VerificationEvent,
        var result: TransitionResult? = null,
    )
}
