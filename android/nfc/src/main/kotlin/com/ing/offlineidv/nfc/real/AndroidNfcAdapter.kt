package com.ing.offlineidv.nfc.real

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ing.offlineidv.core.concurrency.CancellableOperation
import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.NfcFailure
import com.ing.offlineidv.nfc.NfcCapability
import com.ing.offlineidv.nfc.NfcCapabilityDetector
import com.ing.offlineidv.nfc.NfcDiagnosticEvent
import com.ing.offlineidv.nfc.NfcDiagnosticSink
import com.ing.offlineidv.nfc.NfcDiagnosticStage
import com.ing.offlineidv.nfc.NfcDiagnosticStatus
import com.ing.offlineidv.nfc.NfcReadProgressObserver
import com.ing.offlineidv.nfc.NfcReadResult
import com.ing.offlineidv.nfc.NfcTagDiscovery
import com.ing.offlineidv.nfc.NfcTagDiscoveryResult
import com.ing.offlineidv.nfc.PassportAccessKey
import com.ing.offlineidv.nfc.PassportChipSession
import java.util.concurrent.atomic.AtomicBoolean

/** One-shot physical-tag feedback boundary; Demo Mode never constructs this adapter. */
public fun interface NfcTagHapticFeedback {
    public fun perform()

    public companion object {
        public val NONE: NfcTagHapticFeedback = NfcTagHapticFeedback { }
    }
}

/** API-compatible Android vibration adapter that never retains an Activity. */
internal class AndroidNfcTagHapticFeedback(
    context: Context,
) : NfcTagHapticFeedback {
    private val applicationContext: Context = context.applicationContext

    override fun perform() {
        try {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.getSystemService(Vibrator::class.java)
                }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        TAG_DETECTED_VIBRATION_MILLIS,
                        VibrationEffect.DEFAULT_AMPLITUDE,
                    ),
                )
            }
        } catch (_: RuntimeException) {
            // Haptic feedback is best-effort and cannot alter verification behavior.
        }
    }

    private companion object {
        const val TAG_DETECTED_VIBRATION_MILLIS: Long = 45L
    }
}

/** Ensures one physical-tag callback produces at most one haptic pulse. */
internal class NewTagHapticGate {
    private val delivered = AtomicBoolean(false)

    fun perform(feedback: NfcTagHapticFeedback) {
        if (delivered.compareAndSet(false, true)) feedback.perform()
    }
}

/** Closes an active or subsequently attached tag session when its resumed host is lost. */
internal class PhysicalTagSessionLease {
    private var interrupted: Boolean = false
    private var session: PassportChipSession? = null

    @Synchronized
    fun attach(value: PassportChipSession): Boolean {
        if (interrupted) {
            value.close()
            return false
        }
        session = value
        return true
    }

    fun interrupt() {
        val value =
            synchronized(this) {
                if (interrupted) return
                interrupted = true
                session.also { session = null }
            }
        value?.close()
    }
}

