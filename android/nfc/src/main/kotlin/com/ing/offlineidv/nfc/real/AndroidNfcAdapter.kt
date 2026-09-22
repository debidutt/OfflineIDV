package com.ing.offlineidv.nfc.real

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.NfcCapability
import com.ing.offlineidv.nfc.NfcCapabilityDetector
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.NfcTagDiscovery
import com.ing.offlineidv.nfc.NfcTagDiscoveryResult
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PassportChipSession

/** Android capability adapter that distinguishes absent, disabled, and available NFC. */
public class AndroidNfcCapabilityDetector(
    context: Context,
) : NfcCapabilityDetector {
    private val applicationContext: Context = context.applicationContext

    override fun detect(): NfcCapability {
        if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)) {
            return NfcCapability.UNAVAILABLE
        }
        val adapter =
            applicationContext.getSystemService(NfcManager::class.java)?.defaultAdapter
                ?: return NfcCapability.UNAVAILABLE
        return if (adapter.isEnabled) NfcCapability.AVAILABLE else NfcCapability.DISABLED
    }
}

/**
 * Activity-rebindable reader-mode adapter.
 *
 * Android `Tag`, technology lists, and `IsoDep` never leave this platform implementation.
 */
public class AndroidNfcTagDiscovery(
    context: Context,
    private val capabilityDetector: NfcCapabilityDetector = AndroidNfcCapabilityDetector(context),
    private val connectionTimeoutMillis: Int = DEFAULT_CONNECTION_TIMEOUT_MILLIS,
) : NfcTagDiscovery,
    AutoCloseable {
    private val adapter: NfcAdapter? = context.applicationContext.getSystemService(NfcManager::class.java)?.defaultAdapter
    private var activity: Activity? = null
    private var readerModeActivity: Activity? = null
    private var pending: PendingDiscovery? = null
    private var closed: Boolean = false

    init {
        require(connectionTimeoutMillis > 0) { "connectionTimeoutMillis must be positive" }
        JmrtdLoggingContainment.install()
    }

    /** Attaches the currently resumed host; a pending read enables reader mode immediately. */
    public fun attach(activity: Activity) {
        synchronized(this) {
            if (closed || this.activity === activity) return
            this.activity = activity
        }
        refreshReaderMode()
    }

    /** Detaches a paused/destroyed host and disables its reader mode without cancelling the read. */
    public fun detach(activity: Activity) {
        synchronized(this) {
            if (this.activity === activity) this.activity = null
        }
        refreshReaderMode()
    }

    override fun start(callback: (NfcTagDiscoveryResult) -> Unit): CancellableOperation {
        val capability = capabilityDetector.detect()
        if (capability != NfcCapability.AVAILABLE) {
            callback(
                NfcTagDiscoveryResult.Failed(
                    IdvError.Nfc(
                        if (capability == NfcCapability.DISABLED) NfcFailure.DISABLED else NfcFailure.UNAVAILABLE,
                    ),
                ),
            )
            return CancellableOperation.NONE
        }
        val current = PendingDiscovery(callback)
        synchronized(this) {
            if (closed) {
                callback(NfcTagDiscoveryResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                return CancellableOperation.NONE
            }
            pending?.cancelled = true
            pending = current
        }
        refreshReaderMode()
        return CancellableOperation { cancel(current) }
    }

    private fun onTagDiscovered(tag: Tag) {
        val current =
            synchronized(this) {
                val value = pending ?: return
                if (value.cancelled || value.claimed) return
                value.claimed = true
                pending = null
                value
            }
        refreshReaderMode()
        val result =
            if (IsoDep::class.java.name !in tag.techList) {
                NfcTagDiscoveryResult.UnsupportedTag
            } else {
                val isoDep = IsoDep.get(tag)
                if (isoDep == null) {
                    NfcTagDiscoveryResult.IsoDepUnavailable
                } else {
                    NfcTagDiscoveryResult.Connected(
                        AndroidPassportChipSession(isoDep, connectionTimeoutMillis),
                    )
                }
            }
        if (!current.cancelled) {
            current.callback(result)
        } else if (result is NfcTagDiscoveryResult.Connected) {
            result.session.close()
        }
    }

    private fun cancel(target: PendingDiscovery) {
        synchronized(this) {
            target.cancelled = true
            if (pending === target) pending = null
        }
        refreshReaderMode()
    }

    private fun refreshReaderMode() {
        val (disable, enable) =
            synchronized(this) {
                val desired = if (!closed && pending != null) activity else null
                val previous = if (readerModeActivity !== desired) readerModeActivity else null
                if (readerModeActivity !== desired) readerModeActivity = desired
                previous to desired
            }
        disable?.let { host -> runOnHost(host) { adapter?.disableReaderMode(host) } }
        enable?.let { host ->
            runOnHost(host) {
                try {
                    adapter?.enableReaderMode(
                        host,
                        ::onTagDiscovered,
                        NfcAdapter.FLAG_READER_NFC_A or
                            NfcAdapter.FLAG_READER_NFC_B or
                            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        null,
                    )
                } catch (_: RuntimeException) {
                    failPending()
                }
            }
        }
    }

    private fun failPending() {
        val current =
            synchronized(this) {
                pending?.also {
                    it.claimed = true
                    pending = null
                }
            } ?: return
        refreshReaderMode()
        if (!current.cancelled) {
            current.callback(NfcTagDiscoveryResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
        }
    }

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            pending?.cancelled = true
            pending = null
            activity = null
        }
        refreshReaderMode()
    }

    private class PendingDiscovery(
        val callback: (NfcTagDiscoveryResult) -> Unit,
        var claimed: Boolean = false,
        var cancelled: Boolean = false,
    )

    private companion object {
        const val DEFAULT_CONNECTION_TIMEOUT_MILLIS: Int = 10_000

        fun runOnHost(
            activity: Activity,
            block: () -> Unit,
        ) {
            activity.runOnUiThread { block() }
        }
    }
}

/**
 * Owns one `IsoDep` lease and delegates only protected access plus bounded DG1 reading.
 */
internal class AndroidPassportChipSession(
    private val isoDep: IsoDep,
    private val connectionTimeoutMillis: Int,
    private val protocolReader: JmrtdPassportProtocolReader = JmrtdPassportProtocolReader(),
) : PassportChipSession {
    @Volatile private var closed: Boolean = false

    @Volatile private var activeBridge: IsoDepCardServiceBridge? = null

    override fun read(accessKey: PassportAccessKey): NfcReadResult {
        if (closed) return NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST))
        val bridge = IsoDepCardServiceBridge(isoDep, connectionTimeoutMillis)
        activeBridge = bridge
        return try {
            if (closed) {
                bridge.close()
                NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST))
            } else {
                protocolReader.read(bridge, accessKey)
            }
        } finally {
            activeBridge = null
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        activeBridge?.close()
        try {
            isoDep.close()
        } catch (_: Exception) {
            // Platform errors are contained; close remains idempotent.
        }
    }
}
