package com.ing.offlineidv.verification.model

/** Safe result classification for applied and ignored events. */
public enum class TransitionDisposition {
    APPLIED,
    IGNORED_ILLEGAL_EVENT,
    IGNORED_DUPLICATE_EVENT,
    IGNORED_STALE_EVENT,
}

/** Immutable deterministic reducer result. */
public class TransitionResult(
    public val state: VerificationState,
    effects: List<VerificationEffect> = emptyList(),
    public val disposition: TransitionDisposition = TransitionDisposition.APPLIED,
) {
    public val effects: List<VerificationEffect> = effects.toList()

    override fun equals(other: Any?): Boolean =
        other is TransitionResult &&
            state == other.state &&
            effects == other.effects &&
            disposition == other.disposition

    override fun hashCode(): Int = 31 * (31 * state.hashCode() + effects.hashCode()) + disposition.hashCode()

    override fun toString(): String = "TransitionResult(state=$state, effects=$effects, disposition=$disposition)"
}
