package com.ing.offlineidv.mrz.model

import com.ing.offlineidv.core.error.IdvError
import com.ing.offlineidv.core.error.MrzFailure
import com.ing.offlineidv.core.security.Redaction

/** Fatal parse failures that prevent an MRZ document model from being built. */
public enum class MrzParseError(
    public val stableCode: String,
    internal val coreReason: MrzFailure,
) {
    MALFORMED_INPUT("mrz.parse.malformed_input", MrzFailure.MALFORMED),
    UNSUPPORTED_CHARACTER("mrz.parse.unsupported_character", MrzFailure.MALFORMED),
    UNSUPPORTED_FORMAT("mrz.parse.unsupported_format", MrzFailure.UNSUPPORTED_FORMAT),
    INVALID_DATE("mrz.parse.invalid_date", MrzFailure.DATE_INVALID),
    CHECKSUM_FAILURE("mrz.parse.checksum_failure", MrzFailure.CHECKSUM_INVALID),
    ;

    /** Returns the existing SDK error without raw MRZ context. */
    public fun toIdvError(): IdvError = IdvError.Mrz(coreReason)
}

/** Structured parse result that never renders the document or input. */
public sealed interface MrzParseResult<out T : MrzDocument> {
    /** Structurally readable document plus complete validation evidence. */
    public class Parsed<out T : MrzDocument>(
        public val document: T,
        public val validation: MrzValidationResult,
    ) : MrzParseResult<T> {
        override fun toString(): String = "MrzParseResult.Parsed(validation=$validation, document=${Redaction.MARKER})"
    }

    /** Fatal normalization or format rejection with safe issue metadata. */
    public class Rejected(
        public val error: MrzParseError,
        public val validation: MrzValidationResult,
    ) : MrzParseResult<Nothing> {
        override fun toString(): String =
            "MrzParseResult.Rejected(error=${error.stableCode}, validation=$validation, " +
                "document=${Redaction.MARKER})"
    }
}
