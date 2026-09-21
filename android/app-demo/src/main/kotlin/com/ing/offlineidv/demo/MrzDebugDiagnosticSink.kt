package com.ing.offlineidv.demo

import android.util.Log
import com.ing.offlineidv.verification.real.MrzDiagnosticSink
import com.ing.offlineidv.verification.real.MrzDiagnosticSnapshot

/** Debuggable-build-only Logcat bridge for payload-free OCR-to-MRZ diagnostics. */
internal class MrzDebugDiagnosticSink(
    private val enabled: Boolean,
    private val logLine: (String) -> Unit = { message -> Log.d(LOG_TAG, message) },
) : MrzDiagnosticSink {
    override fun record(snapshot: MrzDiagnosticSnapshot) {
        if (!enabled) return
        format(snapshot).forEach(logLine)
    }

    internal companion object {
        private const val LOG_TAG: String = "AtlasMrz"

        fun format(snapshot: MrzDiagnosticSnapshot): List<String> =
            listOf(
                if (snapshot.ocrSuccessful) "MRZ_DIAG OCR_SUCCESS" else "MRZ_DIAG OCR_FAILURE",
                "MRZ_DIAG TEXT_BLOCK_COUNT=${snapshot.textBlockCount ?: "UNKNOWN"}",
                "MRZ_DIAG LINE_COUNT=${snapshot.recognizedLineCount}",
                "MRZ_DIAG MRZ_CANDIDATE_LINES=${snapshot.candidateLineCount}",
                "MRZ_DIAG CANDIDATE_LENGTHS=${snapshot.candidateLengths.joinToString(",", "[", "]")}",
                "MRZ_DIAG FORMAT=${snapshot.format.name}",
                "MRZ_DIAG NORMALIZATION=${snapshot.normalization.name}",
                "MRZ_DIAG PARSE=${snapshot.parse.name}",
                "MRZ_DIAG VALIDATION=${snapshot.validation.name}",
                "MRZ_DIAG FAILURE_REASON=${snapshot.failureReason.name}",
            )
    }
}
