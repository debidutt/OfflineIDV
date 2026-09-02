package com.ing.offlineidv.verification.model

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.SessionFailure
import com.ing.offlineidv.core.error.VerificationFailure
import com.ing.offlineidv.core.session.IdvSessionId

/** Closed verification state hierarchy. */
public sealed interface VerificationState

/** Non-terminal state associated with one active session. */
public sealed interface ActiveVerificationState : VerificationState {
    public val progress: VerificationProgress
}

/** Active state that waits for an explicit host or user event. */
public sealed interface AwaitingUserState : ActiveVerificationState

/** Active state waiting for an external operation completion. */
public sealed interface ProcessingState : ActiveVerificationState

/** Terminal state containing only safe summary evidence and no artifact references. */
public sealed interface TerminalState : VerificationState {
    public val summary: VerificationTerminalSummary
}

public data object Idle : VerificationState

public data class Initializing(
    override val progress: VerificationProgress,
) : ProcessingState

public data class DocumentSelection(
    override val progress: VerificationProgress,
) : AwaitingUserState

public data class CameraPermissionRequired(
    override val progress: VerificationProgress,
) : AwaitingUserState

public data class PreparingCamera(
    override val progress: VerificationProgress,
) : ProcessingState

public data class CameraReady(
    override val progress: VerificationProgress,
) : AwaitingUserState

public data class CapturingDocument(
    override val progress: VerificationProgress,
) : ProcessingState

public data class EvaluatingDocumentQuality(
    override val progress: VerificationProgress,
) : ProcessingState

public data class RunningOcr(
    override val progress: VerificationProgress,
) : ProcessingState

public data class ExtractingMrz(
    override val progress: VerificationProgress,
) : ProcessingState

public data class ValidatingMrz(
    override val progress: VerificationProgress,
) : ProcessingState

public data class AwaitingNfc(
    override val progress: VerificationProgress,
) : AwaitingUserState

public data class ReadingNfc(
    override val progress: VerificationProgress,
) : ProcessingState

public data class ValidatingChipData(
    override val progress: VerificationProgress,
) : ProcessingState

public data class ComparingPrintedAndChipData(
    override val progress: VerificationProgress,
) : ProcessingState

public data class AwaitingSelfie(
    override val progress: VerificationProgress,
) : AwaitingUserState

public data class CapturingSelfie(
    override val progress: VerificationProgress,
) : ProcessingState

public data class EvaluatingSelfie(
    override val progress: VerificationProgress,
) : ProcessingState

public data class ComparingFaces(
    override val progress: VerificationProgress,
) : ProcessingState

public data class MakingDecision(
    override val progress: VerificationProgress,
) : ProcessingState

/** User-visible recovery point valid only for [failedStep]. */
public data class RecoveryRequired(
    override val progress: VerificationProgress,
    public val failedStep: RetryableStep,
    public val retryReason: RetryReason,
    public val retryDecision: RetryDecision,
    public val error: IdvError? = null,
) : AwaitingUserState

public data class Verified(
    override val summary: VerificationTerminalSummary,
) : TerminalState

public data class Rejected(
    override val summary: VerificationTerminalSummary,
) : TerminalState

public data class Inconclusive(
    override val summary: VerificationTerminalSummary,
) : TerminalState

public data class TechnicalFailure(
    override val summary: VerificationTerminalSummary,
) : TerminalState

public data class Cancelled(
    override val summary: VerificationTerminalSummary,
) : TerminalState

public data class Expired(
    override val summary: VerificationTerminalSummary,
) : TerminalState

/** Redacted terminal result with evidence separate from [outcome]. */
public class VerificationTerminalSummary(
    public val sessionId: IdvSessionId,
    public val outcome: VerificationOutcome,
    evidence: Set<VerificationEvidence>,
    public val retries: RetryCounter,
    public val reason: VerificationTerminalReason? = null,
) {
    public val evidence: Set<VerificationEvidence> = evidence.toSet()

    init {
        require((outcome == VerificationOutcome.VERIFIED) == (reason == null)) {
            "Only a verified outcome may omit a terminal reason."
        }
    }

    /** Maps terminal semantics into the existing safe SDK error hierarchy. */
    public fun toIdvErrorOrNull(): IdvError? =
        when (outcome) {
            VerificationOutcome.VERIFIED -> {
                null
            }

            VerificationOutcome.EXPIRED -> {
                IdvError.Session(SessionFailure.EXPIRED)
            }

            VerificationOutcome.CANCELLED -> {
                IdvError.Verification(VerificationFailure.CANCELLED)
            }

            VerificationOutcome.REJECTED -> {
                IdvError.Verification(VerificationFailure.POLICY_REJECTED)
            }

            VerificationOutcome.INCONCLUSIVE -> {
                IdvError.Verification(
                    if (reason == VerificationTerminalReason.REQUIRED_CAPABILITY_UNAVAILABLE) {
                        VerificationFailure.REQUIRED_CAPABILITY_UNAVAILABLE
                    } else {
                        VerificationFailure.INCONCLUSIVE
                    },
                )
            }

            VerificationOutcome.TECHNICAL_FAILURE -> {
                IdvError.Verification(
                    when (reason) {
                        VerificationTerminalReason.STEP_TIMEOUT -> VerificationFailure.STEP_TIMEOUT
                        VerificationTerminalReason.RETRY_EXHAUSTED -> VerificationFailure.RETRY_EXHAUSTED
                        else -> VerificationFailure.TECHNICAL_FAILURE
                    },
                )
            }
        }

    override fun equals(other: Any?): Boolean =
        other is VerificationTerminalSummary &&
            sessionId == other.sessionId &&
            outcome == other.outcome &&
            evidence == other.evidence &&
            retries == other.retries &&
            reason == other.reason

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + outcome.hashCode()
        result = 31 * result + evidence.hashCode()
        result = 31 * result + retries.hashCode()
        return 31 * result + (reason?.hashCode() ?: 0)
    }

    override fun toString(): String =
        "VerificationTerminalSummary(sessionId=$sessionId, outcome=$outcome, evidence=$evidence, " +
            "retries=$retries, reason=$reason)"
}