/** Keeps the Android RF field alive for the whole active read, including after tag claim. */
internal object NfcReaderModeRetentionPolicy {
    fun shouldEnable(
        closed: Boolean,
        hasActiveRead: Boolean,
        hostAttached: Boolean,
    ): Boolean = !closed && hasActiveRead && hostAttached
}

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
    private val hapticFeedback: NfcTagHapticFeedback = AndroidNfcTagHapticFeedback(context),
    private val diagnosticSink: NfcDiagnosticSink = NfcDiagnosticSink.NONE,
) : NfcTagDiscovery,
    AutoCloseable {
    private val adapter: NfcAdapter? = context.applicationContext.getSystemService(NfcManager::class.java)?.defaultAdapter
    private var activity: Activity? = null
    private var readerModeActivity: Activity? = null
    private var readerModeEnabled: Boolean = false
    private var pending: PendingDiscovery? = null
    private var active: PendingDiscovery? = null
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

    /** Detaches a paused/destroyed host and closes any active physical tag session. */
    public fun detach(activity: Activity) {
        val leaseToInterrupt =
            synchronized(this) {
                if (this.activity !== activity) return@synchronized null
                this.activity = null
                active
                    ?.takeIf(PendingDiscovery::claimed)
                    ?.lease
            }
        leaseToInterrupt?.interrupt()
        refreshReaderMode()
    }

    override fun start(callback: (NfcTagDiscoveryResult) -> Unit): CancellableOperation = start(NfcReadProgressObserver.NONE, callback)

    override fun start(
        progressObserver: NfcReadProgressObserver,
        callback: (NfcTagDiscoveryResult) -> Unit,
    ): CancellableOperation {
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
        val current = PendingDiscovery(callback, progressObserver)
        val replaced =
            synchronized(this) {
                if (closed) {
                    callback(NfcTagDiscoveryResult.Failed(IdvError.Nfc(NfcFailure.TECHNICAL_ERROR)))
                    return CancellableOperation.NONE
                }
                active?.also { it.cancelled = true }
                pending = current
                val previous = active
                active = current
                previous
            }
        replaced?.lease?.interrupt()
        refreshReaderMode()
        return CancellableOperation { cancel(current) }
    }

    private fun onTagDiscovered(tag: Tag) {
        val current =
            synchronized(this) {
                val value = pending ?: return
                if (value.cancelled || value.claimed || activity == null) return
                value.claimed = true
                pending = null
                value
            }
        refreshReaderMode()
        current.hapticGate.perform(hapticFeedback)
        diagnosticSink.recordSafely(
            NfcDiagnosticEvent(NfcDiagnosticStage.TAG_DISCOVERY, NfcDiagnosticStatus.SUCCEEDED),
        )
        val result =
            if (IsoDep::class.java.name !in tag.techList) {
                diagnosticSink.recordSafely(
                    NfcDiagnosticEvent(
                        NfcDiagnosticStage.ISO_DEP,
                        NfcDiagnosticStatus.FAILED,
                        NfcFailure.UNSUPPORTED_TAG,
                    ),
                )
                NfcTagDiscoveryResult.UnsupportedTag
            } else {
                val isoDep = IsoDep.get(tag)
                if (isoDep == null) {
                    diagnosticSink.recordSafely(
                        NfcDiagnosticEvent(
                            NfcDiagnosticStage.ISO_DEP,
                            NfcDiagnosticStatus.FAILED,
                            NfcFailure.ISO_DEP_UNAVAILABLE,
                        ),
                    )
                    NfcTagDiscoveryResult.IsoDepUnavailable
                } else {
                    NfcTagDiscoveryResult.Connected(
                        AndroidPassportChipSession(
                            isoDep = isoDep,
                            connectionTimeoutMillis = connectionTimeoutMillis,
                            diagnosticSink = diagnosticSink,
                        ),
                    )
                }
            }
        val deliver =
            synchronized(this) {
                val attached =
                    if (result is NfcTagDiscoveryResult.Connected) {
                        current.lease.attach(result.session)
                    } else {
                        true
                    }
                active === current && !current.cancelled && attached
            }
        if (deliver) {
            current.callback(result)
        } else if (result is NfcTagDiscoveryResult.Connected) {
            result.session.close()
            if (!current.cancelled) {
                current.callback(
                    NfcTagDiscoveryResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST)),
                )
            }
        }
    }

    private fun cancel(target: PendingDiscovery) {
        synchronized(this) {
            target.cancelled = true
            if (pending === target) pending = null
            if (active === target) active = null
        }
        target.lease.interrupt()
        refreshReaderMode()
    }

    private fun refreshReaderMode() {
        val update =
            synchronized(this) {
                val desired =
                    if (
                        NfcReaderModeRetentionPolicy.shouldEnable(
                            closed = closed,
                            hasActiveRead = active != null,
                            hostAttached = activity != null,
                        )
                    ) {
                        activity
                    } else {
                        null
                    }
                if (readerModeActivity === desired) {
                    ReaderModeUpdate(reportActive = active?.takeIf { desired != null && readerModeEnabled })
                } else {
                    val previous = readerModeActivity
                    readerModeActivity = desired
                    readerModeEnabled = false
                    ReaderModeUpdate(
                        disable = previous,
                        enable = desired,
                    )
                }
            }
        update.disable?.let { host -> runOnHost(host) { runCatching { adapter?.disableReaderMode(host) } } }
        val enable = update.enable
        if (enable == null) {
            update.reportActive?.reportReaderActive()
            return
        }
        runOnHost(enable) {
            diagnosticSink.recordSafely(
                NfcDiagnosticEvent(NfcDiagnosticStage.READER_MODE, NfcDiagnosticStatus.STARTED),
            )
            val currentAdapter = adapter
            if (currentAdapter == null) {
                diagnosticSink.recordSafely(
                    NfcDiagnosticEvent(
                        NfcDiagnosticStage.READER_MODE,
                        NfcDiagnosticStatus.FAILED,
                        NfcFailure.UNAVAILABLE,
                    ),
                )
                failPending(NfcFailure.UNAVAILABLE)
                return@runOnHost
            }
            try {
                currentAdapter.enableReaderMode(
                    enable,
                    ::onTagDiscovered,
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null,
                )
                diagnosticSink.recordSafely(
                    NfcDiagnosticEvent(NfcDiagnosticStage.READER_MODE, NfcDiagnosticStatus.SUCCEEDED),
                )
                val current =
                    synchronized(this) {
                        if (readerModeActivity === enable) {
                            readerModeEnabled = true
                            active
                        } else {
                            null
                        }
                    }
                if (current == null) {
                    runCatching { currentAdapter.disableReaderMode(enable) }
                } else {
                    current.reportReaderActive()
                }
            } catch (_: RuntimeException) {
                synchronized(this) {
                    if (readerModeActivity === enable) readerModeEnabled = false
                }
                diagnosticSink.recordSafely(
                    NfcDiagnosticEvent(
                        NfcDiagnosticStage.READER_MODE,
                        NfcDiagnosticStatus.FAILED,
                        NfcFailure.TECHNICAL_ERROR,
                    ),
                )
                failPending(NfcFailure.TECHNICAL_ERROR)
            }
        }
    }

    private fun failPending(failure: NfcFailure) {
        val current =
            synchronized(this) {
                pending?.also {
                    it.claimed = true
                    pending = null
                }
            } ?: return
        refreshReaderMode()
        if (!current.cancelled) {
            current.callback(NfcTagDiscoveryResult.Failed(IdvError.Nfc(failure)))
        }
    }

    override fun close() {
        val leaseToInterrupt =
            synchronized(this) {
                if (closed) return
                closed = true
                pending?.cancelled = true
                active?.cancelled = true
                val lease = active?.lease
                pending = null
                active = null
                activity = null
                lease
            }
        leaseToInterrupt?.interrupt()
        refreshReaderMode()
    }

    private class PendingDiscovery(
        val callback: (NfcTagDiscoveryResult) -> Unit,
        private val progressObserver: NfcReadProgressObserver,
        var claimed: Boolean = false,
        var cancelled: Boolean = false,
        val lease: PhysicalTagSessionLease = PhysicalTagSessionLease(),
        val hapticGate: NewTagHapticGate = NewTagHapticGate(),
    ) {
        private val readerActiveReported = AtomicBoolean(false)

        fun reportReaderActive() {
            if (!cancelled && readerActiveReported.compareAndSet(false, true)) {
                runCatching { progressObserver.onProgress(com.ing.offlineidv.nfc.NfcReadProgress.READER_ACTIVE) }
            }
        }
    }

    private data class ReaderModeUpdate(
        val disable: Activity? = null,
        val enable: Activity? = null,
        val reportActive: PendingDiscovery? = null,
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
    private val diagnosticSink: NfcDiagnosticSink = NfcDiagnosticSink.NONE,
    private val protocolReader: JmrtdPassportProtocolReader = JmrtdPassportProtocolReader(diagnosticSink = diagnosticSink),
) : PassportChipSession {
    @Volatile private var closed: Boolean = false

    @Volatile private var activeBridge: IsoDepCardServiceBridge? = null

    override fun read(accessKey: PassportAccessKey): NfcReadResult = read(accessKey, NfcReadProgressObserver.NONE)

    override fun read(
        accessKey: PassportAccessKey,
        progressObserver: NfcReadProgressObserver,
    ): NfcReadResult {
        if (closed) return NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST))
        val bridge = IsoDepCardServiceBridge(isoDep, connectionTimeoutMillis)
        activeBridge = bridge
        return try {
            if (closed) {
                bridge.close()
                NfcReadResult.Failed(IdvError.Nfc(NfcFailure.TAG_LOST))
            } else {
                protocolReader.read(bridge, accessKey) {
                    runCatching { progressObserver.onProgress(com.ing.offlineidv.nfc.NfcReadProgress.READING) }
                }
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

private fun NfcDiagnosticSink.recordSafely(event: NfcDiagnosticEvent) {
    runCatching { record(event) }
}
