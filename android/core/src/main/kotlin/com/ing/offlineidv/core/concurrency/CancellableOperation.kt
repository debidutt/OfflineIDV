package com.ing.offlineidv.core.concurrency

/** Cooperative cancellation handle for asynchronous platform work. */
public fun interface CancellableOperation {
    /** Prevents a pending completion from crossing its owning boundary. Repeated calls are safe. */
    public fun cancel()

    public companion object {
        /** Handle used when an operation completed before a handle was returned. */
        public val NONE: CancellableOperation = CancellableOperation {}
    }
}
