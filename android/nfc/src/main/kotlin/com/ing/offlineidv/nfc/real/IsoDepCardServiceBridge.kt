package com.ing.offlineidv.nfc.real

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ResponseAPDU
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Minimal Scuba transport bridge; it retains no APDU transcript and registers no listeners. */
internal class IsoDepCardServiceBridge(
    private val isoDep: IsoDep,
    private val timeoutMillis: Int,
) : CardService() {
    private val closeRequested = AtomicBoolean(false)
    private val failure = AtomicReference<TransportFailure?>(null)

    init {
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
    }

    @Synchronized
    override fun open() {
        if (isOpen()) return
        if (closeRequested.get()) throw CardServiceException(TRANSPORT_UNAVAILABLE)
        try {
            isoDep.timeout = timeoutMillis
            isoDep.connect()
            if (!isoDep.isConnected) {
                failure.compareAndSet(null, TransportFailure.CONNECTION_TIMEOUT)
                throw CardServiceException(TRANSPORT_UNAVAILABLE)
            }
            if (closeRequested.get()) {
                failure.compareAndSet(null, TransportFailure.TAG_LOST)
                isoDep.close()
                throw CardServiceException(TRANSPORT_UNAVAILABLE)
            }
            state = SESSION_STARTED_STATE
        } catch (_: TagLostException) {
            failure.compareAndSet(null, TransportFailure.TAG_LOST)
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        } catch (_: IOException) {
            failure.compareAndSet(null, transportFailureAfterIo())
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        } catch (_: RuntimeException) {
            failure.compareAndSet(null, TransportFailure.TECHNICAL_ERROR)
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        }
    }

    override fun isOpen(): Boolean = !closeRequested.get() && state == SESSION_STARTED_STATE && isoDep.isConnected

    override fun transmit(commandAPDU: CommandAPDU): ResponseAPDU {
        if (!isOpen()) throw CardServiceException(TRANSPORT_UNAVAILABLE)
        val command = commandAPDU.bytes
        return try {
            val maximum = minOf(isoDep.maxTransceiveLength, MAXIMUM_TRANSCEIVE_BYTES)
            if (command.size !in MINIMUM_COMMAND_BYTES..maximum) {
                failure.compareAndSet(null, TransportFailure.LIMIT_EXCEEDED)
                throw CardServiceException(TRANSPORT_COMMAND_REJECTED)
            }
            val response = isoDep.transceive(command)
            try {
                if (response.size !in MINIMUM_RESPONSE_BYTES..maximum) {
                    failure.compareAndSet(null, TransportFailure.LIMIT_EXCEEDED)
                    throw CardServiceException(TRANSPORT_RESPONSE_REJECTED)
                }
                ResponseAPDU(response.copyOf())
            } finally {
                response.fill(0)
            }
        } catch (_: TagLostException) {
            failure.compareAndSet(null, TransportFailure.TAG_LOST)
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        } catch (error: CardServiceException) {
            throw error
        } catch (_: IOException) {
            failure.compareAndSet(null, transportFailureAfterIo())
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        } catch (_: RuntimeException) {
            failure.compareAndSet(null, TransportFailure.TECHNICAL_ERROR)
            throw CardServiceException(TRANSPORT_UNAVAILABLE)
        } finally {
            command.fill(0)
        }
    }

    override fun getATR(): ByteArray = byteArrayOf()

    override fun isConnectionLost(exception: Exception): Boolean =
        exception is TagLostException || failure.get() == TransportFailure.TAG_LOST

    override fun isExtendedAPDULengthSupported(): Boolean = false

    override fun close() {
        if (!closeRequested.compareAndSet(false, true)) return
        failure.compareAndSet(null, TransportFailure.TAG_LOST)
        state = SESSION_STOPPED_STATE
        try {
            isoDep.close()
        } catch (_: IOException) {
            // Closing is best-effort and never changes an already produced finite result.
        } catch (_: RuntimeException) {
            // Platform exceptions never leave the adapter boundary.
        }
    }

    fun consumeFailure(): TransportFailure? = failure.getAndSet(null)

    private fun transportFailureAfterIo(): TransportFailure =
        if (closeRequested.get() || !isoDep.isConnected) {
            TransportFailure.TAG_LOST
        } else {
            TransportFailure.READ_FAILED
        }

    internal enum class TransportFailure {
        CONNECTION_TIMEOUT,
        TAG_LOST,
        READ_FAILED,
        LIMIT_EXCEEDED,
        TECHNICAL_ERROR,
    }

    private companion object {
        const val MINIMUM_COMMAND_BYTES: Int = 4
        const val MINIMUM_RESPONSE_BYTES: Int = 2
        const val MAXIMUM_TRANSCEIVE_BYTES: Int = 65_536
        const val TRANSPORT_UNAVAILABLE: String = "NFC transport unavailable"
        const val TRANSPORT_COMMAND_REJECTED: String = "NFC transport command rejected"
        const val TRANSPORT_RESPONSE_REJECTED: String = "NFC transport response rejected"
    }
}
