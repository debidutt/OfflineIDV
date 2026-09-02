package com.ing.offlineidv.verification.scheduling

import com.ing.offlineidv.core.session.IdvSessionId
import com.ing.offlineidv.verification.model.VerificationOperationToken
import java.time.Duration

/** Platform-independent scheduling boundary for tokenized verification timeouts. */
public interface VerificationScheduler {
    /** Registers one timeout callback without defining a wall-clock implementation. */
    public fun schedule(
        operation: VerificationOperationToken,
        duration: Duration,
        onTimeout: () -> Unit,
    )

    /** Cancels [operation] if it is scheduled. */
    public fun cancel(operation: VerificationOperationToken)

    /** Cancels all scheduled work for [sessionId]. */
    public fun cancelAll(sessionId: IdvSessionId)
}

/** Deterministic scheduler fired explicitly by tests or a platform-independent demo runner. */
public class DeterministicVerificationScheduler : VerificationScheduler {
    private val scheduled = linkedMapOf<VerificationOperationToken, ScheduledTimeout>()

    override fun schedule(
        operation: VerificationOperationToken,
        duration: Duration,
        onTimeout: () -> Unit,
    ) {
        require(!duration.isZero && !duration.isNegative) { "duration must be positive" }
        scheduled[operation] = ScheduledTimeout(duration, onTimeout)
    }

    override fun cancel(operation: VerificationOperationToken) {
        scheduled.remove(operation)
    }

    override fun cancelAll(sessionId: IdvSessionId) {
        scheduled.keys.filter { it.sessionId == sessionId }.forEach(scheduled::remove)
    }

    /** Fires [operation] once when still scheduled; cancelled and unknown operations return false. */
    public fun fire(operation: VerificationOperationToken): Boolean {
        val timeout = scheduled.remove(operation) ?: return false
        timeout.onTimeout()
        return true
    }

    /** Whether [operation] is currently scheduled. */
    public fun isScheduled(operation: VerificationOperationToken): Boolean = operation in scheduled

    /** Current number of scheduled callbacks. */
    public val scheduledCount: Int
        get() = scheduled.size

    /** Safe token list for deterministic tests. */
    public fun scheduledOperations(): List<VerificationOperationToken> = scheduled.keys.toList()

    private data class ScheduledTimeout(
        val duration: Duration,
        val onTimeout: () -> Unit,
    )
}
