package com.ing.offlineidv.demo

import android.util.Log
import com.ing.offlineidv.nfc.NfcDiagnosticEvent
import com.ing.offlineidv.nfc.NfcDiagnosticObservation
import com.ing.offlineidv.nfc.NfcDiagnosticSink

/** Debuggable-build-only Logcat bridge for payload-free NFC diagnostics. */
internal class NfcDebugDiagnosticSink(
    private val enabled: Boolean,
    private val logLine: (String) -> Unit = { message -> Log.d(LOG_TAG, message) },
) : NfcDiagnosticSink {
    override fun record(event: NfcDiagnosticEvent) {
        if (!enabled) return
        logLine(format(event))
    }

    internal companion object {
        private const val LOG_TAG: String = "AtlasNfc"

        fun format(event: NfcDiagnosticEvent): String =
            buildString {
                append("NFC_DIAG STAGE=")
                append(event.stage.name)
                append(" STATUS=")
                append(event.status.name)
                event.failure?.let {
                    append(" FAILURE=")
                    append(it.stableCode)
                }
                if (event.observation != NfcDiagnosticObservation.NONE) {
                    append(" OBSERVATION=")
                    append(event.observation.name)
                }
            }
    }
}
