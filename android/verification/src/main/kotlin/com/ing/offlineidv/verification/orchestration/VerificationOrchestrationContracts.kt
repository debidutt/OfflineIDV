package com.ing.offlineidv.verification.orchestration

import com.ing.offlineidv.verification.model.TransitionResult
import com.ing.offlineidv.verification.model.VerificationEffect
import com.ing.offlineidv.verification.model.VerificationEvent
import com.ing.offlineidv.verification.model.VerificationState

/** Receives safe events from a host or external effect handler. */
public fun interface VerificationEventSink {
    /** Dispatches [event] to the owning orchestrator. */
    public fun dispatch(event: VerificationEvent): TransitionResult
}

/** Observes immutable state changes without imposing Flow, coroutines, or Android lifecycle APIs. */
public fun interface VerificationStateObserver {
    /** Receives the latest [state] after an applied transition. */
    public fun onStateChanged(state: VerificationState)
}

/** Executes one intent outside the reducer and returns eventual completions through [eventSink]. */
public fun interface VerificationEffectHandler {
    /** Handles [effect] without exposing implementation objects to verification contracts. */
    public fun handle(
        effect: VerificationEffect,
        eventSink: VerificationEventSink,
    )
}

/** Platform-independent orchestration boundary; no implementation is supplied in Milestone 3. */
public interface VerificationOrchestrator : VerificationEventSink {
    /** Current immutable state. */
    public val state: VerificationState

    /** Adds [observer] and returns a handle that removes it when closed. */
    public fun observe(observer: VerificationStateObserver): AutoCloseable
}

/** Host lifecycle boundary for cancellation and reset without Android lifecycle coupling. */
public interface VerificationSessionController {
    /** Requests cancellation of the active session. */
    public fun cancel(): TransitionResult

    /** Resets a terminal session to the idle state. */
    public fun reset(): TransitionResult
}
