package com.ing.offlineidv.core.security

/** Safe redaction helpers for UI and local diagnostics. */
public object Redaction {
    /** Marker used instead of a complete sensitive value. */
    public const val MARKER: String = "[REDACTED]"

    /** Returns the standard redaction marker without inspecting the source value. */
    public fun full(): String = MARKER

    /**
     * Reveals only a requested suffix and prefixes it with an explicit redaction marker.
     *
     * If revealing the suffix would reveal the complete value, nothing is revealed. Callers should
     * use the smallest suffix that satisfies their UX requirement.
     */
    public fun retainingSuffix(
        value: CharSequence,
        visibleCharacters: Int = 2,
    ): String {
        require(visibleCharacters >= 0) { "visibleCharacters must not be negative" }
        if (visibleCharacters == 0 || value.length <= visibleCharacters) return MARKER
        return MARKER + value.takeLast(visibleCharacters)
    }
}
