package com.ing.offlineidv.demo

import com.ing.offlineidv.verification.real.MrzDiagnosticFailureReason
import com.ing.offlineidv.verification.real.MrzDiagnosticFormat
import com.ing.offlineidv.verification.real.MrzDiagnosticSnapshot
import com.ing.offlineidv.verification.real.MrzDiagnosticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class MrzDebugDiagnosticsTest {
    @Test
    public fun `enabled sink logs only stable privacy safe MRZ diagnostics`() {
        val lines = mutableListOf<String>()
        val sink = MrzDebugDiagnosticSink(enabled = true, logLine = lines::add)

        sink.record(snapshot())

        assertEquals(
            listOf(
                "MRZ_DIAG OCR_SUCCESS",
                "MRZ_DIAG TEXT_BLOCK_COUNT=3",
                "MRZ_DIAG LINE_COUNT=5",
                "MRZ_DIAG MRZ_CANDIDATE_LINES=2",
                "MRZ_DIAG CANDIDATE_LENGTHS=[44,44]",
                "MRZ_DIAG FORMAT=TD3",
                "MRZ_DIAG NORMALIZATION=SUCCESS",
                "MRZ_DIAG PARSE=SUCCESS",
                "MRZ_DIAG VALIDATION=FAILURE",
                "MRZ_DIAG FAILURE_REASON=CHECK_DIGIT_MISMATCH",
            ),
            lines,
        )
        val output = lines.joinToString("\n")
        listOf("ERIKSSON", "L898902C3", "740812", "120415").forEach { sensitiveValue ->
            assertFalse(output.contains(sensitiveValue))
        }
    }

    @Test
    public fun `disabled sink emits nothing`() {
        val lines = mutableListOf<String>()

        MrzDebugDiagnosticSink(enabled = false, logLine = lines::add).record(snapshot())

        assertTrue(lines.isEmpty())
    }

    private fun snapshot(): MrzDiagnosticSnapshot =
        MrzDiagnosticSnapshot(
            ocrSuccessful = true,
            textBlockCount = 3,
            recognizedLineCount = 5,
            candidateLineCount = 2,
            candidateLengths = listOf(44, 44),
            format = MrzDiagnosticFormat.TD3,
            normalization = MrzDiagnosticStatus.SUCCESS,
            parse = MrzDiagnosticStatus.SUCCESS,
            validation = MrzDiagnosticStatus.FAILURE,
            failureReason = MrzDiagnosticFailureReason.CHECK_DIGIT_MISMATCH,
        )
}
