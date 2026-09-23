package com.ing.offlineidv.nfc

import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Opaque connected passport-tag session; Android `Tag` and `IsoDep` stay behind its implementation. */
public fun interface PassportChipSession : AutoCloseable {
    /** Performs a reviewed protocol read or returns a safe feature observation. */
    public fun read(accessKey: PassportAccessKey): NfcReadResult

    /** Performs a read while optionally reporting payload-free transport progress. */
    public fun read(
        accessKey: PassportAccessKey,
        progressObserver: NfcReadProgressObserver,
    ): NfcReadResult = read(accessKey)

    override fun close() {
        // Functional implementations that own no transport need no cleanup.
    }
}

/** Safe result of platform tag discovery without any tag identifier or technology object. */
public sealed interface NfcTagDiscoveryResult {
    public data class Connected(
        public val session: PassportChipSession,
    ) : NfcTagDiscoveryResult

    public data object UnsupportedTag : NfcTagDiscoveryResult

    public data object IsoDepUnavailable : NfcTagDiscoveryResult

    public data class Failed(
        public val error: IdvError,
    ) : NfcTagDiscoveryResult
}

/** Starts Android-owned tag discovery and returns an idempotent reader-mode cancellation handle. */
public fun interface NfcTagDiscovery {
    public fun start(callback: (NfcTagDiscoveryResult) -> Unit): CancellableOperation

    /** Starts discovery with confirmation that Android reader mode was enabled successfully. */
    public fun start(
        progressObserver: NfcReadProgressObserver,
        callback: (NfcTagDiscoveryResult) -> Unit,
    ): CancellableOperation = start(callback)
}

/**
 * Serializes one user-driven NFC read without knowing verification state, policy, events, or retry.
 *
 * Discovery and chip I/O are injected so cancellation, duplicate tags, and late callbacks remain
 * deterministic in JVM tests.
 */
public class NfcSessionCoordinator(
    private val capabilityDetector: NfcCapabilityDetector,
    private val discovery: NfcTagDiscovery,
    private val executor: Executor,
) : AsyncPassportNfcEngine,
    AutoCloseable {
    private var active: ActiveRead? = null
    private var closed: Boolean = false

    override fun read(
        request: NfcReadRequest,
        callback: (NfcReadResult) -> Unit,
    ): CancellableOperation = read(request, NfcReadProgressObserver.NONE, callback)

    override fun read(
        request: NfcReadRequest,
        progressObserver: NfcReadProgressObserver,
        callback: (NfcReadResult) -> Unit,
    ): CancellableOperation {
        val capability = safeCapability()
        when (capability) {
            NfcCapability.UNAVAILABLE -> {
                callback(NfcReadResult.Unavailable)
                return CancellableOperation.NONE
            }

            NfcCapability.DISABLED -> {
                callback(NfcReadResult.Failed(IdvError.Nfc(NfcFailure.DISABLED)))
                return CancellableOperation.NONE
            }

            NfcCapability.AVAILABLE -> {
                Unit
            }
        }

        val operation = ActiveRead(request, progressObserver, callback)
        synchronized(this) {
            if (closed) {
                callback(NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                return CancellableOperation.NONE
            }
            active?.cancel()
            active = operation
        }
        val handle =
            try {
                discovery.start(NfcReadProgressObserver(operation::report)) { result -> accept(operation, result) }
            } catch (_: RuntimeException) {
                complete(operation, NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                CancellableOperation.NONE
            }
        operation.attachDiscovery(handle)
        return CancellableOperation { cancel(operation) }
    }

    private fun accept(
        operation: ActiveRead,
        result: NfcTagDiscoveryResult,
    ) {
        if (!isCurrent(operation) || !operation.claimTag()) {
            if (result is NfcTagDiscoveryResult.Connected) safelyClose(result.session)
            return
        }
        when (result) {
            NfcTagDiscoveryResult.UnsupportedTag -> {
                complete(operation, NfcReadResult.Failed(IdvError.Nfc(NfcFailure.UNSUPPORTED_TAG)))
            }

            NfcTagDiscoveryResult.IsoDepUnavailable -> {
                complete(operation, NfcReadResult.Failed(IdvError.Nfc(NfcFailure.ISO_DEP_UNAVAILABLE)))
            }

            is NfcTagDiscoveryResult.Failed -> {
                complete(operation, NfcReadResult.Failed(result.error))
            }

            is NfcTagDiscoveryResult.Connected -> {
                operation.attachSession(result.session)
                operation.report(NfcReadProgress.TAG_DETECTED)
                try {
                    executor.execute {
                        operation.report(NfcReadProgress.CONNECTING)
                        val readResult =
                            try {
                                result.session.read(operation.request.accessKey, operation::report)
                            } catch (_: RuntimeException) {
                                NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR))
                            } finally {
                                safelyClose(result.session)
                            }
                        complete(operation, readResult)
                    }
                } catch (_: RuntimeException) {
                    safelyClose(result.session)
                    complete(operation, NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                }
            }
        }
    }

    private fun complete(
        operation: ActiveRead,
        result: NfcReadResult,
    ) {
        val deliver =
            synchronized(this) {
                if (active === operation && !operation.cancelled.get()) {
                    active = null
                    true
                } else {
                    false
                }
            }
        operation.cancelDiscovery()
        if (deliver) operation.callback(result)
    }

    private fun cancel(operation: ActiveRead) {
        synchronized(this) {
            if (active === operation) active = null
        }
        operation.cancel()
    }

    private fun isCurrent(operation: ActiveRead): Boolean = synchronized(this) { !closed && active === operation }

    private fun safeCapability(): NfcCapability =
        try {
            capabilityDetector.detect()
        } catch (_: RuntimeException) {
            NfcCapability.UNAVAILABLE
        }

    override fun close() {
        val pending =
            synchronized(this) {
                if (closed) return
                closed = true
                active.also { active = null }
            }
        pending?.cancel()
    }

    private class ActiveRead(
        val request: NfcReadRequest,
        private val progressObserver: NfcReadProgressObserver,
        val callback: (NfcReadResult) -> Unit,
    ) {
        val cancelled = AtomicBoolean(false)
        private val tagClaimed = AtomicBoolean(false)
        private val discoveryCancellationRequested = AtomicBoolean(false)

        @Volatile private var discovery: CancellableOperation? = null

        @Volatile private var session: PassportChipSession? = null

        fun attachDiscovery(handle: CancellableOperation) {
            discovery = handle
            if (cancelled.get() || discoveryCancellationRequested.get()) handle.cancel()
        }

        fun cancelDiscovery() {
            discoveryCancellationRequested.set(true)
            discovery?.cancel()
        }

        fun attachSession(value: PassportChipSession) {
            session = value
            if (cancelled.get()) safelyClose(value)
        }

        fun claimTag(): Boolean = tagClaimed.compareAndSet(false, true)

        fun report(progress: NfcReadProgress) {
            if (!cancelled.get()) {
                runCatching { progressObserver.onProgress(progress) }
            }
        }

        fun cancel() {
            if (cancelled.compareAndSet(false, true)) {
                cancelDiscovery()
                session?.let(::safelyClose)
            }
        }
    }

    private companion object {
        fun safelyClose(session: PassportChipSession) {
            runCatching { session.close() }
        }
    }
}
