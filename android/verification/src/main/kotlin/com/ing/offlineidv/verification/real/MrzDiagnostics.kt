package com.ing.offlineidv.verification.real

/** Coarse stage status that never contains OCR or identity payloads. */
public enum class MrzDiagnosticStatus {
    SUCCESS,
    FAILURE,
    NOT_RUN,
}

/** Safe format classification for OCR-to-MRZ device diagnostics. */
public enum class MrzDiagnosticFormat {
    TD1,
    TD3,
    UNKNOWN,
}

/** Closed, privacy-safe reasons for an unsuccessful OCR-to-MRZ handoff. */
public enum class MrzDiagnosticFailureReason {
    NONE,
    OCR_ARTIFACT_UNAVAILABLE,
    NO_MRZ_CANDIDATE,
    INCORRECT_LINE_COUNT,
    INCORRECT_LINE_LENGTH,
    UNSUPPORTED_CHARACTER,
    UNSUPPORTED_FORMAT,
    MALFORMED_INPUT,
    INVALID_DATE,
    CHECK_DIGIT_MISMATCH,
    INVALID_DOCUMENT_FIELDS,
}

/**
 * Payload-free diagnostic snapshot for one real OCR-to-MRZ handoff.
 *
 * Only counts, lengths, and closed enums are accepted. Raw OCR text, MRZ characters, parsed
 * fields, dates, and document identifiers cannot be represented by this contract.
 */
public class MrzDiagnosticSnapshot(
    public val ocrSuccessful: Boolean,
    public val textBlockCount: Int?,
    public val recognizedLineCount: Int,
    public val candidateLineCount: Int,
    candidateLengths: List<Int>,
    public val format: MrzDiagnosticFormat,
    public val normalization: MrzDiagnosticStatus,
    public val parse: MrzDiagnosticStatus,
    public val validation: MrzDiagnosticStatus,
    public val failureReason: MrzDiagnosticFailureReason,
) {
    public val candidateLengths: List<Int> = candidateLengths.toList()

    init {
        require(textBlockCount == null || textBlockCount >= 0) { "textBlockCount must not be negative" }
        require(recognizedLineCount >= 0) { "recognizedLineCount must not be negative" }
        require(candidateLineCount >= 0) { "candidateLineCount must not be negative" }
        require(candidateLengths.size == candidateLineCount) { "candidate length count must match candidateLineCount" }
        require(candidateLengths.all { it >= 0 }) { "candidate lengths must not be negative" }
    }

    override fun toString(): String =
        "MrzDiagnosticSnapshot(ocrSuccessful=$ocrSuccessful, textBlockCount=$textBlockCount, " +
            "recognizedLineCount=$recognizedLineCount, candidateLineCount=$candidateLineCount, " +
            "candidateLengths=$candidateLengths, format=$format, normalization=$normalization, " +
            "parse=$parse, validation=$validation, failureReason=$failureReason)"
}

/** Optional diagnostic observer. Implementations must never receive or reconstruct OCR content. */
public fun interface MrzDiagnosticSink {
    public fun record(snapshot: MrzDiagnosticSnapshot)

    public companion object {
        /** Default sink used outside explicitly enabled device diagnostics. */
        public val NONE: MrzDiagnosticSink = MrzDiagnosticSink { }
    }
}
