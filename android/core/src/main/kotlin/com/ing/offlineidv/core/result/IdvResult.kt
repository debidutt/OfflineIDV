package com.ing.offlineidv.core.result

import com.ing.offlineidv.core.error.IdvError

/**
 * Result of an SDK operation.
 *
 * [Success.toString] deliberately omits its value so accidental logging cannot reveal identity data.
 */
public sealed interface IdvResult<out T> {
    /** Successful value. Callers remain responsible for the value's sensitive-data lifecycle. */
    public class Success<out T>(
        public val value: T,
    ) : IdvResult<T> {
        override fun toString(): String = "IdvResult.Success([REDACTED])"
    }

    /** Failure represented only by the SDK's safe, structured error hierarchy. */
    public data class Failure(
        public val error: IdvError,
    ) : IdvResult<Nothing>
}

/** Transforms a successful value without changing a failure. */
public inline fun <T, R> IdvResult<T>.map(transform: (T) -> R): IdvResult<R> =
    when (this) {
        is IdvResult.Success -> IdvResult.Success(transform(value))
        is IdvResult.Failure -> this
    }

/** Chains a successful operation without nesting [IdvResult] values. */
public inline fun <T, R> IdvResult<T>.flatMap(transform: (T) -> IdvResult<R>): IdvResult<R> =
    when (this) {
        is IdvResult.Success -> transform(value)
        is IdvResult.Failure -> this
    }

/** Returns the successful value, or `null` for a failure. */
public fun <T> IdvResult<T>.getOrNull(): T? =
    when (this) {
        is IdvResult.Success -> value
        is IdvResult.Failure -> null
    }
