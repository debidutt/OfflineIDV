package com.ing.offlineidv.core.security

/** Strategy for erasing the mutable storage backing a [SensitiveValue]. */
public fun interface SensitiveValueCleaner<T> {
    /** Erases sensitive content from [value] to the extent the representation permits. */
    public fun clear(value: T)
}

/**
 * Opaque holder for sensitive values with explicit cleanup and a redacted string representation.
 *
 * [use] provides scoped access while preventing concurrent cleanup. A caller must not retain the
 * supplied reference beyond the callback. Prefer the byte/character factories because immutable
 * types such as `String` cannot be reliably erased from memory.
 */
public class SensitiveValue<T> private constructor(
    value: T,
    private val cleaner: SensitiveValueCleaner<T>,
) : AutoCloseable {
    private val monitor: Any = Any()
    private var value: T? = value

    /** Whether cleanup has been requested and the held reference has been released. */
    public val isCleared: Boolean
        get() = synchronized(monitor) { value == null }

    /** Performs [block] while the value is available and protected from concurrent cleanup. */
    public fun <R> use(block: (T) -> R): R =
        synchronized(monitor) {
            val available = checkNotNull(value) { "Sensitive value has already been cleared." }
            block(available)
        }

    /** Releases the held reference and invokes its cleanup strategy exactly once. */
    override fun close() {
        val valueToClear =
            synchronized(monitor) {
                val available = value ?: return
                value = null
                available
            }
        cleaner.clear(valueToClear)
    }

    final override fun toString(): String = "SensitiveValue(${Redaction.MARKER})"

    public companion object {
        /** Copies [value] into an owned buffer that is overwritten during cleanup. */
        public fun bytes(value: ByteArray): SensitiveValue<ByteArray> = SensitiveValue(value.copyOf()) { bytes -> bytes.fill(0) }

        /** Copies [value] into an owned buffer that is overwritten during cleanup. */
        public fun characters(value: CharArray): SensitiveValue<CharArray> =
            SensitiveValue(value.copyOf()) { characters -> characters.fill('\u0000') }

        /**
         * Wraps a custom representation with a mandatory cleanup strategy.
         *
         * The caller must ensure [cleaner] erases every mutable buffer reachable from [value].
         */
        public fun <T> withCleaner(
            value: T,
            cleaner: SensitiveValueCleaner<T>,
        ): SensitiveValue<T> = SensitiveValue(value, cleaner)
    }
}
